/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.crypto.provider;

import java.security.InvalidKeyException;
import java.security.spec.KeySpec;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactorySpi;
import javax.crypto.spec.PBEKeySpec;
import java.util.HashSet;
import java.util.Locale;

/**
 * This class implements a key factory for PBE keys according to PKCS#5,
 * meaning that the password must consist of printable ASCII characters
 * (values 32 to 126 decimal inclusive) and only the low order 8 bits
 * of each password character are used.
 *
 * @author Jan Luehe
 *
 */
abstract class PBEKeyFactory extends SecretKeyFactorySpi {

    private String type;
    private static HashSet<String> validTypes;

    /**
     * Simple constructor
     */
    private PBEKeyFactory(String keytype) {
        type = keytype;
    }

    static {
        validTypes = HashSet.newHashSet(17);
        validTypes.add("PBEWithMD5AndDES".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithSHA1AndDESede".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithSHA1AndRC2_40".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithSHA1AndRC2_128".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithSHA1AndRC4_40".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithSHA1AndRC4_128".toUpperCase(Locale.ENGLISH));
        // Proprietary algorithm.
        validTypes.add("PBEWithMD5AndTripleDES".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA1AndAES_128".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA224AndAES_128".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA256AndAES_128".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA384AndAES_128".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA512AndAES_128".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA512/224AndAES_128".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA512/256AndAES_128".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA1AndAES_256".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA224AndAES_256".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA256AndAES_256".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA384AndAES_256".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA512AndAES_256".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA512/224AndAES_256".toUpperCase(Locale.ENGLISH));
        validTypes.add("PBEWithHmacSHA512/256AndAES_256".toUpperCase(Locale.ENGLISH));
    }

    public static final class PBEWithMD5AndDES extends PBEKeyFactory {
        public PBEWithMD5AndDES() {
            super("PBEWithMD5AndDES");
        }
    }

    public static final class PBEWithSHA1AndDESede extends PBEKeyFactory {
        public PBEWithSHA1AndDESede() {
            super("PBEWithSHA1AndDESede");
        }
    }

    public static final class PBEWithSHA1AndRC2_40 extends PBEKeyFactory {
        public PBEWithSHA1AndRC2_40() {
            super("PBEWithSHA1AndRC2_40");
        }
    }

    public static final class PBEWithSHA1AndRC2_128 extends PBEKeyFactory {
        public PBEWithSHA1AndRC2_128() {
            super("PBEWithSHA1AndRC2_128");
        }
    }

    public static final class PBEWithSHA1AndRC4_40 extends PBEKeyFactory {
        public PBEWithSHA1AndRC4_40() {
            super("PBEWithSHA1AndRC4_40");
        }
    }

    public static final class PBEWithSHA1AndRC4_128 extends PBEKeyFactory {
        public PBEWithSHA1AndRC4_128() {
            super("PBEWithSHA1AndRC4_128");
        }
    }

    /*
     * Private proprietary algorithm for supporting JCEKS.
     */
    public static final class PBEWithMD5AndTripleDES extends PBEKeyFactory {
        public PBEWithMD5AndTripleDES() {
            super("PBEWithMD5AndTripleDES");
        }
    }

    public static final class PBEWithHmacSHA1AndAES_128 extends PBEKeyFactory {
        public PBEWithHmacSHA1AndAES_128() {
            super("PBEWi