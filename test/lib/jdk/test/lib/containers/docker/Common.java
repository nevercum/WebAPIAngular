/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.containers.docker;

/*
 * Methods and definitions common to docker tests container in this directory
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;

import static jdk.test.lib.Asserts.assertNotNull;


public class Common {
    // Create a unique name for docker image.
    public static String imageName() {
        // jtreg guarantees that test.name is unique among all concurrently executing
        // tests. For example, if you have two test roots:
        //
        //     $ find test -type f
        //     test/foo/TEST.ROOT
        //     test/foo/my/TestCase.java
        //     test/bar/TEST.ROOT
        //     test/bar/my/TestCase.java
        //     $ jtreg -concur:2 test/foo test/bar
        //
        // jtreg will first run all the tests under test/foo. When they are all finished, then
        // jtreg will run all the tests under test/bar. So you will never have two concurrent
        // test cases whos