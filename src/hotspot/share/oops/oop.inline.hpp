/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_OOP_INLINE_HPP
#define SHARE_OOPS_OOP_INLINE_HPP

#include "oops/oop.hpp"

#include "memory/universe.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/arrayOop.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/markWord.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"
#include "utilities/globalDefinitions.hpp"

// Implementation of all inlined member functions defined in oop.hpp
// We need a separate file to avoid circular references

markWord oopDesc::mark() const {
  return Atomic::load(&_mark);
}

markWord oopDesc::mark_acquire() const {
  return Atomic::load_acquire(&_mark);
}

markWord* oopDesc::mark_addr() const {
  return (markWord*) &_mark;
}

void oopDesc::set_mark(markWord m) {
  Atomic::store(&_mark, m);
}

void oopDesc::set_mark(HeapWord* mem, markWord m) {
  *(markWord*)(((char*)mem) + mark_offset_in_bytes()) = m;
}

void oopDesc::release_set_mark(markWord m) {
  Atomic::release_store(&_mark, m);
}

markWord oopDesc::cas_set_mark(markWord new_mark, markWord old_mark) {
  return Atomic::cmpxchg(&_mark, old_mark, new_mark);
}

markWord oopDesc::cas_set_mark(markWord new_mark, markWord old_mark, atomic_memory_order order) {
  return Atomic::cmpxchg(&_mark, old_mark, new_mark, order);
}

void oopDesc::init_mark() {
  set_mark(markWord::prototype());
}

Klass* oopDesc::klass() const {
  if (UseCompressedClassPointers) {
    return CompressedKlassPointers::decode_not_null(_metadata._compressed_klass);
  } else {
    return _metadata._klass;
  }
}

Klass* oopDesc::klass_or_null() const {
  if (UseCompressedClassPointers) {
    return CompressedKlassPointers::decode(_metadata._compressed_klass);
  } else {
    return _metadata._klass;
  }
}

Klass* oopDesc::klass_or_null_acquire() const {
  if (UseCompressedClassPointers) {
    narrowKlass nklass = Atomic::load_acquire(&_metadata._compressed_klass);
    return Com