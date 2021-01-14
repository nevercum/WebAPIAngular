/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.mscapi;

import java.nio.ByteBuffer;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.math.BigInteger;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Locale;

import sun.security.rsa.RSAKeyFactory;
import sun.security.util.ECUtil;
import sun.security.util.KeyUtil;

/**
 * Signature implementation.
 *
 * Objects should be instantiated by calling Signature.getInstance() using the
 * following algorithm names:
 *
 *  . "NONEwithRSA"
 *  . "SHA1withRSA"
 *  . "SHA256withRSA"
 *  . "SHA384withRSA"
 *  . "SHA512withRSA"
 *  . "MD5withRSA"
 *  . "MD2withRSA"
 *  . "RSASSA-PSS"
 *  . "SHA1withECDSA"
 *  . "SHA224withECDSA"
 *  . "SHA256withECDSA"
 *  . "SHA384withECDSA"
 *  . "SHA512withECDSA"
 *
 * NOTE: RSA keys must be at least 512 bits long.
 *
 * NOTE: NONEwithRSA must be supplied with a pre-computed message digest.
 *       Only the following digest algorithms are supported: MD5, SHA-1,
 *       SHA-256, SHA-384, SHA-512 and a special-purpose digest
 *       algorithm which is a concatenation of SHA-1 and MD5 digests.
 *
 * @since   1.6
 * @author  Stanley Man-Kit Ho
 */
abstract class CSignature extends SignatureSpi {
    // private key algorithm name
    protected String keyAlgorithm;

    // message digest implementation we use
    protected MessageDigest messageDigest;

    // message digest name
    protected String messageDigestAlgorithm;

    // flag indicating whether the digest has been reset
    protected boolean needsReset;

    // the signing key
    protected CPrivateKey privateKey = null;

    // the verification key
    protected CPublicKey publicKey = null;

    /**
     * Constructs a new CSignature. Used by subclasses.
     */
    CSignature(String keyName, String digestName) {

        this.keyAlgorithm = keyName;
        if (digestName != null) {
            try {
                messageDigest = MessageDigest.getInstance(digestName);
                // Get the digest's canonical name
                messageDigestAlgorithm = messageDigest.getAlgorithm();
            } catch (NoSuchAlgorithmException e) {
                throw new ProviderException(e);
            }
        } else {
            messageDigest = null;
            messageDigestAlgorithm = null;
        }
        needsReset = false;
    }

    static class RSA extends CSignature {

        public RSA(String digestAlgorithm) {
            super("RSA", digestAlgorithm);
        }

        // initialize for signing. See JCA doc
        @Override
        protected void engineInitSign(PrivateKey key) throws InvalidKeyException {
            if (key == null) {
                throw new InvalidKeyException("Key cannot be null");
            }
            if ((key instanceof CPrivateKey) == false
                    || !key.getAlgorithm().equalsIgnoreCase("RSA")) {
                throw new InvalidKeyException("Key type not supported: "
                        + key.getClass() + " " + key.getAlgorithm());
            }
            privateKey = (CPrivateKey) key;

            // Check against the local and global values to make sure
            // the sizes are ok.  Round up to nearest byte.
            RSAKeyFactory.checkKeyLengths(((privateKey.length() + 7) & ~7),
                    null, CKeyPairGenerator.RSA.KEY_SIZE_MIN,
                    CKeyPairGenerator.RSA.KEY_SIZE_MAX);

            this.publicKey = null;
            resetDigest();
        }

        // initialize for signing. See JCA doc
        @Override
        protected void engineInitVerify(PublicKey key) throws InvalidKeyException {
            if (key == null) {
                throw new InvalidKeyException("Key cannot be null");
            }
            // This signature accepts only RSAPublicKey
            if ((key instanceof RSAPublicKey) == false) {
                throw new InvalidKeyException("Key type not supported: "
                        + key.getClas