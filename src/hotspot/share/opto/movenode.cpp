/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "opto/addnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/movenode.hpp"
#include "opto/phaseX.hpp"
#include "opto/subnode.hpp"

//=============================================================================
/*
 The major change is for CMoveP and StrComp.  They have related but slightly
 different problems.  They both take in TWO oops which are both null-checked
 independently before the using Node.  After CCP removes the CastPP's they need
 to pick up the guarding test edge - in this case TWO control edges.  I tried
 various solutions, all have problems:

 (1) Do nothing.  This leads to a bug where we hoist a Load from a CMoveP or a
 StrComp above a guarding null check.  I've seen both cases in normal -Xcomp
 testing.

 (2) Plug the control edge from 1 of the 2 oops in.  Apparent problem here is
 to figure out which test post-dominates.  The real problem is that it doesn't
 matter which one you pick.  After you pick up, the dominating-test elider in
 IGVN can remove the test and allow you to hoist up to the dominating test on
 the chosen oop bypassing the test on the not-chosen oop.  Seen in testing.
 Oops.

 (3) Leave the CastPP's in.  This makes the graph more accurate in some sense;
 we get to keep around the knowledge that an oop is not-null after some test.
 Alas, the CastPP's interfere with GVN (some values are the regular oop, some
 are the CastPP of the oop, all merge at Phi's which cannot collapse, etc).
 This cost us 10% on SpecJVM, even when I removed some of the more trivial
 cases in the optimizer.  Removing more useless Phi's started allowing Loads to
 illegally float above null checks.  I gave up on this approach.

 (4) Add BOTH control edges to both tests.  Alas, too much code knows that
 control edges are in slot-zero ONLY.  Many quick asserts fail; no way to do
 this one.  Note that I really want to allow the CMoveP to float and add both
 control edges to the dependent Load op - meaning I can select early but I
 cannot Load until I pass both tests.

 (5) Do not hoist CMoveP and StrComp.  To this end I added the v-call
 depends_only_on_test().  No obvious performance loss on Spec, but we are
 clearly conservative on CMoveP (also so on StrComp but that's unlikely to
 matter ever).

 */


//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.
// Move constants to the right.
Node *CMoveNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (in(0) != NULL && remove_dead_region(phase, can_reshape)) {
    return this;
  }
  // Don't bother trying to transform a dead node
  if (in(0) != NULL && in(0)->is_top()) {
    return NULL;
  }
  assert(in(Condition) != this &&
         in(IfFalse)   != this &&
         in(IfTrue)    != this, "dead loop in CMoveNode::Ideal");
  if (phase->type(in(Condition)) == Type::TOP ||
      phase->type(in(IfFalse))   == Type::TOP ||
      phase->type(in(IfTrue))    == Type::TOP) {
    return NULL;
  }
  // Canonicalize the node by moving constants to the right input.
  if (in(Condition)->is_Bool() && phase->type(in(IfFalse))->singleton() && !phase->type(in(IfTrue))->singleton()) {
    BoolNode* b = in(Condition)->as_Bool()->negate(phase);
    return make(in(Control), phase->transform(b), in(IfTrue), in(IfFalse), _type);
  }
  return NULL;
}

//------------------------------is_cmove_id------------------------------------
// Helper function to check for CMOVE identity.  Shared with PhiNode::Identity
Node *CMoveNode::is_cmove_id( PhaseTransform *phase, Node *cmp, Node *t, Node *f, BoolNode *b ) {
  // Check for Cmp'ing and CMove'ing same values
  if ((cmp->in(1) == f && cmp->in(2) == t) ||
      // Swapped Cmp is OK
      (cmp->in(2) == f && cmp->in(1) == t)) {
       // Give up this identity check for floating points because it may choose incorrect
       // value around 0.0 and -0.0
       if ( cmp->Opcode()==Op_CmpF || cmp->Opcode()==Op_CmpD )
       return NULL;
       // Check for "(t==f)?t:f;" and replace with "f"
       if( b->_test._test == BoolTest::eq )
       return f;
       // Allow the inverted case as well
       // Check for "(t!=f)?t:f;" and replace with "t"
       if( b->_test._test == BoolTest::ne )
       return t;
     }
  return NULL;
}

//------------------------------Identity---------------------------------------
// Conditional-move is an identity if both inputs are the same, or the test
// true or false.
Node* CMoveNode::Identity(PhaseGVN* phase) {
  // C-moving identical inputs?
  if (in(IfFalse) == in(IfTrue)) {
    return in(IfFalse); // Then it doesn't matter
  }
  if (phase->type(in(Condition)) == TypeInt::ZERO) {
    return in(IfFalse); // Always pick left(false) input
  }
  if (phase->type(in(Condition)) == TypeInt::ONE) {
    return in(IfTrue);  // Always pick right(true) input
  }

  // Check for CMove'ing a constant after comparing against the constant.
  // Happens all the time now, since if we compare equality vs a constant in
  // the parser, we "know" the variable is constant on one path and we force
  // it.  Thus code like "if( x==0 ) {/*EMPTY*/}" ends up inserting a
  // conditional move: "x = (x==0)?0:x;".  Yucko.  This fix is slightly more
  // general in that we don't need constants.
  if( in(Condition)->is_Bool() ) {
    BoolNode *b = in(Condition)->as_Bool();
    Node *cmp = b->in(1);
    if( cmp->is_Cmp() ) {
      Node *id = is_cmove_id( phase, cmp, in(IfTrue), in(IfFalse), b );
      if( id ) return 