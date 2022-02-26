/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8193682 8278794 8284771
 * @summary Test Infinite loop while writing on closed Deflater and Inflater.
 * @run testng CloseInflaterDeflaterTest
 */
import java.io.*;
import java.util.Random;
import java.util.jar.JarOutputStream;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterOutputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertThrows;


public class CloseInflaterDeflaterTest {

    // Number of bytes to write/read from Deflater/Inflater
    private static final int INPUT_LENGTH= 512;
    // OutputStream that will throw an exception during a write operation
    private static OutputStream outStream = new OutputStream() {
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            throw new IOException();
        }
        @Override
        public void write(byte[] b) throw