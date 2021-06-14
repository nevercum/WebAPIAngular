/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.BScenarios.singlethrd;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import com.sun.jdi.request.*;
import com.sun.jdi.event.*;

import java.util.*;
import java.io.*;

/**
 * This test is from the group of so-called Borland's scenarios and
 * implements the following test case:
 *     Suite 1 - Breakpoints (single threads)
 *     Test case:      TC2
 *     Description:    Line breakpoint & step over
 *     Steps:          1.Set breakpoint on line 19
 *                     2.Debug Main
 *                       X. Stops on line 19
 *                     3.Run | Step over three times
 *                       X. Stops on line 22 in Main.java
 *
 * When the test is starting debugee, debugger sets breakpoint at
 * the 49th line (method "performTest").
 * After the breakpoint is reached, debugger creates "step over" request
 * and resumes debugee. For the third <code>StepEvent</code> debugger checks line
 * number of one's location. It should be 52th line.
 *
 * In case, when line number of event is wrong, test fails.
 */

public class tc02x001 {

    public final static String SGL_READY = "ready";
    public final static String SGL_PERFORM = "perform";
    public final static String SGL_QUIT = "quit";

    private final static String prefix = "nsk.jdi.BScenarios.singlethrd.";
    private final static String debuggerName = prefix + "tc02x001";
    private final static String debugeeName = debuggerName + "a";

    private static int exitStatus;
    private static Log log;
    private static Debugee debugee;
    private static long waitTime;
    private final static int expectedStepEventCount = 3;
    private static int stepEventCount = 0;

    private ClassType debugeeClass;

    private static void display(String msg) {
        log.display(msg);
    }

    private static void complain(String msg) {
        log.complain("debugger FAILURE> " + msg + "\n");
    }

    public static void main(String argv[]) {
        System.exit(Consts.JCK_STATUS_BASE + run(argv, System.out));
    }

    public static int run(String argv[], PrintStream out) {

        exitStatus = Consts.TEST_PASSED;

        tc02x001 thisTest = new tc02x001();

        ArgumentHandler argHandler = new ArgumentHandler(argv);
        log = new Log(out, argHandler);

        waitTime = argHandler.getWaitTime() * 60000;

        debugee = Debugee.prepareDebugee(argHandler, log, debugeeName);

        try {
            thisTest.execTest();
        } catch (Throwable e) {
            complain("Unexpected " + e);
            exitStatus = Consts.TEST_FAILED;
            e.printStackTrace();
        } finally {
            debugee.resume();
            debugee.quit();
        }
        display("Test finished. exitStatus = " + exitStatus);

        return exitStatus;
    }

    private void execTest() throws Failure {

        debugeeClass = (ClassType)debugee.classByName(debugeeName);

        displa