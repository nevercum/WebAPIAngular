/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2013, 2019, Red Hat, Inc. All rights reserved.
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
#include "gc/shared/space.inline.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/powerOfTwo.hpp"

size_t ShenandoahHeapRegion::RegionCount = 0;
size_t ShenandoahHeapRegion::RegionSizeBytes = 0;
size_t ShenandoahHeapRegion::RegionSizeWords = 0;
size_t ShenandoahHeapRegion::RegionSizeBytesShift = 0;
size_t ShenandoahHeapRegion::RegionSizeWordsShift = 0;
size_t ShenandoahHeapRegion::RegionSizeBytesMask = 0;
size_t ShenandoahHeapRegion::RegionSizeWordsMask = 0;
size_t ShenandoahHeapRegion::HumongousThresholdBytes = 0;
size_t ShenandoahHeapRegion::HumongousThresholdWords = 0;
size_t ShenandoahHeapRegion::MaxTLABSizeBytes = 0;
size_t ShenandoahHeapRegion::MaxTLABSizeWords = 0;

ShenandoahHeapRegion::ShenandoahHeapRegion(HeapWord* start, size_t index, bool committed) :
  _index(index),
  _bottom(start),
  _end(start + RegionSizeWords),
  _new_top(nullptr),
  _empty_time(os::elapsedTime()),
  _state(committed ? _empty_committed : _empty_uncommitted),
  _top(start),
  _tlab_allocs(0),
  _gclab_allocs(0),
  _live_data(0),
  _critical_pins(0),
  _update_watermark(start) {

  assert(Universe::on_page_boundary(_bottom) && Universe::on_page_boundary(_end),
         "invalid space boundaries");
  if (ZapUnusedHeapArea && committed) {
    SpaceMangler::mangle_region(MemRegion(_bottom, _end));
  }
}

void ShenandoahHeapRegion::report_illegal_transition(const char *method) {
  stringStream ss;
  ss.print("Illegal region state transition from \"%s\", at %s\n  ", region_state_to_string(_state), method);
  print_on(&ss);
  fatal("%s", ss.freeze());
}

void ShenandoahHeapRegion::make_regular_allocation() {
  shenandoah_assert_heaplocked();

  switch (_state) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
      set_state(_regular);
    case _regular:
    case _pinned:
      return;
    default:
      report_illegal_transition("regular allocation");
  }
}

void ShenandoahHeapRegion::make_regular_bypass() {
  shenandoah_assert_heaplocked();
  assert (ShenandoahHeap::heap()->is_full_gc_in_progress() || ShenandoahHeap::heap()->is_degenerated_gc_in_progress(),
          "only for full or degen GC");

  switch (_state) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
    case _cset:
    case _humongous_start:
    case _humongous_cont:
      set_state(_regular);
      return;
    case _pinned_cset:
      set_state(_pinned);
      return;
    case _regular:
    case _pinned:
      return;
    default:
      report_illegal_transition("regular bypass");
  }
}

void ShenandoahHeapRegion::make_humongous_start() {
  shenandoah_assert_heaplocked();
  switch (_state) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
      set_state(_humongous_start);
      return;
    default:
      report_illegal_transition("humongous start allocation");
  }
}

void ShenandoahHeapRegion::make_humongous_start_bypass() {
  shenandoah_assert_heaplocked();
  assert (ShenandoahHeap::heap()->is_full_gc_in_progress(), "only for full GC");

  switch (_state) {
    case _empty_committed:
    case _regular:
    case _humongous_start:
    case _humongous_cont:
      set_state(_humongous_start);
      return;
    default:
      report_illegal_transition("humongous start bypass");
  }
}

void ShenandoahHeapRegion::make_humongous_cont() {
  shenandoah_assert_heaplocked();
  switch (_state) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
     set_state(_humongous_cont);
      return;
    default:
      report_illegal_transition("humongous continuation allocation");
  }
}

void ShenandoahHeapRegion::make_humongous_cont_bypass() {
  shenandoah_assert_heaplocked();
  assert (ShenandoahHeap::heap()->is_full_gc_in_progress(), "only for full GC");

  switch (_state) {
    case _empty_committed:
    case _regular:
    case _humongous_start:
    case _humongous_cont:
      set_state(_humongous_cont);
      return;
    default:
      report_illegal_transition("humongous continuation bypass");
  }
}

void ShenandoahHeapRegion::make_pinned() {
  shenandoah_assert_heaplocked();
  assert(pin_count() > 0, "Should have pins: " SIZE_FORMAT, pin_count());

  switch (_state) {
    case _regular:
      set_state(_pinned);
    case _pinned_cset:
    case _pinned:
      return;
    case _humongous_start:
      set_state(_pinned_humongous_start);
    case _pinned_humongous_start:
      return;
    case _cset:
      _state = _pinned_cset;
      return;
    default:
      report_illegal_transition("pinning");
  }
}

void ShenandoahHeapRegion::make_unpinned() {
  shenandoah_assert_heaplocked();
  assert(pin_count() == 0, "Should not have pins: " SIZE_FORMAT, pin_count());

  switch (_state) {
    case _pinned:
      set_state(_regular);
      return;
    case _regular:
    case _humongous_start:
      return;
    case _pinned_cset:
      set_state(_cset);
      return;
    case _pinned_humongous_start:
      set_state(_humongous_start);
      return;
    default:
      report_illegal_transition("unpinning");
  }
}

void ShenandoahHeapRegion::make_cset() {
  shenandoah_assert_heaplocked();
  switch (_state) {
    case _regular:
      set_state(_cset);
    case _cset:
      return;
    default:
      report_illegal_transition("cset");
  }
}

void ShenandoahHeapRegion::make_trash() {
  shenandoah_assert_heaplocked();
  switch (_state) {
    case _cset:
      // Reclaiming cset regions
    case _humongous_start:
    case _humongous_cont:
      // Reclaiming humongous regions
    case _regular:
      // Immediate region reclaim
      set_state(_trash);
      return;
    default:
      report_illegal_transition("trashing");
  }
}

void ShenandoahHeapRegion::make_trash_immediate() {
  make_trash();

  // On this path, we know there are no marked objects in the region,
  // tell marking context about it to bypass bitmap resets.
  ShenandoahHeap::heap()->complete_marking_context()->reset_top_bitmap(this);
}

void ShenandoahHeapRegion::make_empty() {
  shenandoah_assert_heaplocked();
  switch (_state) {
    case _trash:
      set_state(_empty_committed);
      _empty_time = os::elapsedTime();
      return;
    default:
      report_illegal_transition("emptying");
  }
}

void ShenandoahHeapRegion::make_uncommitted() {
  shenandoah_assert_heaplocked();
  switch (_state) {
    case _empty_committed:
      do_uncommit();
      set_state(_empty_uncommitted);
      return;
    default:
      report_illegal_transition("uncommiting");
  }
}

void ShenandoahHeapRegion::make_committed_bypass() {
  shenandoah_assert_heaplocked();
  assert (ShenandoahHeap::heap()->is_full_gc_in_progress(), "only for full GC");

  switch (_state) {
    case _empty_uncommitted:
      do_commit();
      set_state(_empty_committed);
      return;
    default:
      report_illegal_transition("commit bypass");
  }
}

void ShenandoahHeapRegion::reset_alloc_metadata() {
  _tlab_allocs = 0;
  _gclab_allocs = 0;
}

size_t ShenandoahHeapRegion::get_shared_allocs() const {
  return used() - (_tlab_allocs + _gclab_allocs) * HeapWordSize;
}

size_t ShenandoahHeapRegion::get_tlab_allocs() const {
  return _tlab_allocs * HeapWordSize;
}

size_t ShenandoahHeapRegion::get_gclab_allocs() const {
  return _gclab_allocs * HeapWordSize;
}

void ShenandoahHeapRegion::set_live_data(size_t s) {
  assert(Thread::current()->is_VM_thread(), "by VM thread");
  _live_data = (s >> LogHeapWordSize);
}

void ShenandoahHeapRegion::print_on(outputStream* st) const {
  st->print("|");
  st->print(SIZE_FORMAT_W(5), this->_index);

  switch (_state) {
    case _empty_uncommitted:
      st->print("|EU ");
      break;
    case _empty_committed:
      st->print("|EC ");
      break;
    case _regular:
      st->print("|R  ");
      break;
    case _humongous_start:
      st->print("|H  ");
      break;
    case _pinned_humongous_start:
      st->print("|HP ");
      break;
    case _humongous_cont:
      st->print("|HC ");
      break;
    case _cset:
      st->print("|CS ");
      break;
    case _trash:
      st->print("|T  ");
      break;
    case _pinned:
      st->print("|P  ");
      break;
    case _pinned_cset:
      st->print("|CSP");
      break;
    default:
      ShouldNotReachHere();
  }

#define SHR_PTR_FORMAT "%12" PRIxPTR

  st->print("|BTE " SHR_PTR_FORMAT  ", " SHR_PTR_FORMAT ", " SHR_PTR_FORMAT,
            p2i(bottom()), p2i(top()), p2i(end()));
  st->print("|TAMS " SHR_PTR_FORMAT,
            p2i(ShenandoahHeap::heap()->marking_context()->top_at_mark_start(const_cast<ShenandoahHeapRegion*>(this))));
  st->print("|UWM " SHR_PTR_FORMAT,
            p2i(_update_watermark));
  st->print("|U " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(used()),                proper_unit_for_byte_size(used()));
  st->print("|T " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_tlab_allocs()),     proper_unit_for_byte_size(get_tlab_allocs()));
  st->print("|G " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_gclab_allocs()),    proper_unit_for_byte_size(get_gclab_allocs()));
  st->print("|S " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_shared_allocs()),   proper_unit_for_byte_size(get_shared_allocs()));
  st->print("|L " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_live_data_bytes()), proper_unit_for_byte_size(get_live_data_bytes()));
  st->print("|CP " SIZE_FORMAT_W(3), pin_count());
  st->cr();

#undef SHR_PTR_FORMAT
}

void ShenandoahHeapRegion::oop_iterate(OopIterateClosure* blk) {
  if (!is_active()) return;
  if (is_humongous()) {
    oop_iterate_humongous(blk);
  } else {
    oop_iterate_objects(blk);
  }
}

void ShenandoahHeapRegion::