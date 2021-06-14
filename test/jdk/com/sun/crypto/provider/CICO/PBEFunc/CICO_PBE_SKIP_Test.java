/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.security.GeneralSecurityException;
import javax.crypto.CipherInputStream;

/**
 * CICO PBE SKIP functional test.
 *
 * Verifies for the given PBE algorithm if the encrypt/decrypt mechanism is
 * performed correctly for CipherInputStream when skip() method is used.
 *
 * Test scenario:
 * 1. initializes plain text with random generated data with length TEXT_SIZE.
 * 2. for the given PBE algorithm instantiates encrypt and decrypt Ciphers.
 * 3. instantiates CipherInputStream 1 with the encrypt Cipher.
 * 4. instantiates CipherInputStream 2 with the CipherInputStream 1 and decrypt
 *    Cipher.
 * 5. the plain text is divided on TEXT_SIZE/BLOCK blocks. Reading from
 *    CipherInputStream 2 one block at time. The last BLOCK - SAVE bytes are
 *    skipping for each block. Therefor the plain text data go through
 *    CipherInputStream 1 (encrypting