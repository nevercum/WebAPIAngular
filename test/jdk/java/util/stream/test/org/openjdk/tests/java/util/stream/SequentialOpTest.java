/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.tests.java.util.stream;

import java.util.stream.LambdaTestHelpers;
import java.util.stream.OpTestCase;
import java.util.stream.StreamTestDataProvider;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.TestData;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * SequentialOpTest
 *
 * @author Brian Goetz
 */
public class SequentialOpTest extends OpTestCase {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class,
          groups = { "serialization-hostile" })
    public void testLazy(String name, TestData.OfRef<Integer> data) {
        Function<Integer, Integer> id = LambdaTestHelpers.identity();
        AtomicInteger counter = new AtomicIn