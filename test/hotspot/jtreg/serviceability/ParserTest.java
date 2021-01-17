/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that the diagnostic command arguemnt parser works
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ParserTest
 */

import java.math.BigInteger;

import jdk.test.whitebox.parser.DiagnosticCommand;
import jdk.test.whitebox.parser.DiagnosticCommand.DiagnosticArgumentType;
import jdk.test.whitebox.WhiteBox;

public class ParserTest {
    WhiteBox wb;

    public ParserTest() throws Exception {
        wb = WhiteBox.getWhiteBox();

        testNanoTime();
        testJLong();
        testBool();
        testQuotes();
        testMemorySize();
        testSingleLetterArg();
    }

    public static void main(String... args) throws Exception  {
         new ParserTest();
    }

    public void testNanoTime() throws Exception {
        String name = "name";
        DiagnosticCommand arg = new DiagnosticCommand(name,
                "desc", DiagnosticArgumentType.NANOTIME,
                false, "0");
        DiagnosticCommand[] args = {arg};

        BigInteger bi = new BigInteger("7");
        //These should work
        parse(name, bi.toString(), name + "=7ns", args);

        bi = bi.multiply(BigInteger.valueOf(1000));
        parse(name, bi.toString(), name + "=7us", args);

        bi = bi.multiply(BigInteger.valueOf(1000));
        parse(name, bi.toString(), name + "=7ms", args);

        bi = bi.multiply(BigInteger.valueOf(1000));
        parse(name, bi.toString(), name + "=7s", args);

        bi = bi.multiply(BigInteger.valueOf(60));
        parse(name, bi.toString() , name + "=7m", args);

        bi = bi.multiply(BigInteger.valueOf(60));
        parse(name, bi.toString() , name + "=7h", args);

        bi = bi.multiply(BigInteger.valueOf(24));
        parse(name, bi.toString() , name + "=7d", args);

        parse(name, "0", name + "=0", args);

        shouldFail(name + "=7xs", args);
        shouldFail(name + "=7mms", args);
        shouldFail(name + "=7f", args);
        //Currently, only value 0 is allowed without unit
        shouldFail(name + "=7", args);
    }

    public void testJLong() throws Exception {
        String name = "name";
        DiagnosticCommand arg = new DiagnosticCommand(name,
                "desc", DiagnosticArgumentType.JLONG,
                false, "0");
        DiagnosticCommand[] args = {arg};

        wb.parseCommandLine(name + "=10", ',', args);
        parse(name, "10", name + "=10", args);
        parse(name, "-5", name + "=-5", args);

        //shouldFail(name + "=12m", args); <-- should fail, doesn't
    }

    public void testBool() throws Exception {
        String name = "name";
        DiagnosticCommand arg = new DiagnosticCommand(name,
                "desc", DiagnosticArgumentType.BOOLEAN,
                false, "false");
        DiagnosticCommand[] args = {arg};

        parse(name, "true", name + "=true", args);
        parse(name, "false", name + "=false", args);
