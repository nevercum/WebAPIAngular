
/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
 */


/*
 * @test
 *
 * @summary converted from VM Testbase nsk/monitoring/MemoryPoolMBean/isUsageThresholdExceeded/isexceeded001.
 * VM Testbase keywords: [quick, monitoring, quarantine]
 * VM Testbase comments: 8153598
 * VM Testbase readme:
 * DESCRIPTION
 *     The test checks that
 *         MemoryPoolMBean.isUsageThresholdExceeded()
 *     returns correct results, if the pool supports usage thresholds.
 *     The test sets a threshold that is greater than used value, allocates 100K,
 *     and chechs that getUsageThreshold(), getUsed(), isUsageThresholdExceeded()
 *     do not contradict each other, i.e.:
 *         1. if used value is greater or equal than threshold, then
 *            isUsageThresholdExceeded() is expected to return true;
 *         2. If used value is less than threshold, then isUsageThresholdExceeded()
 *            is expected to return false.
 *     The test implements direct access to the metrics.
 * COMMENT
 *     Fixed the bug
 *     4989235 TEST: The spec is updated accoring to 4982289, 4985742
 *
 * @library /vmTestbase
 *          /test/lib
 * @run main/othervm nsk.monitoring.MemoryPoolMBean.isUsageThresholdExceeded.isexceeded001
 */
