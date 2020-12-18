/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8230501
 * @library /test/lib
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @run testng/othervm ClassDataTest
 */

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jdk.internal.org.objectweb.asm.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.lang.invoke.MethodHandles.Lookup.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import static org.testng.Assert.*;

public class ClassDataTest {
    private static final Lookup LOOKUP = MethodHandles.lookup();

    @Test
    public void testOriginalAccess() throws IllegalAccessException {
        Lookup lookup = hiddenClass(20);
        assertTrue(lookup.hasFullPrivilegeAccess());

        int value = MethodHandles.classData(lookup, "_", int.class);
        assertEquals(value, 20);

        Integer i = MethodHandles.classData(lookup, "_", Integer.class);
        assertEquals(i.intValue(), 20);
    }

    /*
     * A lookup class with no class data.
     */
    @Test
    public void noClassData() throws IllegalAccessException {
        assertNull(MethodHandles.classData(LOOKUP, "_", Object.class));
    }

    @DataProvider(name = "teleportedLookup")
    private Object[][] teleportedLookup() throws ReflectiveOperationException {
        Lookup lookup = hiddenClass(30);
        Class<?> hc = lookup.lookupClass();
        assertClassData(lookup, 30);

        int fullAccess = PUBLIC|PROTECTED|PACKAGE|MODULE|PRIVATE;
        return new Object[][] {
                new Object[] { MethodHandles.privateLookupIn(hc, LOOKUP), fullAccess},
                new Object[] { LOOKUP.in(hc), fullAccess & ~(PROTECTED|PRIVATE) },
                new Object[] { lookup.dropLookupMode(PRIVATE), fullAccess & ~(PROTECTED|PRIVATE) },
        };
    }

    @Test(dataProvider = "teleportedLookup", expectedExceptions = { IllegalAccessException.class })
    public void illegalAccess(Lookup lookup, int access) throws IllegalAccessException {
        int lookupModes = lookup.loo