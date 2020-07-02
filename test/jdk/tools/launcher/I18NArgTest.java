/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8016110 8170832
 * @summary verify Japanese character in an argument are treated correctly
 * @compile -XDignore.symbol.file I18NArgTest.java
 * @run main I18NArgTest
 */
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;

public class I18NArgTest extends TestHelper {
    public static void main(String... args) throws IOException {
        if (!isWindows) {
            return;
        }
        if (!"MS932".equals(System.getProperty("sun.jnu.encoding"))) {
            System.err.println("MS932 encoding not set, test skipped");
            return;
        }
        if (args.length == 0) {
            execTest(0x30bd); // MS932 Katakana SO, 0x835C
        } else {
            testCharacters(args);
        }
    }
    static void execTest(int unicodeValue) {
        String hexValue = Integer.toHexString(unicodeValue);
        String unicodeStr = Character.toString((char)unicodeValue);
        execTest("\"" + unicodeStr + "\"", hexValue);
        execTest("\\" + unicodeStr + "\\", hexValue);
        execTest(" " + unicodeStr + " ", hexValue);
        execTest("'" + unicodeStr + "'", hexValue);
        execTest("\t" + unicodeStr + "\t", hexValue);
        execTest("*" + unicodeStr + "*", hexValue);
        execTest("?" + unicodeStr + "?", hexValue);

        execTest("\"" + unicodeStr + unicodeStr + "\"", hexValue + hexValue);
        execTest("\\" + unicodeStr + unicodeStr + "\\", hexValue + hexValue);
        execTest(" " + unicodeStr + unicodeStr + " ", hexValue + hexValue);
        execTest("'" + unicodeStr + unicodeStr + "'", hexValue + hexValue);
        execTest("\t" + unicodeStr + unicodeStr + "\t", hexValue + hexValue);
        execTest("*" + unicodeStr + unicodeStr + "*", hexValue + hexValue);
        execTest("?" + unicodeStr + unicodeStr + "?", hexValue + hexValue);

        execTest("\"" + unicodeStr + "a" + unicodeStr + "\"", hexValue + "0061" + hexValue);
        execTest("\\" + unicodeStr + "a" + unicodeStr + "\\", hexValue + "0061" + hexValue);
        execTest(" " + unicodeStr + "a" + unicodeStr + " ", hexValue + "0061"+ hexValue);
        execTest("'" + unicodeStr + "a" + unicodeStr + "'", hexValue + "0061"+ hexValue);
        execTest("\t" + unicodeStr + "a"