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
          Atomic::add(&_ld[obj_reg->index()], (uint) obj->size(), memory_order_relaxed);
          // fallthrough for fast failure for un-live regions:
        case ShenandoahVerifier::_verify_liveness_conservative:
          check(ShenandoahAsserts::_safe_oop, obj, obj_reg->has_live(),
                   "Object must belong to region with live data");
          break;
        default:
          assert(false, "Unhandled liveness verification");
      }
    }

    oop fwd = ShenandoahForwarding::get_forwardee_raw_unchecked(obj);

    ShenandoahHeapRegion* fwd_reg = nullptr;

    if (obj != fwd) {
      check(ShenandoahAsserts::_safe_oop, obj, _heap->is_in(fwd),
             "Forwardee must be in heap");
      check(ShenandoahAsserts::_safe_oop, obj, !CompressedOops::is_null(fwd),
             "Forwardee is set");
      check(ShenandoahAsserts::_safe_oop, obj, is_object_aligned(fwd),
             "Forwardee must be aligned");

      // Do this before touching fwd->size()
      Klass* fwd_klass = fwd->klass_or_null();
      check(ShenandoahAsserts::_safe_oop, obj, fwd_klass != nullptr,
             "Forwardee klass pointer should not be null");
      check(ShenandoahAsserts::_safe_oop, obj, Metaspace::contains(fwd_klass),
             "Forwardee klass pointer must go to metaspace");
      check(ShenandoahAsserts::_safe_oop, obj, obj_klass == fwd_klass,
             "Forwardee klass pointer must go to metaspace");

      fwd_reg = _heap->heap_region_containing(fwd);

      // Verify that forwardee is not in the dead space:
      check(ShenandoahAsserts::_safe_oop, obj, !fwd_reg->is_humongous(),
             "Should have no humongous forwardees");

      HeapWord *fwd_addr = cast_from_oop<HeapWord *>(fwd);
      check(ShenandoahAsserts::_safe_oop, obj, fwd_addr < fwd_reg->top(),
             "Forwardee start should be within the region");
      check(ShenandoahAsserts::_safe_oop, obj, (fwd_addr + fwd->size()) <= fwd_reg->top(),
             "Forwardee end should be within the region");

      oop fwd2 = ShenandoahForwarding::get_forwardee_raw_unchecked(fwd);
      check(ShenandoahAsserts::_safe_oop, obj, (fwd == fwd2),
             "Double forwarding");
    } else {
      fwd_reg = obj_reg;
    }

    // ------------ obj and fwd are safe at this point --------------

    switch (_options._verify_marked) {
      case ShenandoahVerifier::_verify_marked_disable:
        // skip
        break;
      case ShenandoahVerifier::_verify_marked_incomplete:
        check(ShenandoahAsserts::_safe_all, obj, _heap->marking_context()->is_marked(obj),
               "Must be marked in incomplete bitmap");
        break;
      case ShenandoahVerifier::_verify_marked_complete:
        check(ShenandoahAsserts::_safe_all, obj, _heap->complete_marking_context()->is_marked(obj),
               "Must be marked in complete bitmap");
        break;
      case ShenandoahVerifier::_verify_marked_complete_except_references:
        check(ShenandoahAsserts::_safe_all, obj, _heap->complete_marking_context()->is_marked(obj),
              "Must be marked in complete bitmap, except j.l.r.Reference referents");
        break;
      default:
        assert(false, "Unhandled mark verification");
    }

    switch (_options._verify_forwarded) {
      case ShenandoahVerifier::_verify_forwarded_disable:
        // skip
        break;
      case ShenandoahVerifier::_verify_forwarded_none: {
        check(ShenandoahAsserts::_safe_all, obj, (obj == fwd),
               "Should not be forwarded");
        break;
      }
      case ShenandoahVerifier::_verify_forwarded_allow: {
        if (obj != fwd) {
          check(ShenandoahAsserts::_safe_all, obj, obj_reg != fwd_reg,
                 "Forwardee should be in another region");
        }
        break;
      }
      default:
        assert(false, "Unhandled forwarding verification");
    }

    switch (_options._verify_cset) {
      case ShenandoahVerifier::_verify_cset_disable:
        // skip
        break;
      case ShenandoahVerifier::_verify_cset_none:
        check(ShenandoahAsserts::_safe_all, obj, !_heap->in_collection_set(obj),
               "Should not have references to collection set");
        break;
      case ShenandoahVerifier::_verify_cset_forwarded:
        if (_heap->in_collection_set(obj)) {
          check(ShenandoahAsserts::_safe_all, obj, (obj != fwd),
                 "Object in collection set, should have forwardee");
        }
        break;
      default:
        assert(false, "Unhandled cset verification");
    }

  }

public:
  /**
   * Verify object with known interior reference.
   * @param p interior reference where the object is referenced from; can be off-heap
   * @param obj verified object
   */
  template <class T>
  void verify_oop_at(T* p, oop obj) {
    _interior_loc = p;
    verify_oop(obj);
    _interior_loc = nullptr;
  }

  /**
   * Verify object without known interior reference.
   * Useful when picking up the object at known offset in heap,
   * but without knowing what objects reference it.
   * @param obj verified object
   */
  void verify_oop_standalone(oop obj) {
    _interior_loc = nullptr;
    verify_oop(obj);
    _interior_loc = nullptr;
  }

  /**
   * Verify oop fields from this object.
   * @param obj host object for verified fields
   */
  void verify_oops_from(oop obj) {
    _loc = obj;
    obj->oop_iterate(this);
    _loc = nullptr;
  }

  virtual void do_oop(oop* p) { do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
};

class ShenandoahCalculateRegionStatsClosure : public ShenandoahHeapRegionClosure {
private:
  size_t _used, _committed, _garbage;
public:
  ShenandoahCalculateRegionStatsClosure() : _used(0), _committed(0), _garbage(0) {};

  void heap_region_do(ShenandoahHeapRegion* r) {
    _used += r->used();
    _garbage += r->garbage();
    _committed += r->is_committed() ? ShenandoahHeapRegion::region_size_bytes() : 0;
  }

  size_t used() { return _used; }
  size_t committed() { return _committed; }
  size_t garbage() { return _garbage; }
};

class ShenandoahVerifyHeapRegionClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahHeap* _heap;
  const char* _phase;
  ShenandoahVerifier::VerifyRegions _regions;
public:
  ShenandoahVerifyHeapRegionClosure(const char* phase, ShenandoahVerifier::VerifyRegions regions) :
    _heap(ShenandoahHeap::heap()),
    _phase(phase),
    _regions(regions) {};

  void print_failure(ShenandoahHeapRegion* r, const char* label) {
    ResourceMark rm;

    ShenandoahMessageBuffer msg("Shenandoah verification failed; %s: %s\n\n", _phase, label);

    stringStream ss;
    r->print_on(&ss);
    msg.append("%s", ss.as_string());

    report_vm_error(__FILE__, __LINE__, msg.buffer());
  }

  void verify(ShenandoahHeapRegion* r, bool test, const char* msg) {
    if (!test) {
      print_failure(r, msg);
    }
  }

  void heap_region_do(ShenandoahHeapRegion* r) {
    switch (_regions) {
      case ShenandoahVerifier::_verify_regions_disable:
        break;
      case ShenandoahVerifier::_verify_regions_notrash:
        verify(r, !r->is_trash(),
               "Should not have trash regions");
        break;
      case ShenandoahVerifier::_verify_regions_nocset:
        verify(r, !r->is_cset(),
               "Should not have cset regions");
        break;
      case ShenandoahVerifier::_verify_regions_notrash_nocset:
        verify(r, !r->is_trash(),
               "Should not have trash regions");
        verify(r, !r->is_cset(),
               "Should not have cset regions");
        break;
      default:
        ShouldNotReachHere();
    }

    verify(r, r->capacity() == ShenandoahHeapRegion::region_size_bytes(),
           "Capacity should match region size");

    verify(r, r->bottom() <= r->top(),
           "Region top should not be less than bottom");

    verify(r, r->bottom() <= _heap->marking_context()->top_at_mark_start(r),
           "Region TAMS should not be less than bottom");

    verify(r, _heap->marking_context()->top_at_mark_start(r) <= r->top(),
           "Complete TAMS should not be larger than top");

    verify(r, r->get_live_data_bytes() <= r->capacity(),
           "Live data cannot be larger than capacity");

    verify(r, r->garbage() <= r->capacity(),
           "Garbage cannot be larger than capacity");

    verify(r, r->used() <= r->capacity(),
           "Used cannot be larger than capacity");

    verify(r, r->get_shared_allocs() <= r->capacity(),
           "Shared alloc count should not be larger than capacity");

    verify(r, r->get_tlab_allocs() <= r->capacity(),
           "TLAB alloc count should not be larger than capacity");

    verify(r, r->get_gclab_allocs() <= r->capacity(),
           "GCLAB alloc count should not be larger than capacity");

    verify(r, r->get_shared_allocs() + r->get_tlab_allocs() + r->get_gclab_allocs() == r->used(),
           "Accurate accounting: shared + TLAB + GCLAB = used");

    verify(r, !r->is_empty() || !r->has_live(),
           "Empty regions should not have live data");

    verify(r, r->is_cset() == _heap->collection_set()->is_in(r),
           "Transitional: region flags and collection set agree");
  }
};

class ShenandoahVerifierReachableTask : public WorkerTask {
private:
  const char* _label;
  ShenandoahVerifier::VerifyOptions _options;
  ShenandoahHeap* _heap;
  ShenandoahLivenessData* _ld;
  MarkBitMap* _bitmap;
  volatile size_t _processed;

public:
  ShenandoahVerifierReachableTask(MarkBitMap* bitmap,
                                  ShenandoahLivenessData* ld,
                                  const char* label,
                                  ShenandoahVerifier::VerifyOptions options) :
    WorkerTask("Shenandoah Verifier Reachable Objects"),
    _label(label),
    _options(options),
    _heap(ShenandoahHeap::heap()),
    _ld(ld),
    _bitmap(bitmap),
    _processed(0) {};

  size_t processed() {
    return _processed;
  }

  virtual void work(uint worker_id) {
    ResourceMark rm;
    ShenandoahVerifierStack stack;

    // On level 2, we need to only check the roots once.
    // On level 3, we want to check the roots, and seed the local stack.
    // It is a lesser evil to accept multiple root scans at level 3, because
    // extended parallelism would buy us out.
    if (((ShenandoahVerifyLevel == 2) && (worker_id == 0))
        || (ShenandoahVerifyLevel >= 3)) {
        ShenandoahVerifyOopClosure cl(&stack, _bitmap, _ld,
                                      ShenandoahMessageBuffer("%s, Roots", _label),
                                      _options);
        if (_heap->unload_classes()) {
          ShenandoahRootVerifier::strong_roots_do(&cl);
        } else {
          ShenandoahRootVerifier::roots_do(&cl);
        }
    }

    size_t processed = 0;

    if (ShenandoahVerifyLevel >= 3) {
      ShenandoahVerifyOopClosure cl(&stack, _bitmap, _ld,
                                    ShenandoahMessageBuffer("%s, Reachable", _label),
                                    _options);
      while (!stack.is_empty()) {
        processed++;
        ShenandoahVerifierTask task = stack.pop();
        cl.verify_oops_from(task.obj());
      }
    }

    Atomic::add(&_processed, processed, memory_order_relaxed);
  }
};

class ShenandoahVerifierMarkedRegionTask : public WorkerTask {
private:
  const char* _label;
  ShenandoahVerifier::VerifyOptions _options;
  ShenandoahHeap *_heap;
  MarkBitMap* _bitmap;
  ShenandoahLivenessData* _ld;
  volatile size_t _claimed;
  volatile size_t _processed;

public:
  ShenandoahVerifierMarkedRegionTask(MarkBitMap* bitmap,
                                     ShenandoahLivenessData* ld,
                                     const char* label,
                                     ShenandoahVerifier::VerifyOptions options) :
          WorkerTask("Shenandoah Verifier Marked Objects"),
          _label(label),
          _options(options),
          _heap(ShenandoahHeap::heap()),
          _bitmap(bitmap),
          _ld(ld),
          _claimed(0),
          _processed(0) {};

  size_t processed() {
    return Atomic::load(&_processed);
  }

  virtual void work(uint worker_id) {
    ShenandoahVerifierStack stack;
    ShenandoahVerifyOopClosure cl(&stack, _bitmap, _ld,
                                  ShenandoahMessageBuffer("%s, Marked", _label),
                                  _options);

    while (true) {
      size_t v = Atomic::fetch_and_add(&_claimed, 1u, memory_order_relaxed);
      if (v < _heap->num_regions()) {
        ShenandoahHeapRegion* r = _heap->get_region(v);
        if (!r->is_humongous() && !r->is_trash()) {
          work_regular(r, stack, cl);
        } else if (r->is_humongous_start()) {
          work_humongous(r, stack, cl);
        }
      } else {
        break;
      }
    }
  }

  virtual void work_humongous(ShenandoahHeapRegion *r, ShenandoahVerifierStack& stack, ShenandoahVerifyOopClosure& cl) {
    size_t processed = 0;
    HeapWord* obj = r->bottom();
    if (_heap->complete_marking_context()->is_marked(cast_to_oop(obj))) {
      verify_and_follow(obj, stack, cl, &processed);
    }
    Atomic::add(&_processed, processed, memory_order_relaxed);
  }

  virtual void work_regular(ShenandoahHeapRegion *r, ShenandoahVerifierStack &stack, ShenandoahVerifyOopClosure &cl) {
    size_t processed = 0;
    ShenandoahMarkingContext* ctx = _heap->complete_marking_context();
    HeapWord* tams = ctx->top_at_mark_start(r);

    // Bitmaps, before TAMS
    if (tams > r->bottom()) {
      HeapWord* start = r->bottom();
      HeapWord* addr = ctx->get_next_marked_addr(start, tams);

      while (addr < tams) {
        verify_and_follow(addr, stack, cl, &processed);
        addr += 1;
        if (addr < tams) {
          addr = ctx->get_next_marked_addr(addr, tams);
        }
      }
    }

    // Size-based, after TAMS
    {
      HeapWord* limit = r->top();
      HeapWord* addr = tams;

      while (addr < limit) {
        verify_and_follow(addr, stack, cl, &processed);
        addr += cast_to_oop(addr)->size();
      }
    }

    Atomic::add(&_processed, processed, memory_order_relaxed);
  }

  void verify_and_follow(HeapWord *addr, ShenandoahVerifierStack &stack, ShenandoahVerifyOopClosure &cl, size_t *processed) {
    if (!_bitmap->par_mark(addr)) return;

    // Verify the object itself:
    oop obj = cast_to_oop(addr);
    cl.verify_oop_standalone(obj);

    // Verify everything reachable from that object too, hopefully realizing
    // everything was already marked, and never touching further:
    if (!is_instance_ref_klass(obj->klass())) {
      cl.verify_oops_from(obj);
      (*processed)++;
    }
    while (!stack.is_empty()) {
      ShenandoahVerifierTask task = stack.pop();
      cl.verify_oops_from(task.obj());
      (*processed)++;
    }
  }
};

class VerifyThreadGCState : public ThreadClosure {
private:
  const char* const _label;
         char const _expected;

public:
  VerifyThreadGCState(const char* label, char expected) : _label(label), _expected(expected) {}
  void do_thread(Thread* t) {
    char actual = ShenandoahThreadLocalData::gc_state(t);
    if (actual != _expected) {
      fatal("%s: Thread %s: expected gc-state %d, actual %d", _label, t->name(), _expected, actual);
    }
  }
};

void ShenandoahVerifier::verify_at_safepoint(const char *label,
                                             VerifyForwarded forwarded, VerifyMarked marked,
                                             VerifyCollectionSet cset,
                                             VerifyLiveness liveness, VerifyRegions regions,
                                             VerifyGCState gcstate) {
  guarantee(ShenandoahSafepoin