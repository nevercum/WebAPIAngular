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
  StateSplit::state_values_do(f);

  if (is_set(BlockBegin::exception_entry_flag)) {
    for (int i = 0; i < number_of_exception_states(); i++) {
      exception_state_at(i)->values_do(f);
    }
  }
}


// Implementation of Invoke


Invoke::Invoke(Bytecodes::Code code, ValueType* result_type, Value recv, Values* args,
               ciMethod* target, ValueStack* state_before)
  : StateSplit(result_type, state_before)
  , _code(code)
  , _recv(recv)
  , _args(args)
  , _target(target)
{
  set_flag(TargetIsLoadedFlag,   target->is_loaded());
  set_flag(TargetIsFinalFlag,    target_is_loaded() && target->is_final_method());

  assert(args != NULL, "args must exist");
#ifdef ASSERT
  AssertValues assert_value;
  values_do(&assert_value);
#endif

  // provide an initial guess of signature size.
  _signature = new BasicTypeList(number_of_arguments() + (has_receiver() ? 1 : 0));
  if (has_receiver()) {
    _signature->append(as_BasicType(receiver()->type()));
  }
  for (int i = 0; i < number_of_arguments(); i++) {
    ValueType* t = argument_at(i)->type();
    BasicType bt = as_BasicType(t);
    _signature->append(bt);
  }
}


void Invoke::state_values_do(ValueVisitor* f) {
  StateSplit::state_values_do(f);
  if (state_before() != NULL) state_before()->values_do(f);
  if (state()        != NULL) state()->values_do(f);
}

ciType* Invoke::declared_type() const {
  ciSignature* declared_signature = state()->scope()->method()->get_declared_signature_at_bci(state()->bci());
  ciType *t = declared_signature->return_type();
  assert(t->basic_type() != T_VOID, "need return value of void method?");
  return t;
}

// Implementation of Constant
intx Constant::hash() const {
  if (state_before() == NULL) {
    switch (type()->tag()) {
    case intTag:
      return HASH2(name(), type()->as_IntConstant()->value());
    case addressTag:
      return HASH2(name(), type()->as_AddressConstant()->value());
    case longTag:
      {
        jlong temp = type()->as_LongConstant()->value();
        return HASH3(name(), high(temp), low(temp));
      }
    case floatTag:
      return HASH2(name(), jint_cast(type()->as_FloatConstant()->value()));
    case doubleTag:
      {
        jlong temp = jlong_cast(type()->as_DoubleConstant()->value());
        return HASH3(name(), high(temp), low(temp));
      }
    case objectTag:
      assert(type()->as_ObjectType()->is_loaded(), "can't handle unloaded values");
      return HASH2(name(), type()->as_ObjectType()->constant_value());
    case metaDataTag:
      assert(type()->as_MetadataType()->is_loaded(), "can't handle unloaded values");
      return HASH2(name(), type()->as_MetadataType()->constant_value());
    default:
      ShouldNotReachHere();
    }
  }
  return 0;
}

bool Constant::is_equal(Value v) const {
  if (v->as_Constant() == NULL) return false;

  switch (type()->tag()) {
    case intTag:
      {
        IntConstant* t1 =    type()->as_IntConstant();
        IntConstant* t2 = v->type()->as_IntConstant();
        return (t1 != NULL && t2 != NULL &&
                t1->value() == t2->value());
      }
    case longTag:
      {
        LongConstant* t1 =    type()->as_LongConstant();
        LongConstant* t2 = v->type()->as_LongConstant();
        return (t1 != NULL && t2 != NULL &&
                t1->value() == t2->value());
      }
    case floatTag:
      {
        FloatConstant* t1 =    type()->as_FloatConstant();
        FloatConstant* t2 = v->type()->as_FloatConstant();
        return (t1 != NULL && t2 != NULL &&
                jint_cast(t1->value()) == jint_cast(t2->value()));
      }
    case doubleTag:
      {
        DoubleConstant* t1 =    type()->as_DoubleConstant();
        DoubleConstant* t2 = v->type()->as_DoubleConstant();
        return (t1 != NULL && t2 != NULL &&
                jlong_cast(t1->value()) == jlong_cast(t2->value()));
      }
    case objectTag:
      {
        ObjectType* t1 =    type()->as_ObjectType();
        ObjectType* t2 = v->type()->as_ObjectType();
        return (t1 != NULL && t2 != NULL &&
                t1->is_loaded() && t2->is_loaded() &&
                t1->constant_value() == t2->constant_value());
      }
    case metaDataTag:
      {
        MetadataType* t1 =    type()->as_MetadataType();
        MetadataType* t2 = v->type()->as_MetadataType();
        return (t1 != NULL && t2 != NULL &&
                t1->is_loaded() && t2->is_loaded() &&
                t1->constant_value() == t2->constant_value());
      }
    default:
      return false;
  }
}

Constant::CompareResult Constant::compare(Instruction::Condition cond, Value right) const {
  Constant* rc = right->as_Constant();
  // other is not a constant
  if (rc == NULL) return not_comparable;

  ValueType* lt = type();
  ValueType* rt = rc->type();
  // different types
  if (lt->base() != rt->base()) return not_comparable;
  switch (lt->tag()) {
  case intTag: {
    int x = lt->as_IntConstant()->value();
    int y = rt->as_IntConstant()->value();
    switch (cond) {
    case If::eql: return x == y ? cond_true : cond_false;
    case If::neq: return x != y ? cond_true : cond_false;
    case If::lss: return x <  y ? cond_true : cond_false;
    case If::leq: return x <= y ? cond_true : cond_false;
    case If::gtr: return x >  y ? cond_true : cond_false;
    case If::geq: return x >= y ? cond_true : cond_false;
    default     : break;
    }
    break;
  }
  case longTag: {
    jlong x = lt->as_LongConstant()->value();
    jlong y = rt->as_LongConstant()->value();
    switch (cond) {
    case If::eql: return x == y ? cond_true : cond_false;
    case If::neq: return x != y ? cond_true : cond_false;
    case If::lss: return x <  y ? cond_true : cond_false;
    case If::leq: return x <= y ? cond_true : cond_false;
    case If::gtr: return x >  y ? cond_true : cond_false;
    case If::geq: return x >= y ? cond_true : cond_false;
    default     : break;
    }
    break;
  }
  case objectTag: {
    ciObject* xvalue = lt->as_ObjectType()->constant_value();
    ciObject* yvalue = rt->as_ObjectType()->constant_value();
    assert(xvalue != NULL && yvalue != NULL, "not constants");
    if (xvalue->is_loaded() && yvalue->is_loaded()) {
      switch (cond) {
      case If::eql: return xvalue == yvalue ? cond_true : cond_false;
      case If::neq: return xvalue != yvalue ? cond_true : cond_false;
      default     : break;
      }
    }
    break;
  }
  case metaDataTag: {
    ciMetadata* xvalue = lt->as_MetadataType()->constant_value();
    ciMetadata* yvalue = rt->as_MetadataType()->constant_value();
    assert(xvalue != NULL && yvalue != NULL, "not constants");
    if (xvalue->is_loaded() && yvalue->is_loaded()) {
      switch (cond) {
      case If::eql: return xvalue == yvalue ? cond_true : cond_false;
      case If::neq: return xvalue != yvalue ? cond_true : cond_false;
      default     : break;
      }
    }
    break;
  }
  default:
    break;
  }
  return not_comparable;
}


// Implementation of BlockBegin

void BlockBegin::set_end(BlockEnd* new_end) { // Assumes that no predecessor of new_end still has it as its successor
  assert(new_end != NULL, "Should not reset block new_end to NULL");
  if (new_end == _end) return;

  // Remove this block as predecessor of its current successors
  if (_end != NULL) {
    for (int i = 0; i < number_of_sux(); i++) {
      sux_at(i)->remove_predecessor(this);
    }
  }

  _end = new_end;

  // Add this block as predecessor of its new successors
  for (int i = 0; i < number_of_sux(); i++) {
    sux_at(i)->add_predecessor(this);
  }
}


void BlockBegin::disconnect_edge(BlockBegin* from, BlockBegin* to) {
  // disconnect any edges 