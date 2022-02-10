/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5102289 6278334 8261179
 * @summary Test the default Control implementation. The expiration
 * functionality of newBundle, getTimeToLive, and needsReload is
 * tested by ExpirationTest.sh. The factory methods are tested
 * separately.
 * @build TestResourceRB
 * @build NonResourceBundle
 * @run main DefaultControlTest
 */

import java.util.*;
import static java.util.ResourceBundle.Control.*;

public class DefaultControlTest {
    // The ResourceBundle.Control instance
    static final ResourceBundle.Control CONTROL
        = ResourceBundle.Control.getControl(FORMAT_DEFAULT);

    static final ResourceBundle BUNDLE = new ResourceBundle() {
            public Enumeration<String> getKeys() { return null; }
            protected Object handleGetObject(String key) { return null; }
        };

    static final String CLAZZ = FORMAT_CLASS.get(0);

    static final String PROPERTIES = FORMAT_PROPERTIES.get(0);

    static final ClassLoader LOADER = DefaultControlTest.class.getClassLoader();

    // Full arguments for NPE testing
    static final Object[] FULLARGS = { "any",
                                       Locale.US,
                                       FORMAT_PROPERTIES.get(0),
                                       LOADER,
                                       BUNDLE };

    static int errors;

    public static void main(String[] args) {
        checkConstants();

        // Test getFormats(String)
        testGetFormats();

        // Test getCandidateLocales(String, Locale)
        testGetCandidateLocales();

        // Test getFallbackLocale(String, Locale)
        testGetFallbackLocale();

        // Test newBundle(String, Locale, String, ClassLoader, boolean)
        testNewBundle();

        // Test toBundleName(String, Locale)
        testToBundleName();

        // Test getTimeToLive(String, Locale)
        testGetTimeToLive();

        // Test needsReload(String, Locale, String, ClassLoader,
        //                  ResourceBundle, long)
        testNeedsReload();

        // Test toResourceName(String, String)
        testToResourceName();

        if (errors > 0) {
            throw new RuntimeException("FAILED: " + errors + " error(s)");
        }
    }

    private static void checkConstants() {
        // Check FORMAT_*
        if (!CONTROL.FORMAT_DEFAULT.equals(Arrays.asList("java.class",
                                                        "java.properties"))) {
            error("Wrong Control.FORMAT_DEFAULT");
        }
        checkImmutableList(CONTROL.FORMAT_DEFAULT);
        if (!CONTROL.FORMAT_CLASS.equals(Arrays.asList("java.class"))) {
            error("Wrong Control.FORMAT_CLASS");
        }
        checkImmutableList(CONTROL.FORMAT_CLASS);
        if (!CONTROL.FORMAT_PROPERTIES.equals(Arrays.asList("java.properties"))) {
            error("Wrong Control.FORMAT_PROPERTIES");
        }
        checkImmutableList(CONTROL.FORMAT_PROPERTIES);

        // Check TTL_*
        if (CONTROL.TTL_DONT_CACHE != -1) {
            error("Wrong Control