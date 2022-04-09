/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * @test
 * @summary Validate that opening ZIP files files with invalid UTF-8
 * byte sequences in the name or comment fields fails with ZipException
 * @run testng/othervm InvalidBytesInEntryNameOrComment
 */
public class InvalidBytesInEntryNameOrComment {

    // Offsets for navigating the CEN fields
    private static final int EOC_OFF = 6;   // Offset from EOF to find CEN offset
    private static final int CEN_HDR = 45;  // Size of a CEN header
    private static final int NLEN = 28;     // Name length
    private static final int ELEN = 30;     // Extra length
    private static final int CLEN = 32;     // Comment length

    // Example invalid UTF-8 byte sequence
    private static final byte[] INVALID_UTF8_BYTE_SEQUENCE = {(byte) 0xF0, (byte) 0xA4, (byte) 0xAD};

    // Expected ZipException regex
    private static final String BAD_ENTRY_NAME_OR_COMMENT = "invalid CEN header (bad entry name or comment)";

    // ZIP file with invalid name field
    private Path invalidName;

    // ZIP file with invalid comment field
    private Path invalidComment;

    @BeforeTest
    public void setup() throws IOException {
        // Create a ZIP file with valid name and comment fields
        byte[] templateZip = templateZIP();

        // Create a ZIP with a CEN name field containing an invalid byte sequence
        invalidName = invalidName("invalid-name.zip", templateZip);

        // Create a ZIP with a CEN comment field containing an invalid byte sequence
        invalidComment = invalidComment("invalid-comment.zip", templateZip);
    }

    /**
     * Opening a ZipFile with an invalid UTF-8 byte sequence in
     * the name field of a CEN file header should throw a
     * ZipException with "bad entry name or comment"
     */
    @Test
    public void shouldRejectInvalidName() throws IOException {
        ZipException ex = expectThrows(ZipException.class, () -> {
            new ZipFile(invalidName.toFile());
        });
        assertEquals(ex.getMessage(), BAD_ENTRY_NAME_OR_COMMENT);
    }

    /**
     * Opening a ZipFile with an invalid UTF-8 byte sequence in
     * the comment field of a CEN file header should throw a
     * ZipException with "bad entry name or comment"
     */
    @Test
    public vo