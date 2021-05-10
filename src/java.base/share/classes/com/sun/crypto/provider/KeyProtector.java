/*
 * Copyright (c) 1998, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.AlgorithmParameters;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.SecretKey;
import javax.crypto.SealedObject;
import javax.crypto.spec.*;
import javax.security.auth.DestroyFailedException;

import jdk.internal.access.SharedSecrets;
import sun.security.x509.AlgorithmId;
import sun.security.util.ObjectIdentifier;
import sun.security.util.KnownOIDs;
import sun.security.util.SecurityProperties;

/**
 * This class implements a protection mechanism for private keys. In JCE, we
 * use a stronger protection mechanism than in the JDK, because we can use
 * the <code>Cipher</code> class.
 * Private keys are protected using the JCE mechanism, and are recovered using
 * either the JDK or JCE mechanism, depending on how the key has been
 * protected. This allows us to parse Sun's keystore implementation that ships
 * with JDK 1.2.
 *
 * @author Jan Luehe
 *
 *
 * @see JceKeyStore
 */

final class KeyProtector {

    private static final int MAX_ITERATION_COUNT = 5000000;
    private static final int MIN_ITERATION_COUNT = 10000;
    private static final int DEFAULT_ITERATION_COUNT = 200000;
    private static final int SALT_LEN = 20; // the salt length
    private static final int DIGEST_LEN = 20;
    private static final int ITERATION_COUNT;

    // the password used for protecting/recovering keys passed through this
    // key protector
    private char[] password;

    /**
     * {@systemProperty jdk.jceks.iterationCount} property indicating the
     * number of iterations for password-based encryption (PBE) in JCEKS
     * keystores. Values in the range 10000 to 5000000 are considered valid.
     * If the value is out of this range, or is not a number, or is
     * unspecified; a default of 200000 is used.
     */
    static {
        int iterationCount = DEFAULT_ITERATION_COUNT;
        String ic = SecurityProperties.privilegedGetOverridable(
                "jdk.jceks.iterationCount");
        if (ic != null && !ic.isEmpty()) {
            try {
                iterationCount = Integer.parseInt(ic);
                if (iterationCount < MIN_ITERATION_COUNT ||
                        iterationCount > MAX_ITERATION_COUNT) {
                    iterationCount = DEFAULT_ITERATION_COUNT;
                }
            } catch (NumberFormatException e) {}
        }
        ITERATION_COUNT = iterationCount;
    }

    KeyProtector(char[] password) {
        if (password == null) {
           throw new IllegalArgumentException("password can't be null");
        }
        this.password = password;
    }

    /**
     * Protects the given cleartext private key, using the password provided at
     * construction time.
     */
    byte[] protect(PrivateKey key)
        throws Exception
    {
        // create a random salt (8 bytes)
        byte[] salt = new byte[8];
        SunJCE.getRandom().nextBytes(salt);

        // create PBE parameters from salt and iteration count
        PBEParameterSpec pbeSpec = new PBEParameterSpec(salt, ITERATION_COUNT);

        // create PBE key from password
        PBEKeySpec pbeKeySpec = new PBEKeySpec(this.password);
        SecretKey sKey = null;
        PBEWithMD5AndTripleDESCipher cipher;
        try {
            sKey = new PBEKey(pbeKeySpec, "PBEWithMD5AndTripleDES", false);
            // encrypt private key
            cipher = new PBEWithMD5AndTripleDESCipher();
            cipher.engineInit(Cipher.ENCRYPT_MODE, sKey, pbeSpec, null);
        } finally {
            pbeKeySpec.clearPassword();
            if (sKey != null) sKey.destroy();
        }
        byte[] plain = key.getEncoded();
        byte[] encrKey = cipher.engineDoFinal(plain, 0, plain.length);
        Arrays.fill(plain, (byte) 0x00);

        // wrap encrypted private key in EncryptedPrivateKeyInfo
        // (as defined in PKCS#8)
        AlgorithmParameters pbeParams =
            AlgorithmParameters.getInstance("PBE", SunJCE.getInstance());
        pbeParams.init(pbeSpec);

        AlgorithmId encrAlg = new AlgorithmId
            (ObjectIdentifier.of(KnownOIDs.JAVASOFT_JCEKeyProtector),
             pbeParams);
        return new EncryptedPrivateKeyInfo(encrAlg,encrKey).getEncoded();
    }

    /*
     * Recovers the cleartext version of the given key (in protected format),
     * using the password provided at construction time.
     */
    Key recover(EncryptedPrivateKeyInfo encrInfo)
        throws UnrecoverableKeyException, NoSuchAlgorithmException
    {
        byte[] plain = null;
        SecretKey sKey = null;
        try {
            String encrAlg = encrInfo.getAlgorithm().getOID().toString();
            if (!encrAlg.equals(KnownOIDs.JAVASOFT_JCEKeyProtector.value())
                && !encrAlg.equals(KnownOIDs.JAVASOFT_JDKKeyProtector.value())) {
                throw new UnrecoverableKeyException("Unsupported encryption "
                                                    + "algorithm");
            }

            if (encrAlg.equals(KnownOIDs.JAVASOFT_JDKKeyProtector.value())) {
                // JDK 1.2 style recovery
                plain = recover(encrInfo.getEncryptedData());
            } else {
                byte[] encodedParams =
                    encrInfo.getAlgorithm().getEncodedParams();

                if (encodedParams == null) {
                    throw new IOException("Missing PBE parameters");
                }

                // parse the PBE parameters into the corresponding spec
                AlgorithmParameters pbeParams =
                    AlgorithmParameters.getInstance("PBE");
                pbeParams.init(encodedParams);
                PBEParameterSpec pbeSpec =
                        pbeParams.getParameterSpec(PBEParameterSpec.class);
                if (pbeSpec.getIterationCount() > MAX_ITERATION_COUNT) {
                    throw new IOException("PBE iteration count too large");
                }

                // create PBE key from password
                PBEKeySpec pbeKeySpec = new PBEKeySpec(this.password);
                sKey = new PBEKey(pbeKeySpec, "PBEWithMD5AndTripleDES", false);
                pbeKeySpec.clearPassword();

                // decrypt private key
                PBEWithMD5AndTripleDESCipher cipher;
                cipher = new PBEWithMD5AndTripleDESCipher();
                cipher.engineInit(Cipher.DECRYPT_MODE, sKey, pbeSpec, null);
                plain=cipher.engineDoFinal(encrInfo.getEncryptedData(), 0,
                                           encrInfo.getEncryptedData().length);
            }

            // determine the private-key algorithm, and parse private key
            // using the appropriate key factory
            PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(plain);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(plain);
            String oidName = new AlgorithmId
                (privateKeyInfo.getAlgorithm().getOID()).getName();
            try {
                KeyFactory kFac = KeyFactory.getInstance(oidName);
                return kFac.generatePrivate(spec);
            } finally {
                privateKeyInfo.clear();
                SharedSecrets.getJavaSecuritySpecAccess().clearEncodedKeySpec(spec);
            }
        } catch (NoSuchAlgorithmException ex) {
            // Note: this catch needed to be here because of the
            // later catch of GeneralSecurityException
            throw ex;
        } catch (IOException | GeneralSecurityException e) {
            throw new UnrecoverableKeyException(e.getMess