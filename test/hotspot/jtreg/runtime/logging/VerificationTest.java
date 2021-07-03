/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8150083 8234656
 * @summary test enabling and disabling verification logging and verification log levels
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver VerificationTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class VerificationTest {

    static void analyzeOutputOn(ProcessBuilder pb, boolean isLogLevelInfo) throws Exception {
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("[verification]");
        output.shouldContain("Verifying class VerificationTest$InternalClass with new format");
        output.shouldContain("Verifying method VerificationTest$InternalClass.<init>()V");
        output.shouldContain("End class verification for: VerificationTest$InternalClass");

        if (isLogLevelInfo) {
            // logging level 'info' should not output stack map and opcode data.
            output.shouldNotContain("[verification] StackMapTable: frame_count");
            output.shouldNotContain("[verification] offset = 0,  opcode =");

        } else { // log level debug
            output.shouldContain("[debug][verification] StackMapTable: frame_count");
    