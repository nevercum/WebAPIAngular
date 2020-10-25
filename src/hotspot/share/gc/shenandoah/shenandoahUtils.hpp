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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHUTILS_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHUTILS_HPP

#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/allocation.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"
#include "services/memoryService.hpp"

class GCTimer;

class ShenandoahGCSession : public StackObj {
private:
  ShenandoahHeap* const _heap;
  GCTimer*  const _timer;
  GCTracer* const _tracer;

  TraceMemoryManagerStats _trace_cycle;
public:
  ShenandoahGCSession(GCCause::Cause cause);
  ~ShenandoahGCSession();
};

/*
 * ShenandoahGCPhaseTiming tracks Shenandoah specific timing information
 * of a GC phase
 */
class ShenandoahTimingsTracker : public StackObj {
private:
  static ShenandoahPhaseTimings::Phase  _current_phase;

  ShenandoahPhaseTimings* const         _timings;
  const ShenandoahPhaseTimings::Phase   _phase;
  ShenandoahPhaseTimings::Phase         _parent_phase;
  double _start;

public:
  ShenandoahTimingsTracker(ShenandoahPhaseTimings::Phase phase);
  ~ShenandoahTimingsTracker();

  static ShenandoahPhaseTimings::Phase current_phase() { return _current_phase; }

  static bool is_current_phase_valid();
};

/*
 * ShenandoahPausePhase tracks a STW pause and emits Shenandoah timing and
 * a corresponding JFR event
 */
class ShenandoahPausePhase : public ShenandoahTimingsTracker {
private:
  GCTraceTimeWrapper<LogLevel::Info, LOG_TAGS(gc)> _tracer;
  ConcurrentGCTimer* const _timer;

public:
  ShenandoahPausePhase(const char* title, ShenandoahPhaseTimings::Phase phase, bool log_heap_usage = false);
  ~ShenandoahPausePhase();
};

/*
 * ShenandoahConcurrentPhase tracks a concurrent GC phase and emits Shenandoah timing and
 * a corresponding JFR event
 */
class ShenandoahConcurrentPhase : public ShenandoahTimingsTracker {
private:
  GCTraceTimeWrapper<LogLevel::Info, LOG_TAGS(gc)> _tracer;
  ConcurrentGCTimer* const _timer;

public:
  ShenandoahConcurrentPhase(const char* title, ShenandoahPhaseTimings::Phase phase, bool log_heap_usage = false);
  ~ShenandoahConcurrentPhase();
};

/*
 * ShenandoahGCPhase tracks Shenandoah specific timing information
 * and emits a 