/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

package compiler.intrinsics.mathexact;

import jdk.test.lib.Utils;

import java.util.Random;

/**
 * The class depends on Utils class from testlibrary package.
 * It uses factory method that obtains random generator.
 */
public class Verify {
    public static String throwWord(boolean threw) {
        return (threw ? "threw" : "didn't throw");
    }

    public static void verifyResult(UnaryMethod method, int result1, int result2, boolean exception1, boolean exception2, int value) {
        if (exception1 != exception2) {
            throw new RuntimeException("Intrinsic version [" + method.name() + "]" + throwWord(exception1) + " exception, NonIntrinsic version" + throwWord(exception2) + " for: " + value);
        }
        if (result1 != result2) {
            throw new RuntimeException("Intrinsic version [" + method.name() + "] returned: " + result1 + " while NonIntrinsic version returned: " + result2);
        }
    }

    public static void verifyResult(UnaryLongMethod method, long result1, long result2, boolean exception1, boolean exception2, long value) {
        if (exception1 != exception2) {
            throw new RuntimeException("Intrinsic version [" + method.name() + "]" + throwWord(exception1) + " exception, NonIntrinsic version" + throwWord(exception2) + " for: " + value);
        }
        if (result1 != result2) {
            throw new RuntimeException("Intrinsic version [" + method.name() + "] returned: " + result1 + " while NonIntrinsic version returned: " + result2);
        }
    }

    private static void verifyResult(BinaryMethod method, int result1, int result2, boolean exception1, boolean exception2, int a, int b) {
        if (exception1 != exception2) {
            throw new RuntimeException("Intrinsic version [" + method.name() + "]" + throwWord(exception1) + " exception, NonIntrinsic version " + throwWord(exception2) + " for: " + a + " + " + b);
        }
        if (result1 != result2) {
            throw new RuntimeException("Intrinsic version [" + method.name() + "] returned: " + result1 + " while NonIntrinsic version returned: " + result2);
        }
    }

    private static void verifyResult(BinaryLongMethod method, long result1, long result2, boolean exception1, boolean exception2, long a, long b) {
        if (exception1 != exception2) {
            throw new RuntimeException("Intrinsic version [" + method.name() + "]" + throwWord(exception1) + " exception, NonIntrinsic version " + throwWord(exception2) + " for: " + a + " + " + b);
        }
        if (result1 != result2) {
            throw new RuntimeException("Intrinsic version [" + method.name() + "] returned: " + result1 + " while NonIntrinsic version returned: " + result2);
        }
    }


    public static void verifyUnary(int a, UnaryMethod method) {
        boolean exception1 = false, exception2 = false;
        int result1 = 0, result2 = 0;
        try {
            result1 = method.checkMethod(a);
        } catch (ArithmeticException e) {
            exception1 = true;
        }
        try {
            result2 = method.safeMethod(a);
        } catch (ArithmeticException e) {
            exception2 = true;
        }

        verifyResult(method, result1, result2, exception1, exception2, a);
    }

    public static void verifyUnary(long a, UnaryLongMethod method) {
        boolean exception1 = false, exception2 = false;
        long result1 = 0, result2 = 0;
        try {
            result1 = method.checkMethod(a);
        } catch (ArithmeticException e) {
            exception1 = true;
        }
        try {
            result2 = method.safeMethod(a);
        } catch (ArithmeticException e) {
            exception2 = true;
        }

        verifyResult(method, result1, result2, exception1, exception2, a);
    }


    public static void verifyBinary(int a, int b, BinaryMethod method) {
        boolean exception1 = false, exception2 = false;
        int result1 = 0, result2 = 0;
        try {
            result1 = method.checkMethod(a, b);
        } catch (ArithmeticException e) {
            exception1 = true;
        }
        try {
            result2 = method.safeMethod(a, b);
        } catch (ArithmeticException e) {
            exception2 = true;
        }

        verifyResult(method, result1, result2, exception1, exception2, a, b);
    }

    public static void verifyBinary(long a, long b, BinaryLongMethod method) {
        boolean exception1 = false, exception2 = false;
        long result1 = 0, result2 = 0;
        try {
            result1 = method.checkMethod(a, b);
        } catch (ArithmeticException e) {
            exception1 = true;
        }
        try {
            result2 = method.safeMethod(a, b);
        } catch (ArithmeticException e) {
            exception2 = true;
        }

        verifyResult(method, result1, result2, exception1, exception2, a, b);
    }


    public static class LoadTest {
        public static Random rnd = Utils.getRandomInstance();
        public static int[] values = new int[256];

        public static void init() {
            for (int i = 0; i < values.length; ++i) {
                values[i] = rnd.nextInt();
            }
        }

        public static void verify(BinaryMethod method) {
            for (int i = 0; i < 50000; ++i) {
                Verify.verifyBinary(values[i & 255], values[i & 255] - i, method);
                Verify.verifyBinary(values[i & 255] + i, values[i & 255] - i, method);
                Verify.verifyBinary(values[i & 255], values[i & 255], method);
                if ((i & 1) == 1 && i > 5) {
                    Verify.verifyBinary(values[i & 255] + i, values[i & 255] - i, method);
                } else {
                    Verify.verifyBinary(values[i & 255] - i, values[i & 255] + i, method);
                }
                Verify.verifyBinary(values[i & 255], values[(i + 1) & 255], method);
            }
        }
    }

    public static class NonConstantTest {
        public static Random rnd = Utils.getRandomInstance();
        public static int[] values = new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE };

        public static void verify(BinaryMethod method) {
            for (int i = 0; i < 50000; ++i) {
                int rnd1 = rnd.nextInt(), rnd2 = rnd.nextInt