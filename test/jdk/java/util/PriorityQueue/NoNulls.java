/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Martin Buchholz with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @bug 6950540
 * @summary Attempt to add a null throws NullPointerException
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

public class NoNulls {
    void test(String[] args) throws Throwable {
        final Comparator<String> nullTolerantComparator
            = new Comparator<>() {
            public int compare(String x, String y) {
                return (x == null ? -1 :
                        y == null ? 1 :
                        x.compareTo(y));
            }};

        final SortedSet<String> nullSortedSet
            = new TreeSet<>(nullTolerantComparator);
        nullSortedSet.add(null);

        final PriorityQueue<String> nullPriorityQueue
            = new PriorityQueue<>() {
            public Object[] toArray() { return new Object[] { null };}};

        final Collection<String> nullCollection = new ArrayList<>();
        nullCollection.add(null);

        THROWS(NullPointerException.class,
               new F() { void f() {
                   new PriorityQueue<String>(nullCollection);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>(nullCollection);
               }},
               new F() { void f() {
                   new ArrayBlockingQueue<String>(10, false, nullCollection);
               }},
               new F() { void f() {
                   new ArrayBlockingQueue<String>(10, true, nullCollection);
               }},
               new F() { void f() {
                   new LinkedBlockingQueue<String>(nullCollection);
               }},
               new F() { void f() {
                   new LinkedBlockingDeque<String>(nullCollection);
               }},

               new F() { void f() {
                   new PriorityQueue<String>((