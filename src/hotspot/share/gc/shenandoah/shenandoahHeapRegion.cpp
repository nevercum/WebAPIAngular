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
      report_illegal_transit