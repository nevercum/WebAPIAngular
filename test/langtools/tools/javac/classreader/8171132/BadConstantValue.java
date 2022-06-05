/*
 * Copyright 2016 Google, Inc.  All rights reserved.
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
 * @bug 8171132
 * @summary Improve class reading of invalid or out-of-range ConstantValue attributes
 * @modules jdk.jdeps/com.sun.tools.classfile
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.jvm
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build BadConstantValue
 * @run main BadConstantValue
 */

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ClassWriter;
import com.sun.tools.classfile.Field;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.ClassFinder.BadClassFile;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Names;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Objects;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class BadConstantValue {

    static final File classesdir = new File("badconstants");

    public static void main(String[] args) throws Exception {
        // report errors for ConstantValues of the wrong type
        testInvalidConstantType("int");
        testInvalidConstantType("short");
        testInvalidConstantType("byte");
        testInvalidConstantType("char");
        testInvalidConstantType("boolean");

        // report errors for ConstantValues outside the expected range
        testValidConstRange("int", Integer.MAX_VALUE);
        testValidConstRange("int", Integer.MIN_VALUE);

        testValidConstRange("short", Short.MAX_VALUE);
        testValidConstRange("short", Short.MIN_VALUE);
        testInvalidConstRange("short", Short.MAX_VALUE + 1);
        testInvalidConstRange("short", Short.MIN_VALUE - 1);

        testValidConstRange("byte", Byte.MAX_VALUE);
        testValidConstRan