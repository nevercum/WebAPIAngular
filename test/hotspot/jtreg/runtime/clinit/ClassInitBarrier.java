/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 *
 * @requires !vm.graal.enabled
 *
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -Xint                   -DTHROW=false -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -Xint                   -DTHROW=true  -Xcheck:jni ClassInitBarrier
 *
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=false -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=true  -Xcheck:jni ClassInitBarrier
 *
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=false -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=true  -Xcheck:jni ClassInitBarrier
 *
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=false -XX:CompileCommand=dontinline,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=true  -XX:CompileCommand=dontinline,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=false -XX:CompileCommand=dontinline,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=true  -XX:CompileCommand=dontinline,*::static* -Xcheck:jni ClassInitBarrier
 *
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=false -XX:CompileCommand=exclude,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=true  -XX:CompileCommand=exclude,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=false -XX:CompileCommand=exclude,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=true  -XX:CompileCommand=exclude,*::static* -Xcheck:jni ClassInitBarrier
 */

import jdk.test.lib.Asserts;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ClassInitBarrier {
    static {
        System.loadLibrary("ClassInitBarrier");

        if (!init()) {
            throw new Error("init failed");
        }
    }

    static native boolean init();

    static final boolean THROW = Boolean.getBoolean("THROW");

    static class Test {
        static class A {
            static {
                if (!init(B.class)) {
                    throw new Error("init failed");
                }

                changePhase(Phase.IN_PROGRESS);
                runTests();      // interpreted mode
                warmup();        // trigger compilation
                runTests();      // compiled mode

                ensureBlocked(); // ensure still blocked
                maybeThrow();    // fail initialization if needed

                changePhase(Phase.FINISHED);
            }

            static              void staticM(Runnable action) { action.run(); }
            static synchronized void staticS(Runnable action) { action.run(); }
            static native       void staticN(Runnable action);

            static int staticF;

            int f;
            void m() {}

            static native boolean init(Class<B> cls);
        }

        static class B extends A {}

        static void testInvokeStatic(Runnable action)       { A.staticM(action); }
        static void testInvokeStaticSync(Runnable action)   { A.staticS(action); }
        static void testInvokeStaticNative(Runnable action) { A.staticN(action); }

        static int  testGetStatic(Runnable action)    { int v = A.staticF; action.run(); return v;   }
        static void testPutStatic(Runnable action)    { A.staticF = 1;     action.run(); }
        static A    testNewInstanceA(Runnable action) { A obj = new A();   action.run(); return obj; }
        static B    testNewInstanceB(Runnable action) { B obj = new B();   action.run(); return obj; }

        static int  testGetField(A recv, Runnable action)      { int v = recv.f; action.run(); return v; }
        static void testPutField(A recv, Runnable action)      { recv.f = 1;     action.run(); }
        static void testInvokeVirtual(A recv, Runnable action) { recv.m();       action.run(); }

        static native void testInvokeStaticJNI(Runnable action);
        static native void testInvokeStaticSyncJNI(Runnable action);
        static native void testInvokeStaticNativeJNI(Runnable action);

        static native int  testGetStaticJNI(Runnable action);
        static native void testPutStaticJNI(Runnable action);
        static native A    testNewInstanceAJNI(Runnable action);
        static native B    testNewInstanceBJNI(Runnable action);

        static native int  testGetFieldJNI(A recv, Runnable action);
        static native void testPutFieldJNI(A recv, Runnable action);
        static native void testInvokeVirtualJNI(A recv, Runnable action);

        static void runTests() {
            checkBlockingAction(Test::testInvokeStatic);       // invokestatic
            checkBlockingAction(Test::testInvokeStaticSync);   // invokestatic
            checkBlockingAction(Test::testInvokeStaticNative); // invokestatic
            checkBlockingAction(Test::testGetStatic);          // getstatic
            checkBlockingAction(Test::testPutStatic);          // putstatic
            checkBlockingAction(Test::testNewInstanceA);       // new

            checkNonBlockingAction(Test::testInvokeStaticJNI);       // invokestatic
            checkNonBlockingAction(Test::testInvokeStaticSyncJNI);   // invokestatic
            checkNonBlockingAction(Test::testInvokeStaticNativeJNI); // invokestatic
            checkNonBlockingAction(Test::testGetStaticJNI);          // getstatic
            checkNonBlockingAction(Test::testPutStaticJNI);          // putstatic
            checkBlockingAction(Test::testNewInstanceAJNI);          // new

            A recv = testNewInstanceB(NON_BLOCKING.get());  // trigger B initialization
            checkNonBlockingAction(Test::testNewInstanceB); // new: NO BLOCKING: same thread: A being initialized, B fully initialized

            checkNonBlockingAction(recv, Test::testGetField);      // getfield
            checkNonBlockingAction(recv, Test::testPutField);      // putfield
            checkNonBlockingAction(recv, Test::testInvokeVirtual); // invokevirtual

            checkNonBlockingAction(Test::testNewInstanceBJNI);        // new: NO BLOCKING: same thread: A being initialized, B fully initialized
            checkNonBlockingAction(recv, Test::testGetFieldJNI);      // getfield
            checkNonBlockingAction(recv, Test::testPutFieldJNI);      // putfield
            checkNonBlockingAction(recv, Test::testInvokeVirtualJNI); // invokevirtual
        }

        static void warmup() {
            for (int i = 0; i < 20_000; i++) {
                testInvokeStatic(      NON_BLOCKING_WARMUP);
