/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

package lib.jdb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Utils;
import jdk.test.lib.process.StreamPumper;

public class Jdb implements AutoCloseable {
    public Jdb(String... args) {
        ProcessBuilder pb = new ProcessBuilder(JDKToolFinder.getTestJDKTool("jdb"));
        pb.command().add("-J-Duser.language=en");
        pb.command().add("-J-Duser.country=US");
        pb.command().addAll(Arrays.asList(args));
        try {
            jdb = pb.start();
        } catch (IOException ex) {
            throw new RuntimeException("failed to launch pdb", ex);
        }
        try {
            StreamPumper stdout = new StreamPumper(jdb.getInputStream());
            StreamPumper stderr = new StreamPumper(jdb.getErrorStream());

            stdout.addPump(new StreamPumper.StreamPump(outputHandler));
            stderr.addPump(new StreamPumper.StreamPump(outputHandler));

            stdout.process();
            stderr.process();

            inputWriter = new PrintWriter(jdb.getOutputStream(), true);
        } catch (Throwable ex) {
            // terminate jdb if something went wrong
            jdb.destroy();
            throw ex;
        }
    }

    private final Process jdb;
    private final OutputHandler outputHandler = new OutputHandler();
    private final PrintWriter inputWriter;
    private final List<String> jdbOutput = new LinkedList<>();

    private static final String lineSeparator = System.getProperty("line.separator");
    // wait time before check jdb output (in ms)
    private static final long sleepTime = 1000;
    // max time to wait for  jdb output (in ms)
    private static final long timeout = Utils.adjustTimeo