/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7036582
 * @summary Some new tests for the add method and constructor with MathContext.
 * @run main RangeTests
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+EliminateAutoBox -XX:AutoBoxCacheMax=20000 RangeTests
 * @author Sergey V. Kuksenko
 */

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

public class RangeTests {


    private static int addTest(BigDecimal arg1, BigDecimal arg2, BigDecimal expectedResult) {
        int failures = 0;
        BigDecimal result = arg1.add(arg2);
        if (!result.equals(expectedResult)) {
            System.out.println("Sum:" +
                    arg1 + " + " +
                    arg2 + " == " +
                    result + "; expected  " +
                    expectedResult
            );
            failures++;
        }
        result = arg2.add(arg1);
        if (!result.equals(expectedResult)) {
            System.out.println("Sum:" +
                    arg2 + " + " +
                    arg1 + " == " +
                    result + "; expected  " +
                    expectedResult
            );
            failures++;
        }
        return failures;
    }

    /*
     *  Test BigDecimal.add(BigDecimal) when values are withing different ranges:
     *  1. within 32 bits
     *  2. within 64 bits
     *  3. outside 64 bits.
     */
    private static int addBoundaryTest() {
        int failures = 0;
        failures += addTest(
                new BigDecimal("85070591730234615847396907784232501249"),
                BigDecimal.valueOf(0),
                new BigDecimal("85070591730234615847396907784232501249") );
        failures += addTest(
                new BigDecimal("-85070591730234615847396907784232501249"),
                BigDecimal.valueOf(0),
                new BigDecimal("-85070591730234615847396907784232501249") );
        failures += addTest(
                new BigDecimal("85070591730234615847396907784232501249"),
                BigDecimal.valueOf(1),
                new BigDecimal("85070591730234615847396907784232501250") );
        failures += addTest(
                new BigDecimal("85070591730234615847396907784232501249"),
                BigDecimal.valueOf(-1),
                new BigDecimal("85070591730234615847396907784232501248") );
        failures += addTest(
                new BigDecimal("-85070591730234615847396907784232501250"),
                BigDecimal.valueOf(-1),
                new BigDecimal("-85070591730234615847396907784232501251") );
        failures += addTest(
                new BigDecimal("-85070591730234615847396907784232501249"),
                BigDecimal.valueOf(1),
                new BigDecimal("-85070591730234615847396907784232501248") );
        failures += addTest(
                new BigDecimal("147573952589676412927"),
                BigDecimal.valueOf(Integer.MAX_VALUE),
                new BigDecimal("147573952591823896574") );
        failures += addTest(
                new BigDecimal("-147573952589676412927"),
                BigDecimal.valueOf(Integer.MAX_VALUE),
                new BigDecimal("-147573952587528929280") );
        failures += addTest(
                new BigDecimal("79228162514264337593543950335"),
                BigDecimal.valueOf(999),
                new BigDecimal("79228162514264337593543951334") );
        failures += addTest(
                new BigDecimal("79228162514264337593543950335"),
                BigDecimal.valueOf(Integer.MAX_VALUE/2),
                new BigDecimal("79228162514264337594617692158") );
        failures += addTest(
                new BigDecimal("79228162514264337593543950335"),
                BigDecimal.valueOf(Integer.MIN_VALUE/2),
                new BigDecimal("79228162514264337592470208511") );
        failures += addTest(
                new BigDecimal("-79228162514264337593543950335"),
                BigDecimal.valueOf(Integer.MAX_VALUE/2),
                new BigDecimal("-79228162514264337592470208512") );
        failures += addTest(
                new BigDecimal("79228162514264337593543950335"),
                BigDecimal.valueOf(-(Integer.MIN_VALUE/2)),
                new BigDecimal("79228162514264337594617692159") );
        failures += addTest(
                new BigDecimal("79228162514264337593543950335"),
      