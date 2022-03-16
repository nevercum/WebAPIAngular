/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package util;

public class SerializedTransactionExceptions {

    /**
     * Serialized XAException from JDK 8 with the following values:
     * reason = "This was the error msg"
     * cause = null
     */
    public static byte[] XAE_DATA = {
        (byte) 0xac, (byte) 0xed, (byte) 0x0, (byte) 0x5, (byte) 0x73, (byte) 0x72, (byte) 0x0, (byte) 0x20, (byte) 0x6a, (byte) 0x61, (byte) 0x76, (byte) 0x61, (byte) 0x78, (byte) 0x2e, (byte) 0x74, (byte) 0x72, (byte) 0x61, (byte) 0x6e, (byte) 0x73, (byte) 0x61, (byte) 0x63, (byte) 0x74, (byte) 0x69, (byte) 0x6f, (byte) 0x6e, (byte) 0x2e, (byte) 0x78, (byte) 0x61, (byte) 0x2e, (byte) 0x58, (byte) 0x41, (byte) 0x45,
        (byte) 0x78, (byte) 0x63, (byte) 0x65, (byte) 0x70, (byte) 0x74, (byte) 0x69, (byte) 0x6f, (byte) 0x6e, (byte) 0x8d, (byte) 0x83, (byte) 0x3c, (byte) 0xc2, (byte) 0xda, (byte) 0xf, (byte) 0x2a, (byte) 0x59, (byte) 0x2, (byte) 0x0, (byte) 0x1, (byte) 0x49, (byte) 0x0, (byte) 0x9, (byte) 0x65, (byte) 0x72, (byte) 0x72, (byte) 0x6f, (byte) 0x72, (byte) 0x43, (byte) 0x6f, (byte) 0x64, (byte) 0x65, (byte) 0x78,
        (byte) 0x72, (byte) 0x0, (byte) 0x13, (byte) 0x6a, (byte) 0x61, (byte) 0x76, (byte) 0x61, (byte) 0x2e, (byte) 0x6c, (byte) 0x61, (byte) 0x6e, (byte) 0x67, (byte) 0x2e, (byte) 0x45, (byte) 0x78, (byte) 0x63, (byte) 0x65, (byte) 0x70, (byte) 0x74, (byte) 0x69, (byte) 0x6f, (byte) 0x6e, (byte) 0xd0, (byte) 0xfd, (byte) 0x1f, (byte) 0x3e, (byte) 0x1a, (byte) 0x3b, (byte) 0x1c, (byte) 0xc4, (byte) 0x2, (byte) 0x0,
        (byte) 0x0, (byte) 0x78, (byte) 0x72, (byte) 0x0, (byte) 0x13, (byte) 0x6a, (byte) 0x61, (byte) 0x76, (byte) 0x61, (byte) 0x2e, (byte) 0x6c, (byte) 0x61, (byte) 0x6e, (byte) 0x67, (byte) 0x2e, (byte) 0x54, (byte) 0x68, (byte) 0x72, (byte) 0x6f, (byte) 0x77, (byte) 0x61, (byte) 0x62, (byte) 0x6c, (byte) 0x65, (byte) 0xd5, (byte) 0xc6, (byte) 0x35, (byte) 0x27, (byte) 0x39, (byte) 0x77, (byte) 0xb8, (byt