/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Phaser;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @test
 * @bug 8066859
 * @summary Check that AQS-based locks, conditions, and CountDownLatches do not fail when encountering OOME
 * @run main/othervm -XX:-UseGCOverheadLimit -Xmx24M -XX:-UseTLAB OOMEInAQS
 */

public class OOMEInAQS extends Thread {
    static final int NTHREADS = 2; // intentionally not a scalable test; > 2 is very slow
    static final int NREPS = 100;
    // statically allocate
    static final ReentrantLock mainLock = new ReentrantLock();
    static final Condition condition = mainLock.newCondition();
    static final CountDownLatch started = new CountDownLatch(1);
    static final CountDownLatch filled = new CountDownLatch(1);
    static final CountDownLatch canFill = new CountDownLatch(NTHRE