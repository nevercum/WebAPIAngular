/*
 * Copyright (c) 2017, 2021, Red Hat, Inc. All rights reserved.
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
#include "gc/shared/tlab_globals.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahForwarding.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compressedOops.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/threads.hpp"
#include "utilities/align.hpp"

// Avoid name collision on verify_oop (defined in macroAssembler_arm.hpp)
#ifdef verify_oop
#undef verify_oop
#endif

static bool is_instance_ref_klass(Klass* k) {
  return k->is_instance_klass() && InstanceKlass::cast(k)->reference_type() != REF_NONE;
}

class ShenandoahIgnoreReferenceDiscoverer : public ReferenceDiscoverer {
public:
  virtual bool discover_reference(oop obj, ReferenceType type) {
    return true;
  }
};

class ShenandoahVerifyOopClosure : public BasicOopIterateClosure {
private:
  const char* _phase;
  ShenandoahVerifier::VerifyOptions _options;
  ShenandoahVerifierStack* _stack;
  ShenandoahHeap* _heap;
  MarkBitMap* _map;
  ShenandoahLivenessData* _ld;
  void* _interior_loc;
  oop _loc;

public:
  ShenandoahVerifyOopClosure(ShenandoahVerifierStack* stack, MarkBitMap* map, ShenandoahLivenessData* ld,
                             const char* phase, ShenandoahVerifier::VerifyOptions options) :
    _phase(phase),
    _options(options),
    _stack(stack),
    _heap(ShenandoahHeap::heap()),
    _map(map),
    _ld(ld),
    _interior_loc(nullptr),
    _loc(nullptr) {
    if (options._verify_marked == ShenandoahVerifier::_verify_marked_complete_except_references ||
        options._verify_marked == ShenandoahVerifier::_verify_marked_disable) {
      set_ref_discoverer_internal(new ShenandoahIgnoreReferenceDiscoverer());
    }
  }

private:
  void check(ShenandoahAsserts::SafeLevel level, oop obj, bool test, const char* label) {
    if (!test) {
      ShenandoahAsserts::print_failure(level, obj, _interior_loc, _loc, _phase, label, __FILE__, __LINE__);
    }
  }

  template <class T>
  void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      if (is_instance_ref_klass(obj->klass())) {
        obj = ShenandoahForwarding::get_forwardee(obj);
      }
      // Single threaded verification can use faster non-atomic stack and bitmap
      // methods.
      //
      // For performance reasons, only fully verify non-marked field values.
      // We are here when the host object for *p is already marked.

      if (_map->par_mark(obj)) {
        verify_oop_at(p, obj);
        _stack->push(ShenandoahVerifierTask(obj));
      }
    }
  }

  void verify_oop(oop obj) {
    // Perform consistency checks with gradually decreasing safety level. This guarantees
    // that failure report would not try to touch something that was not yet verified to be
    // safe to process.

    check(ShenandoahAsserts::_safe_unknown, obj, _heap->is_in(obj),
              "oop must be in heap");
    check(ShenandoahAsserts::_safe_unknown, obj, is_object_aligned(obj),
              "oop must be aligned");

    ShenandoahHeapRegion *obj_reg = _heap->heap_region_containing(obj);
    Klass* obj_klass = obj->klass_or_null();

    // Verify that obj is not in dead space:
    {
      // Do this before touching obj->size()
      check(ShenandoahAsserts::_safe_unknown, obj, obj_klass != nullptr,
             "Object klass pointer should not be null");
      check(ShenandoahAsserts::_safe_unknown, obj, Metaspace::contains(obj_klass),
             "Object klass pointer must go to metaspace");

      HeapWord *obj_addr = cast_from_oop<HeapWord*>(obj);
      check(ShenandoahAsserts::_safe_unknown, obj, obj_addr < obj_reg->top(),
             "Object start should be within the region");

      if (!obj_reg->is_humongous()) {
        check(ShenandoahAsserts::_safe_unknown, obj, (obj_addr + obj->size()) <= obj_reg->top(),
               "Object end should be within the region");
      } else {
        size_t humongous_start = obj_reg->index();
        size_t humongous_end = humongous_start + (obj->size() >> ShenandoahHeapRegion::region_size_words_shift());
        for (size_t idx = humongous_start + 1; idx < humongous_end; idx++) {
          check(ShenandoahAsserts::_safe_unknown, obj, _heap->get_region(idx)->is_humongous_continuation(),
                 "Humongous object is in continuation that fits it");
        }
      }

      // ------------ obj is safe at this point --------------

      check(ShenandoahAsserts::_safe_oop, obj, obj_reg->is_active(),
            "Object should be in active region");

      switch (_options._verify_liveness) {
        case ShenandoahVerifier::_verify_liveness_disable:
          // skip
          break;
        case ShenandoahVerifier::_verify_liveness_complete:
          Atomic::add(&_ld[obj_reg->index()], (uint) obj->size()