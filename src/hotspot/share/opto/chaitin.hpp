
/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_CHAITIN_HPP
#define SHARE_OPTO_CHAITIN_HPP

#include "code/vmreg.hpp"
#include "memory/resourceArea.hpp"
#include "opto/connode.hpp"
#include "opto/live.hpp"
#include "opto/machnode.hpp"
#include "opto/matcher.hpp"
#include "opto/phase.hpp"
#include "opto/regalloc.hpp"
#include "opto/regmask.hpp"

class Matcher;
class PhaseCFG;
class PhaseLive;
class PhaseRegAlloc;
class PhaseChaitin;

#define OPTO_DEBUG_SPLIT_FREQ  BLOCK_FREQUENCY(0.001)
#define OPTO_LRG_HIGH_FREQ     BLOCK_FREQUENCY(0.25)

//------------------------------LRG--------------------------------------------
// Live-RanGe structure.
class LRG : public ResourceObj {
  friend class VMStructs;
public:
  static const uint AllStack_size = 0xFFFFF; // This mask size is used to tell that the mask of this LRG supports stack positions
  enum { SPILL_REG=29999 };     // Register number of a spilled LRG

  double _cost;                 // 2 for loads/1 for stores times block freq
  double _area;                 // Sum of all simultaneously live values
  double score() const;         // Compute score from cost and area
  double _maxfreq;              // Maximum frequency of any def or use

  Node *_def;                   // Check for multi-def live ranges
#ifndef PRODUCT
  GrowableArray<Node*>* _defs;
#endif

  uint _risk_bias;              // Index of LRG which we want to avoid color
  uint _copy_bias;              // Index of LRG which we want to share color

  uint _next;                   // Index of next LRG in linked list
  uint _prev;                   // Index of prev LRG in linked list
private:
  uint _reg;                    // Chosen register; undefined if mask is plural
public:
  // Return chosen register for this LRG.  Error if the LRG is not bound to
  // a single register.
  OptoReg::Name reg() const { return OptoReg::Name(_reg); }
  void set_reg( OptoReg::Name r ) { _reg = r; }

private:
  uint _eff_degree;             // Effective degree: Sum of neighbors _num_regs
public:
  int degree() const { assert( _degree_valid , "" ); return _eff_degree; }
  // Degree starts not valid and any change to the IFG neighbor
  // set makes it not valid.
  void set_degree( uint degree ) {
    _eff_degree = degree;
    debug_only(_degree_valid = 1;)
    assert(!_mask.is_AllStack() || (_mask.is_AllStack() && lo_degree()), "_eff_degree can't be bigger than AllStack_size - _num_regs if the mask supports stack registers");
  }
  // Made a change that hammered degree
  void invalid_degree() { debug_only(_degree_valid=0;) }
  // Incrementally modify degree.  If it was correct, it should remain correct
  void inc_degree( uint mod ) {
    _eff_degree += mod;
    assert(!_mask.is_AllStack() || (_mask.is_AllStack() && lo_degree()), "_eff_degree can't be bigger than AllStack_size - _num_regs if the mask supports stack registers");
  }
  // Compute the degree between 2 live ranges
  int compute_degree( LRG &l ) const;
  bool mask_is_nonempty_and_up() const {
    return mask().is_UP() && mask_size();
  }
  bool is_float_or_vector() const {
    return _is_float || _is_vector;
  }

private:
  RegMask _mask;                // Allowed registers for this LRG
  uint _mask_size;              // cache of _mask.Size();
public:
  int compute_mask_size() const { return _mask.is_AllStack() ? AllStack_size : _mask.Size(); }
  void set_mask_size( int size ) {
    assert((size == (int)AllStack_size) || (size == (int)_mask.Size()), "");
    _mask_size = size;
#ifdef ASSERT
    _msize_valid=1;
    if (_is_vector) {
      assert(!_fat_proj, "sanity");
      if (!(_is_scalable && OptoReg::is_stack(_reg))) {
        assert(_mask.is_aligned_sets(_num_regs), "mask is not aligned, adjacent sets");
      }
    } else if (_num_regs == 2 && !_fat_proj) {
      assert(_mask.is_aligned_pairs(), "mask is not aligned, adjacent pairs");
    }
#endif
  }
  void compute_set_mask_size() { set_mask_size(compute_mask_size()); }
  int mask_size() const { assert( _msize_valid, "mask size not valid" );
                          return _mask_size; }
  // Get the last mask size computed, even if it does not match the
  // count of bits in the current mask.
  int get_invalid_mask_size() const { return _mask_size; }
  const RegMask &mask() const { return _mask; }
  void set_mask( const RegMask &rm ) { _mask = rm; debug_only(_msize_valid=0;)}
  void AND( const RegMask &rm ) { _mask.AND(rm); debug_only(_msize_valid=0;)}
  void SUBTRACT( const RegMask &rm ) { _mask.SUBTRACT(rm); debug_only(_msize_valid=0;)}
  void Clear()   { _mask.Clear()  ; debug_only(_msize_valid=1); _mask_size = 0; }
  void Set_All() { _mask.Set_All(); debug_only(_msize_valid=1); _mask_size = RegMask::CHUNK_SIZE; }

  void Insert( OptoReg::Name reg ) { _mask.Insert(reg);  debug_only(_msize_valid=0;) }
  void Remove( OptoReg::Name reg ) { _mask.Remove(reg);  debug_only(_msize_valid=0;) }
  void clear_to_sets()  { _mask.clear_to_sets(_num_regs); debug_only(_msize_valid=0;) }

private:
  // Number of registers this live range uses when it colors
  uint16_t _num_regs;           // 2 for Longs and Doubles, 1 for all else
                                // except _num_regs is kill count for fat_proj

  // For scalable register, num_regs may not be the actual physical register size.
  // We need to get the actual physical length of scalable register when scalable
  // register is spilled. The size of one slot is 32-bit.
  uint _scalable_reg_slots;     // Actual scalable register length of slots.
                                // Meaningful only when _is_scalable is true.
public:
  int num_regs() const { return _num_regs; }
  void set_num_regs( int reg ) { assert( _num_regs == reg || !_num_regs, "" ); _num_regs = reg; }

  uint scalable_reg_slots() { return _scalable_reg_slots; }
  void set_scalable_reg_slots(uint slots) {
    assert(_is_scalable, "scalable register");
    assert(slots > 0, "slots of scalable register is not valid");
    _scalable_reg_slots = slots;
  }

  bool is_scalable() {
#ifdef ASSERT
    if (_is_scalable) {
      assert(_is_vector && (_num_regs == RegMask::SlotsPerVecA) ||
             _is_predicate && (_num_regs == RegMask::SlotsPerRegVectMask), "unexpected scalable reg");
    }
#endif
    return Matcher::implements_scalable_vector && _is_scalable;
  }

private:
  // Number of physical registers this live range uses when it colors
  // Architecture and register-set dependent
  uint16_t _reg_pressure;
public:
  void set_reg_pressure(int i)  { _reg_pressure = i; }
  int      reg_pressure() const { return _reg_pressure; }

  // How much 'wiggle room' does this live range have?
  // How many color choices can it make (scaled by _num_regs)?
  int degrees_of_freedom() const { return mask_size() - _num_regs; }
  // Bound LRGs have ZERO degrees of freedom.  We also count
  // must_spill as bound.
  bool is_bound  () const { return _is_bound; }
  // Negative degrees-of-freedom; even with no neighbors this
  // live range must spill.
  bool not_free() const { return degrees_of_freedom() <  0; }
  // Is this live range of "low-degree"?  Trivially colorable?
  bool lo_degree () const { return degree() <= degrees_of_freedom(); }
  // Is this live range just barely "low-degree"?  Trivially colorable?
  bool just_lo_degree () const { return degree() == degrees_of_freedom(); }

  uint   _is_oop:1,             // Live-range holds an oop
         _is_float:1,           // True if in float registers
         _is_vector:1,          // True if in vector registers
         _is_predicate:1,       // True if in mask/predicate registers
         _is_scalable:1,        // True if register size is scalable
                                //      e.g. Arm SVE vector/predicate registers.
         _was_spilled1:1,       // True if prior spilling on def
         _was_spilled2:1,       // True if twice prior spilling on def
         _is_bound:1,           // live range starts life with no
                                // degrees of freedom.
         _direct_conflict:1,    // True if def and use registers in conflict
         _must_spill:1,         // live range has lost all degrees of freedom
    // If _fat_proj is set, live range does NOT require aligned, adjacent
    // registers and has NO interferences.
    // If _fat_proj is clear, live range requires num_regs() to be a power of
    // 2, and it requires registers to form an aligned, adjacent set.
         _fat_proj:1,           //
         _was_lo:1,             // Was lo-degree prior to coalesce
         _msize_valid:1,        // _mask_size cache valid
         _degree_valid:1,       // _degree cache valid
         _has_copy:1,           // Adjacent to some copy instruction
         _at_risk:1;            // Simplify says this guy is at risk to spill


  // Alive if non-zero, dead if zero
  bool alive() const { return _def != NULL; }
  bool is_multidef() const { return _def == NodeSentinel; }
  bool is_singledef() const { return _def != NodeSentinel; }

#ifndef PRODUCT
  void dump( ) const;
#endif
};

//------------------------------IFG--------------------------------------------
//                         InterFerence Graph
// An undirected graph implementation.  Created with a fixed number of
// vertices.  Edges can be added & tested.  Vertices can be removed, then
// added back later with all edges intact.  Can add edges between one vertex
// and a list of other vertices.  Can union vertices (and their edges)
// together.  The IFG needs to be really really fast, and also fairly
// abstract!  It needs abstraction so I can fiddle with the implementation to
// get even more speed.
class PhaseIFG : public Phase {
  friend class VMStructs;
  // Current implementation: a triangular adjacency list.

  // Array of adjacency-lists, indexed by live-range number
  IndexSet *_adjs;

  // Assertion bit for proper use of Squaring
  bool _is_square;

  // Live range structure goes here
  LRG *_lrgs;                   // Array of LRG structures

public:
  // Largest live-range number
  uint _maxlrg;

  Arena *_arena;

  // Keep track of inserted and deleted Nodes
  VectorSet *_yanked;

  PhaseIFG( Arena *arena );
  void init( uint maxlrg );

  // Add edge between a and b.  Returns true if actually added.
  int add_edge( uint a, uint b );

  // Test for edge existence
  int test_edge( uint a, uint b ) const;

  // Square-up matrix for faster Union
  void SquareUp();

  // Return number of LRG neighbors
  uint neighbor_cnt( uint a ) const { return _adjs[a].count(); }
  // Union edges of b into a on Squared-up matrix
  void Union( uint a, uint b );
  // Test for edge in Squared-up matrix
  int test_edge_sq( uint a, uint b ) const;
  // Yank a Node and all connected edges from the IFG.  Be prepared to
  // re-insert the yanked Node in reverse order of yanking.  Return a
  // list of neighbors (edges) yanked.
  IndexSet *remove_node( uint a );
  // Reinsert a yanked Node
  void re_insert( uint a );
  // Return set of neighbors
  IndexSet *neighbors( uint a ) const { return &_adjs[a]; }
