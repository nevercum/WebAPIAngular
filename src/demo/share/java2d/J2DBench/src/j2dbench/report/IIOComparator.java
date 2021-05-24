/*
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package j2dbench.report;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import j2dbench.report.J2DAnalyzer.ResultHolder;
import j2dbench.report.J2DAnalyzer.ResultSetHolder;

/**
 * This simple utility generates a wiki- or html-formatted table, which
 * compares the performance of various image loading routines (relative
 * to the core Image I/O plugins).
 */
public class IIOComparator {

    private static final DecimalFormat decimalFormat =
        new DecimalFormat("0.0");

    /**
     * List of methods, given in the order we want them to appear in
     * the printed columns.
     */
    private static final String[] methodKeys = new String[] {
        "IIO-Core", "IIO-Ext", "Toolkit", "JPEGCodec", "GdkPixBuf"
    };

    private static final Hashtable allResults = new Hashtable();

    private static boolean wikiStyle;

    private static void printIIOTable(String resultsFile) {
        try {
            J2DAnalyzer.readResults(resultsFile);
        } catch (Exception e) {
            System.err.println("Error reading results file: " +
                               e.getMessage());
            return;
        }

        Vector results = J2DAnalyzer.results;
        int numsets = results.size();

        ResultSetHolder base = (ResultSetHolder)results.elementAt(0);
        Enumeration basekeys = base.getKeyEnumeration();
        String[] keys = toSortedArray(basekeys, false);

        // build results table
        for (int k = 0; k < keys.length; k++) {
            String key = keys[k];
            ResultHolder rh = base.getResultByKey(key);
            double score = rh.getScore();
            Hashtable opts = rh.getOptions();

            String imgsize = (String)opts.get("imageio.opts.size");
            String content = (String)opts.get("imageio.opts.content");
            String testname = "size=" + imgsize + ",content=" + content;

            String format = null;
            String method = null;
            String name = rh.getName();
            if (name.equals("imageio.input.image.imageio.reader.tests.read")) {
                format = (String)opts.get("imageio.input.image.imageio.opts.format");
                String type = format.substring(0, format.indexOf('-'));
                format = format.substring(format.indexOf('-')+1);
                if (format.equals("jpeg")) {
                    format = "jpg";
                }
                method = "IIO-" + (type.equals("core") ? "Core" : "Ext");
            } else if (name.equals("imageio.input.image.toolkit.tests.createImage")) {
                format = (String)opts.get("imageio.input.image.toolkit.opts.format");
                method = "Toolkit";
            } else if (name.equals("imageio.input.image.toolkit.tests.gdkLoadImage")) {
                format = (String)opts.get("imageio.input.image.toolkit.opts.format");
                method = "GdkPixBuf";
            } else if (name.equals("imageio.input.image.jpegcodec.tests.decodeAsBufferedImage")) {
                format = "jpg";
                method = "JPEGCodec";
            } else {
                System.err.println("skipping unrecognized key: " + name);
                continue;
            }

            //System.out.println(format + ": " + method + " = " + score);
            Hashtable fmtResults = (Hashtable)allResults.get(format);
            if (fmtResults == null) {
                fmtResults = new Hashtable();
                allResults.put(format, fmtResults);
            }
            Hashtable testResults = (Hashtable)fmtResults.get(testname);
            if (testResults == null) {
                testResults = new Hashtable();
                fmtResults.put(testname, testResults);
            }
            testResults.put(method, new Double(score));
        }

        if (wikiStyle) {
            printWikiTable();
        } else {
            printHtmlTable();
        }
    }

    private static void printWikiTable() {
        // print a table for each format
        Enumeration allKeys = allResults.keys();
        while (allKeys.hasMoreElements()) {
            String format = (String)allKeys.nextElement();
            System.out.println("---+++ " + format.toUpperCase());

            Hashtable fmtResults = (Hashtable)allResults.get(format);
            Enumeration testKeys = fmtResults.keys();
            String[] tests = toSortedArray(testKeys, true);

            // print the column headers
            Hashtable testResults = (Hashtable)fmtResults.get(tests[0]);
            String[] methods = new String[testResults.keySet().size()];
            for (int k = 0, i = 0; i < methodKeys.length; i++) {
                if (testResults.containsKey(methodKeys[i])) {
                    methods[k++] = methodKeys[i];
                }
            }
            System.out.print("| |");
            for (int i = 0; i < methods.length; i++) {
                System.out.print(" *" + methods[i] + "* |");
                if (i > 0) {
                    System.out.print(" *%* |");
                }
            }
            System.out.println("");

            // print all rows in the table
            for (int i = 0; i < tests.length; i++) {
                String testname = tests[i];
                testResults = (Hashtable)fmtResults.get(testname);
                System.out.print("| " + testname + " |");
                double baseres = 0.0;
                for (int j = 0; j < methods.length; j++) {
                    Double result = (Double)testResults.get(methods[j]);
                    double res = result.doubleValue();

                    System.out.print("   " +
                                     decimalFormat.format(