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
        int lookupModes = lookup.lookupModes();
        assertTrue((lookupModes & ORIGINAL) == 0);
        assertEquals(lookupModes, access);
        MethodHandles.classData(lookup, "_", int.class);
    }

    @Test(expectedExceptions = { ClassCastException.class })
    public void incorrectType() throws IllegalAccessException {
        Lookup lookup = hiddenClass(20);
        MethodHandles.classData(lookup, "_", Long.class);
    }

    @Test(expectedExceptions = { IndexOutOfBoundsException.class })
    public void invalidIndex() throws IllegalAccessException {
        Lookup lookup = hiddenClass(List.of());
        MethodHandles.classDataAt(lookup, "_", Object.class, 0);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void unboxNull() throws IllegalAccessException {
        List<Integer> list = new ArrayList<>();
        list.add(null);
        Lookup lookup = hiddenClass(list);
        MethodHandles.classDataAt(lookup, "_", int.class, 0);
    }

    @Test
    public void nullElement() throws IllegalAccessException {
        List<Object> list = new ArrayList<>();
        list.add(null);
        Lookup lookup = hiddenClass(list);
        assertTrue(MethodHandles.classDataAt(lookup, "_", Object.class, 0) == null);
    }

    @Test
    public void intClassData() throws ReflectiveOperationException {
        ClassByteBuilder builder = new ClassByteBuilder("T1-int");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, int.class).build();
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, 100, true);
        int value = MethodHandles.classData(lookup, "_", int.class);
        assertEquals(value, 100);
        // call through condy
        assertClassData(lookup, 100);
    }

    @Test
    public void floatClassData() throws ReflectiveOperationException {
        ClassByteBuilder builder = new ClassByteBuilder("T1-float");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, float.class).build();
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, 0.1234f, true);
        float value = MethodHandles.classData(lookup, "_", float.class);
        assertEquals(value, 0.1234f);
        // call through condy
        assertClassData(lookup, 0.1234f);
    }

    @Test
    public void classClassData() throws ReflectiveOperationException {
        Class<?> hc = hiddenClass(100).lookupClass();
        ClassByteBuilder builder = new ClassByteBuilder("T2");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, Class.class).build();
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, hc, true);
        Class<?> value = MethodHandles.classData(lookup, "_", Class.class);
        assertEquals(value, hc);
        // call through condy
        assertClassData(lookup, hc);
    }

    @Test
    public void arrayClassData() throws ReflectiveOperationException {
        ClassByteBuilder builder = new ClassByteBuilder("T3");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, String[].class).build();
        String[] colors = new String[] { "red", "yellow", "blue"};
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, colors, true);
        assertClassData(lookup, colors.clone());
        // class data is modifiable and not a constant
        colors[0] = "black";
        // it will get back the modified class data
        String[] value = MethodHandles.classData(lookup, "_", String[].class);
        assertEquals(value, colors);
        // even call through condy as it's not a constant
        assertClassData(lookup, colors);
    }

    @Test
    public void listClassData() throws ReflectiveOperationException {
        ClassByteBuilder builder = new ClassByteBuilder("T4");
        byte[] bytes = builder.classDataAt(ACC_PUBLIC|ACC_STATIC, Integer.class, 2).build();
        List<Integer> cd = List.of(100, 101, 102, 103);
        int expected = 102;  // element at index=2
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, cd, true);
        int value = MethodHandles.classDataAt(lookup, "_", int.class, 2);
        assertEquals(value, expected);
        // call through condy
        assertClassData(lookup, expected);
    }

    @Test
    public void arrayListClassData() throws ReflectiveOperationException {
        ClassByteBuilder builder = new ClassByteBuilder("T4");
        byte[] bytes = builder.classDataAt(ACC_PUBLIC|ACC_STATIC, Integer.class, 1).build();
        ArrayList<Integer> cd = new ArrayList<>();
        Stream.of(100, 101, 102, 103).forEach(cd::add);
        int expected = 101;  // element at index=1
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, cd, true);
        int value = MethodHandles.classDataAt(lookup, "_", int.class, 1);
        assertEquals(value, expected);
        // call through condy
        assertClassData(lookup, expected);
    }

    private static Lookup hiddenClass(int value) {
        ClassByteBuilder builder = new ClassByteBuilder("HC");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, int.class).build();
        try {
            return LOOKUP.defineHiddenClassWithClassData(bytes, value, true);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    private static Lookup hiddenClass(List<?> list) {
        ClassByteBuilder builder = new ClassByteBuilder("HC");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, List.class).build();
        try {
            return LOOKUP.defineHiddenClassWithClassData(bytes, list, true);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void condyInvokedFromVirtualMethod() throws ReflectiveOperationException {
        ClassByteBuilder builder = new ClassByteBuilder("T5");
        // generate classData instance method
        byte[] bytes = builder.classData(ACC_PUBLIC, Class.class).build();
        Lookup hcLookup = hiddenClass(100);
        assertClassData(hcLookup, 100);
        Class<?> hc = hcLookup.lookupClass();
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, hc, true);
        Class<?> value = MethodHandles.classData(lookup, "_", Class.class);
        assertEquals(value, hc);
        // call through condy
        Class<?> c = lookup.lookupClass();
        assertClassData(lookup, c.newInstance(), hc);
    }

    @Test
    public void immutableListClassData() throws ReflectiveOperationException {
        ClassByteBuilder builder = new ClassByteBuilder("T6");
        // generate classDataAt instance method
        byte[] bytes = builder.classDataAt(ACC_PUBLIC, Integer.class, 2).build();
        List<Integer> cd = List.of(100, 101, 102, 103);
        int expected = 102;  // element at index=2
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, cd, true);
        int value = MethodHandles.classDataAt(lookup, "_", int.class, 2);
        assertEquals(value, expected);
        // call through condy
        Class<?> c = lookup.lookupClass();
        assertClassData(lookup, c.newInstance() ,expected);
    }

    /*
     * The return value of MethodHandles::classDataAt is the element
     * contained in the list when the method is called.
     * If MethodHandles::classDataAt is called via condy, the value
     * will be captured as a constant.  If the class data is modified
     * after the element at the given index is computed via condy,
     * subsequent LDC of such ConstantDynamic entry will return the same
     * value. However, direct invocation of MethodHandles::classDataAt
     * will return the modified value.
     */
    @Test
    public void mutableListClassData() throws ReflectiveOperationException {
        ClassByteBuilder bui