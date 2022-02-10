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
            error("Wrong Control.TTL_DONT_CACHE: %d%n", CONTROL.TTL_DONT_CACHE);
        }
        if (CONTROL.TTL_NO_EXPIRATION_CONTROL != -2) {
            error("Wrong Control.TTL_NO_EXPIRATION_CONTROL: %d%n",
                  CONTROL.TTL_NO_EXPIRATION_CONTROL);
        }
    }

    private static void checkImmutableList(List<String> list) {
        try {
            list.add("hello");
            error("%s is mutable%n", list);
        } catch (UnsupportedOperationException e) {
        }
    }

    private static void testGetFormats() {
        List<String> list = CONTROL.getFormats("foo");
        if (list != CONTROL.FORMAT_DEFAULT) {
            error("getFormats returned " + list);
        }
        try {
            list = CONTROL.getFormats(null);
            error("getFormats doesn't throw NPE.");
        } catch (NullPointerException e) {
        }
    }

    private static void testGetCandidateLocales() {
        Map<Locale, Locale[]> candidateData = new HashMap<Locale, Locale[]>();
        candidateData.put(Locale.of("ja", "JP", "YOK"), new Locale[] {
                              Locale.of("ja", "JP", "YOK"),
                              Locale.of("ja", "JP"),
                              Locale.of("ja"),
                              Locale.ROOT });
        candidateData.put(Locale.of("ja", "JP"), new Locale[] {
                              Locale.of("ja", "JP"),
                              Locale.of("ja"),
                              Locale.ROOT });
        candidateData.put(Locale.of("ja"), new Locale[] {
                              Locale.of("ja"),
                              Locale.ROOT });

        candidateData.put(Locale.of("ja", "", "YOK"), new Locale[] {
                              Locale.of("ja", "", "YOK"),
                              Locale.of("ja"),
                              Locale.ROOT });
        candidateData.put(Locale.of("", "JP", "YOK"), new Locale[] {
                              Locale.of("", "JP", "YOK"),
                              Locale.of("", "JP"),
                              Locale.ROOT });
        candidateData.put(Locale.of("", "", "YOK"), new Locale[] {
                              Locale.of("", "", "YOK"),
                              Locale.ROOT });
        candidateData.put(Locale.of("", "JP"), new Locale[] {
                              Locale.of("", "JP"),
                              Locale.ROOT });
        candidateData.put(Locale.ROOT, new Locale[] {
                              Locale.ROOT });

        // Norwegian Bokmal
        candidateData.put(Locale.forLanguageTag("nb-NO-POSIX"), new Locale[] {
                Locale.forLanguageTag("nb-NO-POSIX"),
                Locale.forLanguageTag("no-NO-POSIX"),
                Locale.forLanguageTag("nb-NO"),
                Locale.forLanguageTag("no-NO"),
                Locale.forLanguageTag("nb"),
                Locale.forLanguageTag("no"),
                Locale.ROOT});
        candidateData.put(Locale.forLanguageTag("no-NO-POSIX"), new Locale[] {
                Locale.forLanguageTag("no-NO-POSIX"),
                Locale.forLanguageTag("nb-NO-POSIX"),
                Locale.forLanguageTag("no-NO"),
                Locale.forLanguageTag("nb-NO"),
                Locale.forLanguageTag("no"),
                Locale.forLanguageTag("nb"),
                Locale.ROOT});


        for (Locale locale : candidateData.keySet()) {
            List<Locale> candidates = CONTROL.getCandidateLocales("any", locale);
            List<Locale> expected = Arrays.asList(candidateData.get(locale));
            if (!candidates.equals(expected)) {
                error("Wrong candidates for %s: got %s, expected %s%n",
                      toString(locale), candidates, expected);
            }
        }
        final int NARGS = 2;
        for (int mask = 0; mask < (1 << NARGS)-1; mask++) {
            Object[] data = getNpeArgs(NARGS, mask);
            try {
                List<Locale> candidates = CONTROL.getCandidateLocales((String) data[0],
                                                                      (Locale) data[1]);
                error("getCandidateLocales(%s, %s) doesn't throw NPE.%n",
                      data[0], toString((Locale)data[1]));
            } catch (NullPointerException e) {
            }
        }
    }

    private static void testGetFallbackLocale() {
        Locale current = Locale.getDefault();
        Locale.setDefault(Locale.ITALY);
        try {
            Locale loc = CONTROL.getFallbackLocale("any", Locale.FRANCE);
            if (loc != Locale.ITALY) {
                error("getFallbackLocale: got %s, expected %s%n",
                      toString(loc), toString(Locale.ITALY));
            }
            loc = CONTROL.getFallbackLocale("any", Locale.ITALY);
            if (loc != null) {
                error("getFallbackLocale: got %s, expected null%n", toString(loc));
            }
        } finally {
            Locale.setDefault(current);
        }

        final int NARGS = 2;
        for (int mask = 0; mask < (1 << NARGS)-1; mask++) {
            Object[] data = getNpeArgs(NARGS, mask);
            try {
                Locale loc = CONTROL.getFallbackLocale((String) data[0], (Locale) data[1]);
                error("getFallbackLocale(%s, %s) doesn't throw NPE.%n", data[0], data[1]);
            } catch (NullPointerException e) {
            }
        }
    }

    private static void testNewBundle() {
        int testNo = 0;
        ResourceBundle rb = null;
        try {
            testNo = 1;
            rb = CONTROL.newBundle("StressOut", Locale.JAPANESE,
                                   PROPERTIES, LOADER, false);
            String s = rb.getString("data");
            if (!s.equals("Japan")) {
                error("newBundle: #%d got %s, expected Japan%n", testNo, s);
            }

            testNo = 2;
            rb = CONTROL.newBundle("TestResourceRB", Locale.ROOT,
                                   CLAZZ, LOADER, false);
            s = rb.getString("type");
            if (!s.equals(CLAZZ)) {
                error("newBundle: #%d got %s, expected %s%n", testNo, s, CLAZZ);
            }
        } catch (Throwable e) {
            error("newBundle: #%d threw %s%n", testNo, e);
            e.printStackTrace();
        }

        // Test exceptions

        try {
            // MalformedDataRB contains an invalid Unicode notation which
            // causes to throw an IllegalArgumentException.
            rb = CONTROL.newBundle("MalformedDataRB", Locale.ENGLISH,
                                   PROPERTIES, LOADER, false);
            error("newBundle: doesn't throw IllegalArgumentException with malformed data.");
        } catch (IllegalArgumentException iae) {
        } catch (Exception e) {
            error("newBundle: threw %s%n", e);
        }

        try {
            rb = CONTROL.newBundle("StressOut", Locale.JAPANESE,
                                   "foo.bar", LOADER, false);
            error("newBundle: doesn't throw IllegalArgumentException with invalid format.");
        } catch (IllegalArgumentException iae) {
        } catch (Exception e) {
            error("newBundle: threw %s%n", e);
        }

        try {
            rb = CONTROL.newBundle("NonResourceBundle", Locale.ROOT,
                                   "java.class", LOADER, false);
            error("newBundle: doesn't throw ClassCastException with a non-ResourceBundle subclass.");
        } catch (ClassCastException cce) {
        } catch (Exception e) {
            error("newBundle: threw %s%n", e);
        }

        // NPE test
        final int NARGS = 4;
        for (int mask = 0; mask < (1 << NARGS)-1; mask++) {
            Object[] data =