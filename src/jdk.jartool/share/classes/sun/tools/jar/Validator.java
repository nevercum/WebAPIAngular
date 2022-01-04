/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.tools.jar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Opens;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.jar.JarFile.MANIFEST_NAME;
import static sun.tools.jar.Main.VERSIONS_DIR;
import static sun.tools.jar.Main.VERSIONS_DIR_LENGTH;
import static sun.tools.jar.Main.MODULE_INFO;
import static sun.tools.jar.Main.getMsg;
import static sun.tools.jar.Main.formatMsg;
import static sun.tools.jar.Main.formatMsg2;
import static sun.tools.jar.Main.toBinaryName;

final class Validator {

    private final Map<String,FingerPrint> classes = new HashMap<>();
    private final Main main;
    private final ZipFile zf;
    private boolean isValid = true;
    private Set<String> concealedPkgs = Collections.emptySet();
    private ModuleDescriptor md;
    private String mdName;

    private Validator(Main main, ZipFile zf) {
        this.main = main;
        this.zf = zf;
        checkModuleDescriptor(MODULE_INFO);
    }

    static boolean validate(Main main, ZipFile zf) throws IOException {
        return new Validator(main, zf).validate();
    }

    private boolean validate() {
        try {
            zf.stream()
              .filter(e -> e.getName().endsWith(".class"))
              .map(this::getFingerPrint)
              .filter(FingerPrint::isClass)    // sk