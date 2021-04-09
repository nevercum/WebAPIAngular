/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.stream.IntStream;

/**
 * @test
 * @bug 8080462 8226651 8242332
 * @summary Generate a RSASSA-PSS signature and verify it using PKCS11 provider
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main SignatureTestPSS
 */
public class SignatureTestPSS extends PKCS11Test {

    // PKCS11 does not support RSASSA-PSS keys yet
    private static final String KEYALG = "RSA";
    private static final String SIGALG = "RSASSA-PSS";

    private static final int[] KEYSIZES = { 2048, 3072 };
    private static final String[] DIGESTS = {
            "SHA-224", "SHA-256", "SHA-384" , "SHA-512",
            "SHA3-224", "SHA3-256", "SHA3-384" , "SHA3-512",
    };
    private Provider prov;

    /**
     * How much times signature updated.
     */
    private static final int UPDATE_TIMES_FIFTY = 50;

    /**
     * How much times signature initial updated.
     */
    private static final int UPDATE_TIMES_HUNDRED = 100;

    public static void main(String[] args) throws Exception {
        main(new SignatureTestPSS(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        Signature sig;
        try {
            sig = Signature.getInstance(SIGALG, p);
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Skip testing RSASSA-PSS" +
                " due to no support");
            return;
        }
        this.prov = p;
        for (int i : KEYSIZES) {
            runTest(i);
        }
    }

    private void runTest(int keySize) throws Exception {
        byte[] data = new byte[100];
        IntStream.range(0, data.length).forEach(j -> {
            data[j] = (byte) j;
        });
        System.out.println("[KEYSIZE = " + keySize + "]");

        // create a key pair
        KeyPair kpair = generateKeys(KEYALG, keySize);
        test(DIGESTS, kpair.getPrivate(), kpair.getPublic(), data);
    }

    private void test(String[] digestAlgs, PrivateKey privKey,
            PublicKey pubKey, byte[] data) throws RuntimeException {
        // For signature algorithm, create and verify a signature
        for (String hash : digestAlgs) {
            for (String mgfHash : digestAlgs) {
                try {
                    checkSignature(data, pubKey, privKey, hash, mgfHash);
                } catch (NoSuchAlgorithmException | InvalidKeyException |
                         SignatureException | NoSuchProviderException ex) {
                    throw new RuntimeException(ex);
                } catch (InvalidAlgorithmParameterException ex2) {
                    System.out.println("Skip test due to " + ex2);
                }
            }
        