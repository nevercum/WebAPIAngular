/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.test.lib.compiler.CompilerUtils;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * @test
 * @bug 8174826
 * @library /lib/testlibrary /test/lib
 * @modules jdk.charsets jdk.compiler jdk.jlink
 * @build SuggestProviders jdk.test.lib.compiler.CompilerUtils
 * @run testng SuggestProviders
 */

public class SuggestProviders {
    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mods");

    private static final String MODULE_PATH =
        Paths.get(JAVA_HOME, "jmods").toString() +
        File.pathSeparator + MODS_DIR.toString();

    // the names of the modules in this test
    private static String[] modules = new String[] {"m1", "m2", "m3"};


    private static boolean hasJmods() {
        if (!Files.exists(Paths.get(JAVA_HOME, "jmods"))) {
            System.err.println("Test skipped. NO jmods directory");
            return false;
        }
        return true;
    }

    /*
     * Compiles all modules used by the test
     */
    @BeforeTest
    public void compileAll() throws Throwable {
        if (!hasJmods()) return;

        for (String mn : modules) {
            Path msrc = SRC_DIR.resolve(mn);
            assertTrue(CompilerUtils.compile(msrc, MODS_DIR,
                "--module-source-path", SRC_DIR.toString()));
        }
    }

    // check a subset of services used by java.base
    private final List<String> JAVA_BASE_USES = List.of(
        "uses java.lang.System$LoggerFinder",
        "uses java.net.ContentHandlerFactory",
        "uses java.net.spi.URLStreamHandlerProvider",
        "uses java.nio.channels.spi.AsynchronousChannelProvider",
        "uses java.nio.channels.spi.SelectorProvider",
        "uses java.nio.charset.spi.CharsetProvider",
        "uses java.nio.file.spi.FileSystemProvider",
        "uses java.nio.file.spi.FileTypeDetector",
        "uses java.security.Provider",
        "uses java.util.spi.ToolProvider"
    );

    private final List<String> JAVA_BASE_PROVIDERS = List.of(
        "java.base provides java.nio.file.spi.FileSystemProvider used by java.base"
    );

    private final List<String> SYSTEM_PROVIDERS = List.of(
        "jdk.charsets provides java.nio.charset.spi.CharsetProvider used by java.base",
        "jdk.compiler provides java.util.spi.ToolProvider used by java.base",
        "jdk.compiler provides javax.tools.JavaCompiler used by java.compiler",
        "jdk.jlink provides jdk.tools.jlink.plugin.Plugi