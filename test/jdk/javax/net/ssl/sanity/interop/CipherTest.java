/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import java.security.*;
import java.security.cert.*;

import javax.net.ssl.*;

/**
 * Test that all ciphersuites work in all versions and all client
 * authentication types. The way this is setup the server is stateless and
 * all checking is done on the client side.
 *
 * The test is multithreaded to speed it up, especially on multiprocessor
 * machines. To simplify debugging, run with -DnumThreads=1.
 *
 * @author Andreas Sterbenz
 */
public class CipherTest {

    // use any available port for the server socket
    static int serverPort = 0;

    final int THREADS;

    // assume that if we do not read anything for 20 seconds, something
    // has gone wrong
    final static int TIMEOUT = 20 * 1000;

    static KeyStore trustStore, keyStore;
    static X509ExtendedKeyManager keyManager;
    static X509TrustManager trustManager;
    static SecureRandom secureRandom;

    private static PeerFactory peerFactory;

    static abstract class Server implements Runnable {

        final CipherTest cipherTest;

        Server(CipherTest cipherTest) throws Exception {
            this.cipherTest = cipherTest;
        }

        public abstract void run();

        void handleRequest(InputStream in, OutputStream out) throws IOException {
            boolean newline = false;
            StringBuilder sb = new StringBuilder();
            while (true) {
                int ch = in.read();
                if (ch < 0) {
                    throw new EOFException();
                }
                sb.append((char)ch);
                if (ch == '\r') {
                    // empty
                } else if (ch == '\n') {
                    if (newline) {
                        // 2nd newline in a row, end of request
                        break;
                    }
                    newline = true;
                } else {
                    newline = false;
                }
            }
            String request = sb.toString();
            if (request.startsWith("GET / HTTP/1.") == false) {
                throw new IOException("Invalid request: " + request);
            }
            out.write("HTTP/1.0 200 OK\r\n\r\n".getBytes());
        }

    }

    public static class TestParameters {

        CipherSuite cipherSuite;
        Protocol protocol;
        String clientAuth;

        TestParameters(CipherSuite cipherSuite, Protocol protocol,
                String clientAuth) {
            this.cipherSuite = cipherSuite;
            this.protocol = protocol;
            this.clientAuth = clientAuth;
        }

        boolean isEnabled() {
            return cipherSuite.supportedByProtocol(protocol);
        }

        public String toString() {
            String s = cipherSuite + " in " + protocol + " mode";
            if (clientAuth != null) {
                s += " with " + clientAuth + " client authentication";
            }
            return s;
        }
    }

    private List<TestParameters> tests;
    private Iterator<TestParameters> testIterator;
    private SSLSocketFactory factory;
    private boolean failed;

    private CipherTest(PeerFactory peerFactory) throws IOException {
        THREADS = Integer.parseInt(System.getProperty("numThreads", "4"));
        factory = (SSLSocketFactory)SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket)factory.createSocket();
        String[] cipherSuites = socket.getSupportedCipherSuites();
        String[] protocols = socket.getSupportedProtocols();
        String[] clientAuths = {null, "RSA", "DSA"};
        tests = new ArrayList<TestParameters>(
            cipherSuites.length * protocols.length * clientAuths.length);
        for (int j = 0; j < protocols.length; j++) {
            String protocol = protocols[j];
            if (protocol.equals(Protocol.SSLV2HELLO.name)) {
                System.out.println("Skipping SSLv2Hello protocol");
                continue;
            }

            for (int i = 0; i < cipherSuites.length; i++) {
                String cipherSuite = cipherSuites[i];

                // skip kerberos cipher suites and TLS_EMPTY_RENEGOTIATION_INFO_SCSV
                if (cipherSuite.startsWith("TLS_KRB5") || cipherSuite.equals(
                        CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV.name())) {
                    System.out.println("Skipping unsupported test for " +
                                        cipherSuite + " of " + protocol);
                    continue;
                }

                if (!peerFactory.isSupported(cipherSuite, protocol)) {
                    continue;
                }

                for (int k = 0; k < clientAuths.length; k++) {
                    String clientAuth = clientAuths[k];
                    // no client with anonymous cipher suites;
                    // TLS 1.3 doesn't support DSA
                    if ((clientAuth != null && cipherSuite.contains("DH_anon"))
                            || ("DSA".equals(clientAuth) && "TLSv1.3".equals(protocol))) {
                        continue;
                    }
                    tests.add(new TestParameters(
                            CipherSuite.cipherSuite(cipherSuite),
                            Protocol.protocol(protocol),
                            clientAuth));
                }
            }
        }
        testIterator = tests.iterator();
    }

    synchronized void setFailed() {
        failed = true;
    }

    public void run() throws Exception {
        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            try {
                threads[i] = new Thread(peerFactory.newClient(this),
                    "Client " + i);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            threads[i].start();
        }
        try {
            for (int i = 0; i < THREADS; i++) {
                threads[i].join();
            }
        } catch (InterruptedException e) {
            setFailed();
            e.printStack