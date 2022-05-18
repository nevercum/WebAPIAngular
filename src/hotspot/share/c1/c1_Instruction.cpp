/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "c1/c1_IR.hpp"
#include "c1/c1_Instruction.hpp"
#include "c1/c1_InstructionPrinter.hpp"
#include "c1/c1_ValueStack.hpp"
#include "ci/ciObjArrayKlass.hpp"
#include "ci/ciTypeArrayKlass.hpp"
#include "utilities/bitMap.inline.hpp"


// Implementation of Instruction


int Instruction::dominator_depth() {
  int result = -1;
  if (block()) {
    result = block()->dominator_depth();
  }
  assert(result != -1 || this->as_Local(), "Only locals have dominator depth -1");
  return result;
}

Instruction::Condition Instruction::mirror(Condition cond) {
  switch (cond) {
    case eql: return eql;
    case neq: return neq;
    case lss: return gtr;
    case leq: return geq;
    case gtr: return lss;
    case geq: return leq;
    case aeq: return beq;
    case beq: return aeq;
  }
  ShouldNotReachHere();
  return eql;
}


Instruction::Condition Instruction::negate(Condition cond) {
  switch (cond) {
    case eql: return neq;
    case neq: return eql;
    case lss: return geq;
    case leq: return gtr;
    case gtr: return leq;
    case geq: return lss;
    case aeq: assert(false, "Above equal cannot be negated");
    case beq: assert(false, "Below equal cannot be negated");
  }
  ShouldNotReachHere();
  return eql;
}

void Instruction::update_exception_state(ValueStack* state) {
  if (state != NULL && (state->kind() == ValueStack::EmptyExceptionState || state->kind() == ValueStack::ExceptionState)) {
    assert(state->kind() == ValueStack::EmptyExceptionState || Compilation::current()->env()->should_retain_local_variables(), "unexpected state kind");
    _exception_state = state;
  } else {
    _exception_state = NULL;
  }
}

// Prev without need to have BlockBegin
Instruction* Instruction::prev() {
  Instruction* p = NULL;
  Instruction* q = block();
  while (q != this) {
    assert(q != NULL, "this is not in the block's instruction list");
    p = q; q = q->next();
  }
  return p;
}


void Instruction::state_values_do(ValueVisitor* f) {
  if (state_before() != NULL) {
    state_before()->values_do(f);
  }
  if (exception_state() != NULL){
    exception_state()->values_do(f);
  }
}

ciType* Instruction::exact_type() const {
  ciType* t =  declared_type();
  if (t != NULL && t->is_klass()) {
    return t->as_klass()->exact_klass();
  }
  return NULL;
}


#ifndef PRODUCT
void Instruction::check_state(ValueStack* state) {
  if (state != NULL) {
    state->verify();
  }
}


void Instruction::print() {
  InstructionPrinter ip;
  print(ip);
}


void Instruction::print_line() {
  InstructionPrinter ip;
  ip.print_line(this);
}


void Instruction::print(InstructionPrinter& ip) {
  ip.print_head();
  ip.print_line(this);
  tty->cr();
}
#endif // PRODUCT


// perform constant and interval tests on index value
bool AccessIndexed::compute_needs_range_check() {
  if (length()) {
    Constant* clength = length()->as_Constant();
    Constant* cindex = index()->as_Constant();
    if (clength && cindex) {
      IntConstant* l = clength->type()->as_IntConstant();
      IntConstant* i = cindex->type()->as_IntConstant();
      if (l && i && i->value() < l->value() && i->value() >= 0) {
        return false;
      }
    }
  }

  if (!this->check_flag(NeedsRangeCheckFlag)) {
    return false;
  }

  return true;
}


ciType* Constant::exact_type() const {
  if (type()->is_object() && type()->as_ObjectType()->is_loaded()) {
    return type()->as_ObjectType()->exact_type();
  }
  return NULL;
}

ciType* LoadIndexed::exact_type() const {
  ciType* array_type = array()->exact_type();
  if (array_type != NULL) {
    assert(array_type->is_array_klass(), "what else?");
    ciArrayKlass* ak = (ciArrayKlass*)array_type;

    if (ak->element_type()->is_instance_klass()) {
      ciInstanceKlass* ik = (ciInstanceKlass*)ak->element_type();
      if (ik->is_loaded() && ik->is_final()) {
        return ik;
      }
    }
  }
  return Instruction::exact_type();
}


ciType* LoadIndexed::declared_type() const {
  ciType* array_type = array()->declared_type();
  if (array_type == NULL || !array_type->is_loaded()) {
    return NULL;
  }
  assert(array_type->is_array_klass(), "what else?");
  ciArrayKlass* ak = (ciArrayKlass*)array_type;
  return ak->element_type();
}


ciType* LoadField::declared_type() const {
  return field()->type();
}


ciType* NewTypeArray::exact_type() const {
  return ciTypeArrayKlass::make(elt_type());
}

ciType* NewObjectArray::exact_type() const {
  return ciObjArrayKlass::make(klass());
}

ciType* NewArray::declared_type() const {
  return exact_type();
}

ciType* NewInstance::exact_type() const {
  return klass();
}

ciType* NewInstance::declared_type() const {
  return exact_type();
}

ciType* CheckCast::declared_type() const {
  return klass();
}

// Implementation of ArithmeticOp

bool ArithmeticOp::is_commutative() const {
  switch (op()) {
    case Bytecodes::_iadd: // fall through
    case Bytecodes::_ladd: // fall through
    case Bytecodes::_fadd: // fall through
    case Bytecodes::_dadd: // fall through
    case Bytecodes::_imul: // fall through
    case Bytecodes::_lmul: // fall through
    case Bytecodes::_fmul: // fall through
    case Bytecodes::_dmul: return true;
    default              : return false;
  }
}


bool ArithmeticOp::can_trap() const {
  switch (op()) {
    case Bytecodes::_idiv: // fall through
    case Bytecodes::_ldiv: // fall through
    case Bytecodes::_irem: // fall through
    case Bytecodes::_lrem: return true;
    default              : return false;
  }
}


// Implementation of LogicOp

bool LogicOp::is_commutative() const {
#ifdef ASSERT
  switch (op()) {
    case Bytecodes::_iand: // fall through
    case Bytecodes::_land: // fall through
    case Bytecodes::_ior : // fall through
    case Bytecodes::_lor : // fall through
    case Bytecodes::_ixor: // fall through
    case Bytecodes::_lxor: break;
    default              : ShouldNotReachHere(); break;
  }
#endif
  // all LogicOps are commutative
  return true;
}


// Implementation of IfOp

bool IfOp::is_commutative() const {
  return cond() == eql || cond() == neq;
}


// Implementation of StateSplit

void StateSplit::substitute(BlockList& list, BlockBegin* old_block, BlockBegin* new_block) {
  NOT_PRODUCT(bool assigned = false;)
  for (int i = 0; i < list.length(); i++) {
    BlockBegin** b = list.adr_at(i);
    if (*b == old_block) {
      *b = new_block;
      NOT_PRODUCT(assigned = true;)
    }
  }
  assert(assigned == true, "should have assigned at least once");
}


IRScope* StateSplit::scope() const {
  return _state->scope();
}


void StateSplit::state_values_do(ValueVisitor* f) {
  Instruction::state_values_do(f);
  if (state() != NULL) state()->values_do(f);
}


void BlockBegin::state_values_do(ValueVisitor* f) {
  StateSpl