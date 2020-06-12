/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8076221 8211883 8163327 8279164
 * @summary Check if weak cipher suites are disabled
 * @modules jdk.crypto.ec
 * @run main/othervm DisabledAlgorithms default
 * @run main/othervm DisabledAlgorithms empty
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class DisabledAlgorithms {

    private static final String pathToStores = "../etc";
    private static final String keyStoreFile = "keystore";
    private static final String trustStoreFile = "truststore";
    private static final String passwd = "passphrase";

    private static final String keyFilename =
            System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + keyStoreFile;

    private static final String trustFilename =
            System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + trustStoreFile;

    // disabled 3DES, DES, RC4, NULL, anon, and ECDH cipher suites
    private static final String[] disabled_ciphersuites
        = new String[] {
        "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
        "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
        "SSL_RSA_WITH_RC4_128_SHA",
        "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
        "TLS_ECDH_RSA_WITH_RC4_128_SHA",
        "SSL_RSA_WITH_RC4_128_MD5",
        "TLS_ECDH_anon_WITH_RC4_128_SHA",
        "SSL_DH_anon_WITH_RC4_128_MD5",
        "SSL_RSA_WITH_NULL_MD5",
        "SSL_RSA_WITH_NULL_SHA",
        "TLS_RSA_WITH_NULL_SHA256",
        "TLS_ECDH_ECDSA_WITH_NULL_SHA",
        "TLS_ECDHE_ECDSA_WITH_NULL_SHA",
        "TLS_ECDH_RSA_WITH_NULL_SHA",
        "TLS_ECDHE_RSA_WITH_NULL_SHA",
        "TLS_ECDH_anon_WITH_NULL_SHA",
        "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
        "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
        "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",
        "SSL_DH_anon_WITH_DES_CBC_SHA",
        "SSL_DH_anon_WITH_RC4_128_MD5",
        "TLS_DH_anon_WITH_AES_128_CBC_SHA",
        "TLS_DH_anon_WITH_AES_128_CBC_SHA256",
        "TLS_DH_anon_WITH_AES_128_GCM_SHA256",
        "TLS_DH_anon_WITH_AES_256_CBC_SHA",
        "TLS_DH_anon_WITH_AES_256_CBC_SHA256",
        "TLS_DH_anon_WITH_AES_256_GCM_SHA384",
        "SSL_RSA_WITH_DES_CBC_SHA",
        "SSL_DHE_RSA_WITH_DES_CBC_SHA",
        "SSL_DHE_DSS_WITH_DES_CBC_SHA",
        "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
        "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
        "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
        "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
        "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
        "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
        "TLS_ECDH_anon_WITH_NULL_SHA",
        "TLS_ECDH_anon_WITH_RC4_128_SHA",
        "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
        "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
        "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
        "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
        "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
        "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA"
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new RuntimeException("No parameters specified");
        }

        System.setProperty("javax.net.ssl.keyStore", keyFilename);
        System.setProperty("javax.net.ssl.keyStorePassword", passwd);
        System.setProperty("javax.net.ssl.trustStore", trustFilename);
        System.setProperty("javax.net.ssl.trustStorePassword", passwd);

        switch (args[0]) {
            case "default":
                // use default jdk.tls.disabledAlgorithms
                System.out.println("jdk.tls.disabledAlgorithms = "
                        + Security.getProperty("jdk.tls.disabledAlgorithms"));

                // check that disabled cipher suites can't be used by default
                checkFailure(disabled_ciphersuites);
                break;
            case "empty":
                // reset jdk.tls.disabledAlgorithms
                Security.setProperty("jdk.tls.disabledAlgorithms", "");
                System.out.println("jdk.tls.disabledAlgorithms = "
                        + Security.getProperty("jdk.tls.disabledAlgorithms"));
                // reset jdk.certpath.disabledAlgorithms. This is necessary
                // to allow the RSA_EXPORT suites to pass which use an RSA 512
                // bit key which violates the default certpath constraints.
                Security.setProperty("jdk.certpath.disabledAlgorithms", "");
                System.out.println("jdk.certpath.disabledAlgorithms = "
                    + Security.getProperty("jdk.certpath.disabledAlgorithms"));

                // check that disabled cipher suites can be used if
                // jdk.{tls,certpath}.disabledAlgorithms is empty
                checkSuccess(disabled_ciphersuites);
                break;
            default:
                throw new RuntimeException("Wrong parameter: " + args[0]);
        }

        System.out.println("Test passed");
    }

    /*
     * Checks if that specified cipher suites cannot be used.
     */
    private static void checkFailure(String[] ciphersuites) throws Exception {
        try (SSLServer server = SSLServer.init(ciphersuites)) {
            startNewThread(server);
            while (!server.isRunning()) {
                sleep();
            }

            int port = server.getPort();
            for (String ciphersuite : ciphersuites) {
                try (SSLClient client = SSLClient.init(port, ciphersuite)) {
                    client.connect();
                    throw new RuntimeException("Expected SSLHandshakeException "
                            + "not thrown");
                } catch (SSLHandshakeException e) {
                    System.out.println("Got expected exception on client side: "
                            + e);
                }
            }

            server.stop();
            while (server.isRunning()) {
                sleep();
            }

            if (!server.sslError()) {
                throw new RuntimeException("Expected SSL exception "
                        + "not thrown on server side");
            }
        }

    }

    /*
     * Checks if specified cipher suites can be used.
     */
    private static void checkSuccess(String[] ciphersuites) throws Exception {
        try (SSLServer server = SSLServer.init(ciphersuites)) {
            startNewThread(server);
            while (!server.isRunning()) {
                sleep();
            }

            int port = server.getPort();
            for (String ciphersuite : ciphersuites) {
                try (SSLClient client = SSLClient.init(port, ciphersuite)) {
                    client.connect();
                    String negotiated = client.getNegotiatedCipherSuite();
                    System.out.println("Negotiated cipher suite: "
                            + negotiated);
                    if (!negotiated.equals(ciphersuite)) {
                        throw new RuntimeException("Unexpected cipher suite: "
                                + negotiated);
                    }
                }
            }

            server.stop();
            while (server.isRunning()) {
                sleep();
            }

            if (server.error()) {
                throw new RuntimeException("Unexpected error on server side");
            }
        }

    }

    private static Thread startNewThread(SSLServer server) {
        Thread serverThread = new Thread(server, "SSL server thread");
        serverThread.setDaemon(true);
        serverThread.start();
        return serverThread;
    }

    private static void sleep() {
        try {
            TimeUnit.MILLISECONDS.sleep(50);
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    static class SSLServer implements Runnable, AutoCloseable {

        private final SSLServerSocket ssocket;
        private volatile boolean stopped = false;
        private volatile boolean running = false;
        private volatile boolean sslError = false;
        private volatile boolean otherError = false;

        private SSLServer(SSLServerSocket ssocket) {
            this.ssocket = ssocket;
        }

        @Override
        public void run() {
            System.out.println("Server: started");
            running = true;
            while (!stopped) {
                try (SSLSocket socket = (SSLSocket) ssocket.accept()) {
                    System.out.println("Server: accepted client connection");
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();
                    int b = in.read();
                    if (b < 0) {
                        thr