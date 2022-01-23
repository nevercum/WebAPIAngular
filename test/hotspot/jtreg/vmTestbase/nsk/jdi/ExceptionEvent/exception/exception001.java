/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.ExceptionEvent.exception;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.io.*;
import java.util.Iterator;
import java.util.List;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;


// This class is the debugger in the test

public class exception001 {
    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int JCK_STATUS_BASE = 95;

    // time interval to wait for events
    static final int TIMEOUT_DELTA = 1000; // milliseconds

    // synchronization commands
    static final String COMMAND_READY = "ready";
    static final String COMMAND_QUIT  = "quit";
    static final String COMMAND_GO    = "go";
    static final String COMMAND_DONE  = "done";
    static final String COMMAND_ERROR = "error";

    // checked classes names
    static final String DEBUGGEE_NAME = "nsk.jdi.ExceptionEvent.exception.exception001a";

    static final String USER_EXCEPTION = DEBUGGEE_NAME + "Exception";
    static final String USER_ERROR     = DEBUGGEE_NAME + "Error";
    static final String USER_THROWABLE = DEBUGGEE_NAME + "Throwable";
    static final String JAVA_EXCEPTION = "java.lang.NumberFormatException";
    static final String JAVA_ERROR     = "java.lang.StackOverflowError";

    // scaffold objects
    static private Debugee debuggee;
    static private VirtualMachine vm;
    static private IOPipe pipe;
    static private Log log;
    static private ArgumentHandler argHandler;
    static private EventSet eventSet;

    // timeout for waiting events
    static private long eventTimeout;

    // mirrors for checked classes and threads in debuggee
    static private ExceptionRequest  checkedRequest;
    static private ReferenceType     checkedClass;
    static private ThreadReference   checkedThread;

    static private ReferenceType userException;
    static private ReferenceType userError;
    static private ReferenceType userThrowable;


    // results of receiving particular events
    static private boolean userExceptionReceived;
    static private boolean userErrorReceived;
    static private boolean userThrowableReceived;
    static private boolean javaExceptionReceived;
    static private boolean javaErrorReceived;

    // results of test execution
    static private boolean eventsReceived;
    static private boolean testFailed;

    // flag for EventHandler thread
    static private volatile boolean exceptionsThrown;

    // execute test from command line
    public static void main (String args[]) {
        System.exit(run(args, System.out) + JCK_STATUS_BASE);
    }

    // execute test from JCK-compatible harness
    public static int run(final String args[], final PrintStream out) {

        testFailed = false;
        userExceptionReceived = false;
        userErrorReceived = false;
        userThrowableReceived = false;
        javaExceptionReceived = false;
        javaErrorReceived = false;
        eventsReceived = false;

        argHandler = new ArgumentHandler(args);
        log = new Log(out, argHandler);
        eventTimeout = argHandler.getWaitTime() * 60 * 1000; // milliseconds

        // launch debuggee
        Binder binder = new Binder(argHandler, log);
        log.display("Connecting to debuggee");
        debuggee = binder.bindToDebugee(DEBUGGEE_NAME);
        debuggee.redirectStderr(log, "exception001a >");

        // create synchronization channel with debuggee
        pipe = debuggee.createIOPipe();
        vm = debuggee.VM();
        EventRequestManager erManager = vm.eventRequestManager();

        // resume debuggee
        log.display("Resuming debuggee");
        debuggee.resume();

        // catch exceptions while testing and finally quit debuggee
        try {

            // wait for debuggee started
            log.display("Waiting for command: " + COMMAND_READY);
            String command = pipe.readln();
            if (!command.equals(COMMAND_READY)) {
                throw new Failure("TEST BUG: unexpected debuggee's command: " + command);
            }

            // get mirrors of checked classes in debuggee
            log.display("Getting loaded classes in debuggee");
            if ((checkedClass = debuggee.classByName(DEBUGGEE_NAME)) == null) {
                throw new Failure("TEST BUG: cannot find " + DEBUGGEE_NAME);
            }

            if ((userException = debuggee.classByName(USER_EXCEPTION)) == null) {
                throw new Failure("TEST BUG: cannot find " + USER_EXCEPTION);
            }

            if ((userError = debuggee.classByName(USER_ERROR)) == null) {
                throw new Failure("TEST BUG: cannot find " + USER_ERROR);
            }

            if ((userThrowable = debuggee.classByName(USER_THROWABLE)) == null) {
                throw new Failure("TEST BUG: cannot find " + USER_THROWABLE);
            }

            // get mirror of main thread in debuggee
            log.display("Getting reference to main thread");
            Iterator threadIterator = vm.allThreads().iterator();
            while (threadIterator.hasNext()) {
                ThreadReference curThread = (ThreadReference) threadIterator.next();
                if (curThread.name().equals("main")) {
                     checkedThread = curThread;
                }
            }
            if (checkedThread == null) {
                throw new Failure("TEST BUG: unable to find reference to main thread");
            }

            // create ExceptionRequest for all throwable classes (initially disabled)
            log.display("Creating ExceptionRequest");
            boolean notifyCaught = true;
            boolean notifyUncaught = true;
            if ((checkedRequest = erManager.createExceptionRequest(null, notifyCaught, notifyUncaught)) == null) {
                throw new Failure("TEST BUG: unable to create ExceptionRequest");
            }

            // define separate thread for handling received events
            class EventHandler extends Thread {
                public void run() {
                    // handle events until all exceptions thrown and
                    // all expected events received
                    while (!(exceptionsThrown && eventsReceived)) {
                        eventSet = null;
                        try {
                            eventSet = vm.eventQueue().remove(TIMEOUT_DELTA);
                        } catch (InterruptedException e) {
                            log.complain("Unexpected InterruptedException while receiving event: " + e);
                            break;
                        }

                        if (eventSet == null) {
                            continue;
                        }

                        // handle each event from event set
                        EventIterator eventIterator = eventSet.eventIterator();
                        while (eventIterator.hasNext()) {

                            Event event = eventIterator.nextEvent();
                            log.display("\nEvent received:\n  " + event);

                            if (EventFilters.filtered(event))
                                continue;

                            // han