/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8159602 8170549 8171255 8171322 8254023
 * @summary Test annotations on module declaration.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.classfile
 * @build toolbox.ToolBox toolbox.JavacTask ModuleTestBase
 * @run main AnnotationsOnModules
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;

import com.sun.tools.classfile.Annotation;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.RuntimeInvisibleAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleAnnotations_attribute;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.OutputKind;

public class AnnotationsOnModules extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        AnnotationsOnModules t = new AnnotationsOnModules();
        t.runTests();
    }

    @Test
    public void testSimpleAnnotation(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1x");

        tb.writeJavaFiles(m1,
                          "@Deprecated module m1x { }");

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        new JavacTask(tb)
                .options("--module-source-path", moduleSrc.toString())
                .outdir(modulePath)
                .files(findJavaFiles(m1))
                .run()
                .writeAll();

        ClassFile cf = ClassFile.read(modulePath.resolve("m1x").resolve("module-info.class"));
        RuntimeVisibleAnnotations_attribute annotations = (RuntimeVisibleAnnotations_attribute) cf.attributes.map.get(Attribute.RuntimeVisibleAnnotations);

        if (annotations == null || annotations.annotations.length != 1) {
            throw new AssertionError("Annotations not correct!");
        }
    }

    @Test
    public void testSimpleJavadocDeprecationTag(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("src1/A");

        tb.writeJavaFiles(m1,
                "/** @deprecated */ module A { }");

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        List<String> warning = new JavacTask(tb)
                .options("--module-source-path", m1.getParent().toString(),
                        "-XDrawDiagnostics")
                .outdir(modulePath)
                .files(findJavaFiles(m1))
                .run()
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "module-info.java:1:20: compiler.warn.missing.deprecated.annotation",
                "1 warning");
        if (!warning.containsAll(expected)) {
            throw new AssertionError("Expected output not found. Expected: " + expected);
        }

        Path m2 = base.resolve("src2/B");

        tb.writeJavaFiles(m2,
                "module B { requires A; }");
        String log = new JavacTask(tb)
                .options("--module-source-path", m2.getParent().toString(),
                        "--module-path", modulePath.toString(),
                        "-XDrawDiagnostics")
                .outdir(modulePath)
                .f