/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.List;
import java.util.ArrayList;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * @test
 * @bug 8066619
 * @run testng ValueUtf8Coding
 * @summary Tests encoding and decoding manifest header values to and from
 * UTF-8 with the complete Unicode character set.
 */ /*
 * see also "../tools/launcher/UnicodeTest.java" for manifest attributes
 * parsed during launch
 */
public class ValueUtf8Coding {

    /**
     * Maximum number of bytes of UTF-8 encoded characters in one header value.
     * <p>
     * There are too many different Unicode code points (more than one million)
     * to fit all into one manifest value. The specifications state:
     * <q>Implementations should support 65535-byte (not character) header
     * values, and 65535 headers per file. They might run out of memory,
     * but there should not be hard-coded limits below these values.</q>
     *
     * @see <a
     * href="{@docRoot}/../specs/jar/jar.html#Notes_on_Manifest_and_Signature_Files">
     * Notes on Manifest and Signature Files</a>
     */
    static final 