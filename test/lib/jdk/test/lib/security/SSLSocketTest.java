/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.KeyFactory;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Template to help speed up your client/server tests.
 *
 * Two examples that use this template:
 *    test/sun/security/ssl/ServerHandshaker/AnonCipherWithWantClientAuth.java
 *    test/sun/net/www/protocol/https/HttpsClient/ServerIdentityTest.java
 */
public abstract class SSLSocketTest {

    /*
     * Run the test case.
     */
    public void run() throws Exception {
        bootup();
    }

    /*
     * Define the server side application of the test for the specified socket.
     */
    protected abstract void runServerApplication(SSLSocket socket) throws Exception;

    /*
     * Define the client side application of the test for the specified socket.
     * This method is used if the returned value of
     * isCustomizedClientConnection() is false.
     *
     * @param socket may be null is no client socket is generated.
     *
     * @see #isCustomizedClientConnection()
     */
    protected abstract void runClientApplication(SSLSocket socket) throws Exception;

    /*
     * Define the client side application of the test for the specified
     * server port.  This method is used if the returned value of
     * isCustomizedClientConnection() is true.
     *
     * Note that the client need to connect to the server port by itself
     * for the actual message exchange.
     *
     * @see #isCustomizedClientConnection()
     */
    protected void runClientApplication(int serverPort) throws Exception {
        // blank
    }

    /*
     * Create an instance of SSLContext for client use.
     */
    protected SSLContext createClientSSLContext() throws Exception {
        return createSSLContext(trustedCertStrs,
                endEntityCertStrs, endEntityPrivateKeys,
                endEntityPrivateKeyAlgs,
                endEntityPrivateKeyNames,
                getClientContextParameters());
    }

    /*
     * Create an instance of SSLContext for server use.
     */
    protected SSLContext createServerSSLContext() throws Exception {
        return createSSLContext(trustedCertStrs,
                endEntityCertStrs, endEntityPrivateKeys,
                endEntityPrivateKeyAlgs,
                endEntityPrivateKeyNames,
                getServerContextParameters());
    }

    /*
     * The parameters used to configure SSLContext.
     */
    protected static final class ContextParameters {
        final String contextProtocol;
        final String tmAlgorithm;
        final String kmAlgorithm;

        ContextParameters(String contextProtocol,
                String tmAlgorithm, String kmAlgorithm) {

            this.contextProtocol = contextProtocol;
            this.tmAlgorithm = tmAlgorithm;
            this.kmAlgorithm = kmAlgorithm;
        }
    }

    /*
     * Get the client side parameters of SSLContext.
     */
    protected ContextParameters getClientContextParameters() {
        return new ContextParameters("TLS", "PKIX", "NewSunX509");
    }

    /*
     * Get the server side parameters of SSLContext.
     */
    protected ContextParameters getServerContextParameters() {
        return new ContextParameters("TLS", "PKIX", "NewSunX509");
    }

    /*
     * Does the client side use customized connection other than
     * explicit Socket.connect(), for example, URL.openConnection()?
     */
    protected boolean isCustomizedClientConnection() {
        return false;
    }

    /*
     * Configure the server side socket.
     */
    protected void configureServerSocket(SSLServerSocket socket) {

    }

    /*
     * =============================================
     * Define the client and server side operations.
     *
     * If the client or server is doing some kind of object creation
     * that the other side depends on, and that thread prematurely
     * exits, you may experience a hang.  The test harness will
     * terminate all hung threads after its timeout has expired,
     * currently 3 minutes by default, but you might try to be
     * smart about it....
     */

    /*
     * Is the server ready to serve?
     */
    private final CountDownLatch serverCondition = new CountDownLatch(1);

    /*
     * Is the client ready to handshake?
     */
    private final CountDownLatch clientCondition = new CountDownLatch(1);

    /*
     * What's the server port?  Use any free port by default
     */
    private volatile int serverPort = 0;

    /*
     * Define the server side of the test.
     */
    private void doServerSide() throws Exception {
        // kick start the server side service
        SSLContext context = createServerSSLContext();
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();
        SSLServerSocket sslServerSocket =
                (SSLServerSocket)sslssf.createServerSocket(serverPort);
        configureServerSocket(sslServerSocket);
        serverPort = sslServerSocket.getLocalPort();

        // Signal the client, the server is ready to accept connection.
        serverCondition.countDown();

        // Try to accept a connection in 30 seconds.
        SSLSocket sslSocket;
        try {
            sslServerSocket.setSoTimeout(30000);
            sslSocket = (SSLSocket)sslServerSocket.accept();
        } catch (SocketTimeoutException ste) {
            // Ignore the test case if no connection within 30 seconds.
            System.out.println(
                "No incoming client connection in 30 seconds. " +
                "Ignore in server side.");
            return;
        } finally {
            sslServerSocket.close();
        }

        // handle the connection
        try {
            // Is it the expected client connection?
            //
            // Naughty test cases or third party routines may try to
            // connection to this server port unintentionally.  In
            // order to mitigate the impact of unexpected client
            // connections and avoid intermittent failure, it should
            // be checked that the accepted connection is really linked
            // to the expected client.
            boolean clientIsReady =
                    clientCondition.await(30L, TimeUnit.SECONDS);

            if (clientIsReady) {
                // Run the application in server side.
                runServerApplication(sslSocket);
            } else {    // Otherwise, ignore
                // We don't actually care about plain socket connections
                // for TLS communication testing generally.  Just ignore
                // the test if the accepted connection is not linked to
                // the expected client or the client connection timeout
                // in 30 seconds.
                System.out.println(
                        "The client is not the expected one or timeout. " +
                        "Ignore in server side.");
            }
        } finally {
            sslSocket.close();
        }
    }

    /*
     * Define the client side of the test.
     */
    private void doClientSide() throws Exception {

        // Wait for server to get started.
        //
        // The server side takes care of the issue if the server cannot
        // get started in 90 seconds.  The client side would just ignore
        // the test case if the serer is not ready.
        boolean serverIsReady =
                serverCondition.await(90L, TimeUnit.SECONDS);
        if (!serverIsReady) {
            System.out.println(
                    "The server is not ready yet in 90 seconds. " +
                    "Ignore in client side.");
            return;
        }

        if (isCustomizedClientConnection()) {
            // Signal the server, the client is ready to communicate.
            clientCondition.countDown();

            // Run the application in client side.
            runClientApplication(serverPort);

            return;
        }

        SSLContext context = createClientSSLContext();
        SSLSocketFactory sslsf = context.getSocketFactory();

        try (SSLSocket sslSocket = (SSLSocket)sslsf.createSocket()) {
            try {
                sslSocket.connect(
                        new InetSocketAddress("localhost", serverPort), 15000);
            } catch (IOException ioe) {
                // The server side may be impacted by naughty test cases or
                // third party routines, and cannot accept connections.
                //
                // Just ignore the test if the connection cannot be
                // established.
                System.out.println(
                        "Cannot make a connection in 15 seconds. " +
                        "Ignore in client side.");
                return;
            }

            // OK, here the client and server get connected.

            // Signal the server, the client is ready to communicate.
            clientCondition.countDown();

            // There is still a chance in theory that the server thread may
            // wait client-ready timeout and then quit.  The chance should
            // be really rare so we don't consider it until it becomes a
            // real problem.

            // Run the application in client side.
            runClientApplication(sslSocket);
        }
    }

    /*
     * =============================================
     * Stuffs to customize the SSLContext instances.
     */

    /*
     * =======================================
     * Certificates and keys used in the test.
     */
    // Trusted certificates.
    private final static String[] trustedCertStrs = {
        // SHA256withECDSA, curve prime256v1
        // Validity
        //     Not Before: May 22 07:18:16 2018 GMT
        //     Not After : May 17 07:18:16 2038 GMT
        // Subject Key Identifier:
        //     60:CF:BD:73:FF:FA:1A:30:D2:A4:EC:D3:49:71:46:EF:1A:35:A0:86
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIBvjCCAWOgAwIBAgIJAIvFG6GbTroCMAoGCCqGSM49BAMCMDsxCzAJBgNVBAYT\n" +
        "AlVTMQ0wCwYDVQQKDARKYXZhMR0wGwYDVQQLDBRTdW5KU1NFIFRlc3QgU2VyaXZj\n" +
        "ZTAeFw0xODA1MjIwNzE4MTZaFw0zODA1MTcwNzE4MTZaMDsxCzAJBgNVBAYTAlVT\n" +
        "MQ0wCwYDVQQKDARKYXZhMR0wGwYDVQQLDBRTdW5KU1NFIFRlc3QgU2VyaXZjZTBZ\n" +
        "MBMGByqGSM49AgEGCCqGSM49AwEHA0IABBz1WeVb6gM2mh85z3QlvaB/l11b5h0v\n" +
        "LIzmkC3DKlVukZT+ltH2Eq1oEkpXuf7QmbM0ibrUgtjsWH3mULfmcWmjUDBOMB0G\n" +
        "A1UdDgQWBBRgz71z//oaMNKk7NNJcUbvGjWghjAfBgNVHSMEGDAWgBRgz71z//oa\n" +
        "MNKk7NNJcUbvGjWghjAMBgNVHRMEBTADAQH/MAoGCCqGSM49BAMCA0kAMEYCIQCG\n" +
        "6wluh1r2/T6L31mZXRKf9JxeSf9pIzoLj+8xQeUChQIhAJ09wAi1kV8yePLh2FD9\n" +
        "2YEHlSQUAbwwqCDEVB5KxaqP\n" +
        "-----END CERTIFICATE-----",
        // -----BEGIN PRIVATE KEY-----
        // MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg/HcHdoLJCdq3haVd
        // XZTSKP00YzM3xX97l98vGL/RI1KhRANCAAQc9VnlW+oDNpofOc90Jb2gf5ddW+Yd
        // LyyM5pAtwypVbpGU/pbR9hKtaBJKV7n+0JmzNIm61ILY7Fh95lC35nFp
        // -----END PRIVATE KEY-----

        // SHA256withRSA, 2048 bits
        // Validity
        //     Not Before: May 22 07:18:16 2018 GMT
        //     Not After : May 17 07:18:16 2038 GMT
        // Subject Key Identifier:
        //     0D:DD:93:C9:FE:4B:BD:35:B7:E8:99:78:90:FB:DB:5A:3D:DB:15:4C
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDSTCCAjGgAwIBAgIJAI4ZF3iy8zG+MA0GCSqGSIb3DQEBCwUAMDsxCzAJBgNV\n" +
        "BAYTAlVTMQ0wCwYDVQQKDARKYXZhMR0wGwYDVQQLDBRTdW5KU1NFIFRlc3QgU2Vy\n" +
        "aXZjZTAeFw0xODA1MjIwNzE4MTZaFw0zODA1MTcwNzE4MTZaMDsxCzAJBgNVBAYT\n" +
        "AlVTMQ0wCwYDVQQKDARKYXZhMR0wGwYDVQQLDBRTdW5KU1NFIFRlc3QgU2VyaXZj\n" +
        "ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALpMcY7aWieXDEM1/YJf\n" +
        "JW27b4nRIFZyEYhEloyGsKTuQiiQjc8cqRZFNXe2vwziDB4IyTEl0Hjl5QF6ZaQE\n" +
        "huPzzwvQm1pv64KrRXrmj3FisQK8B5OWLty9xp6xDqsaMRoyObLK+oIb20T5fSlE\n" +
        "evmo1vYjnh8CX0Yzx5Gr5ye6YSEHQvYOWEws8ad17OlyToR2KMeC8w4qo6rs59pW\n" +
        "g7Mxn9vo22ImDzrtAbTbXbCias3xlE0Bp0h5luyf+5U4UgksoL9B9r2oP4GrLNEV\n" +
        "oJk57t8lwaR0upiv3CnS8LcJELpegZub5ggqLY8ZPYFQPjlK6IzLOm6rXPgZiZ3m\n" +
        "RL0CAwEAAaNQME4wHQYDVR0OBBYEFA3dk8n+S701t+iZeJD721o92xVMMB8GA1Ud\n" +
        "IwQYMBaAFA3dk8n+S701t+iZeJD721o92xVMMAwGA1UdEwQFMAMBAf8wDQYJKoZI\n" +
        "hvcNAQELBQADggEBAJTRC3rKUUhVH07/1+stUungSYgpM08dY4utJq0BDk36BbmO\n" +
        "0AnLDMbkwFdHEoqF6hQIfpm7SQTmXk0Fss6Eejm8ynYr6+EXiRAsaXOGOBCzF918\n" +
        "/RuKOzqABfgSU4UBKECLM5bMfQTL60qx+HdbdVIpnikHZOFfmjCDVxoHsGyXc1LW\n" +
        "Jhkht8IGOgc4PMGvyzTtRFjz01kvrVQZ75aN2E0GQv6dCxaEY0i3ypSzjUWAKqDh\n" +
        "3e2OLwUSvumcdaxyCdZAOUsN6pDBQ+8VRG7KxnlRlY1SMEk46QgQYLbPDe/+W/yH\n" +
        "ca4PejicPeh+9xRAwoTpiE2gulfT7Lm+fVM7Ruc=\n" +
        "-----END CERTIFICATE-----",
        // -----BEGIN PRIVATE KEY-----
        // MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQC6THGO2lonlwxD
        // Nf2CXyVtu2+J0SBWchGIRJaMhrCk7kIokI3PHKkWRTV3tr8M4gweCMkxJdB45eUB
        // emWkBIbj888L0Jtab+uCq0V65o9xYrECvAeTli7cvcaesQ6rGjEaMjmyyvqCG9tE
        // +X0pRHr5qNb2I54fAl9GM8eRq+cnumEhB0L2DlhMLPGndezpck6EdijHgvMOKqOq
        // 7OfaVoOzMZ/b6NtiJg867QG0212womrN8ZRNAadIeZbsn/uVOFIJLKC/Qfa9qD+B
        // qyzRFaCZOe7fJcGkdLqYr9wp0vC3CRC6XoGbm+YIKi2PGT2BUD45SuiMyzpuq1z4
        // GYmd5kS9AgMBAAECggEAFHSoU2MuWwJ+2jJnb5U66t2V1bAcuOE1g5zkWvG/G5z9
        // rq6Qo5kmB8f5ovdx6tw3MGUOklLwnRXBG3RxDJ1iokz3AvkY1clMNsDPlDsUrQKF
        // JSO4QUBQTPSZhnsyfR8XHSU+qJ8Y+ohMfzpVv95BEoCzebtXdVgxVegBlcEmVHo2
        // kMmkRN+bYNsr8eb2r+b0EpyumS39ZgKYh09+cFb78y3T6IFMGcVJTP6nlGBFkmA/
        // 25pYeCF2tSki08qtMJZQAvKfw0Kviibk7ZxRbJqmc7B1yfnOEHP6ftjuvKl2+RP/
        // +5P5f8CfIP6gtA0LwSzAqQX/hfIKrGV5j0pCqrD0kQKBgQDeNR6Xi4sXVq79lihO
        // a1bSeV7r8yoQrS8x951uO+ox+UIZ1MsAULadl7zB/P0er92p198I9M/0Jth3KBuS
        // zj45mucvpiiGvmQlMKMEfNq4nN7WHOu55kufPswQB2mR4J3xmwI+4fM/nl1zc82h
        // De8JSazRldJXNhfx0RGFPmgzbwKBgQDWoVXrXLbCAn41oVnWB8vwY9wjt92ztDqJ
        // HMFA/SUohjePep9UDq6ooHyAf/Lz6oE5NgeVpPfTDkgvrCFVKnaWdwALbYoKXT2W
        // 9FlyJox6eQzrtHAacj3HJooXWuXlphKSizntfxj3LtMR9BmrmRJOfK+SxNOVJzW2
        // +MowT20EkwKBgHmpB8jdZBgxI7o//m2BI5Y1UZ1KE5vx1kc7VXzHXSBjYqeV9FeF
        // 2ZZLP9POWh/1Fh4pzTmwIDODGT2UPhSQy0zq3O0fwkyT7WzXRknsuiwd53u/dejg
        // iEL2NPAJvulZ2+AuiHo5Z99LK8tMeidV46xoJDDUIMgTG+UQHNGhK5gNAoGAZn/S
        // Cn7SgMC0CWSvBHnguULXZO9wH1wZAFYNLL44OqwuaIUFBh2k578M9kkke7woTmwx
        // HxQTjmWpr6qimIuY6q6WBN8hJ2Xz/d1fwhYKzIp20zHuv5KDUlJjbFfqpsuy3u1C
        // kts5zwI7pr1ObRbDGVyOdKcu7HI3QtR5qqyjwaUCgYABo7Wq6oHva/9V34+G3Goh
        // 63bYGUnRw2l5BD11yhQv8XzGGZFqZVincD8gltNThB0Dc/BI+qu3ky4YdgdZJZ7K
        // z51GQGtaHEbrHS5caV79yQ8QGY5mUVH3E+VXSxuIqb6pZq2DH4sTAEFHyncddmOH
        // zoXBInYwRG9KE/Bw5elhUw==
        // -----END PRIVATE KEY-----

        // SHA256withDSA, 2048 bits
        // Validity
        //     Not Before: May 22 07:18:18 2018 GMT
        //     Not After : May 17 07:18:18 2038 GMT
        // Subject Key Identifier:
        //     76:66:9E:F7:3B:DD:45:E5:3B:D9:72:3C:3F:F0:54:39:86:31:26:53
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIErjCCBFSgAwIBAgIJAOktYLNCbr02MAsGCWCGSAFlAwQDAjA7MQswCQYDVQQG\n" +
        "EwJVUzENMAsGA1UECgwESmF2YTEdMBsGA1UECwwUU3VuSlNTRSBUZXN0IFNlcml2\n" +
        "Y2UwHhcNMTgwNTIyMDcxODE4WhcNMzgwNTE3MDcxODE4WjA7MQswCQYDVQQGEwJV\n" +
        "UzENMAsGA1UECgwESmF2YTEdMBsGA1UECwwUU3VuSlNTRSBUZXN0IFNlcml2Y2Uw\n" +
        "ggNHMIICOQYHKoZIzjgEATCCAiwCggEBAO5GyPhSm0ze3LSu+gicdULLj05iOfTL\n" +
        "UvZQ29sYz41zmqrLBQbdKiHqgJu2Re9sgTb5suLNjF047TOLPnU3jhPtWm2X8Xzi\n" +
        "VGIcHym/Q/MeZxStt/88seqroI3WOKzIML2GcrishT+lcGrtH36Tf1+ue2Snn3PS\n" +
        "WyxygNqPjllP5uUjYmFLvAf4QLMldkd/D2VxcwsHjB8y5iUZsXezc/LEhRZS/02m\n" +
        "ivqlRw3AMkq/OVe/ZtxFWsP0nsfxEGdZuaUFpppGfixxFvymrB3+J51cTt+pZBDq\n" +
        "D2y0DYfc+88iCs4jwHTfcDIpLb538HBjBj2rEgtQESQmB0ooD/+wsPsCIQC1bYch\n" +
        "gElNtDYL3FgpLgNSUYp7gIWv9ehaC7LO2z7biQKCAQBitvFOnDkUja8NAF7lDpOV\n" +
        "b5ipQ8SicBLW3kQamxhyuyxgZyy/PojZ/oPorkqW/T/A0rhnG6MssEpAtdiwVB+c\n" +
        "rBYGo3bcwmExJhdOJ6dYuKFppPWhCwKMHs9npK+lqBMl8l5j58xlcFeC7ZfGf8GY\n" +
        "GkhFW0c44vEQhMMbac6ZTTP4mw+1t7xJfmDMlLEyIpTXaAAk8uoVLWzQWnR40sHi\n" +
        "ybvS0u3JxQkb7/y8tOOZu8qlz/YOS7lQ6UxUGX27Ce1E0+agfPphetoRAlS1cezq\n" +
        "Wa7r64Ga0nkj1kwkcRqjgTiJx0NwnUXr78VAXFhVF95+O3lfqhvdtEGtkhDGPg7N\n" +
        "A4IBBgACggEBAMmSHQK0w2i+iqUjOPzn0yNEZrzepLlLeQ1tqtn0xnlv5vBAeefD\n" +
        "Pm9dd3tZOjufVWP7hhEz8xPobb1CS4e3vuQiv5UBfhdPL3f3l9T7JMAKPH6C9Vve\n" +
        "OQXE5eGqbjsySbcmseHoYUt1WCSnSda1opX8zchX04e7DhGfE2/L9flpYEoSt8lI\n" +
        "vMNjgOwvKdW3yvPt1/eBBHYNFG5gWPv/Q5KoyCtHS03uqGm4rNc/wZTIEEfd66C+\n" +
        "QRaUltjOaHmtwOdDHaNqwhYZSVOip+Mo+TfyzHFREcdHLapo7ZXqbdYkRGxRR3d+\n" +
        "3DfHaraJO0OKoYlPkr3JMvM/MSGR9AnZOcejUDBOMB0GA1UdDgQWBBR2Zp73O91F\n" +
        "5TvZcjw/8FQ5hjEmUzAfBgNVHSMEGDAWgBR2Zp73O91F5TvZcjw/8FQ5hjEmUzAM\n" +
        "BgNVHRMEBTADAQH/MAsGCWCGSAFlAwQDAgNHADBEAiBzriYE41M2y9Hy5ppkL0Qn\n" +
        "dIlNc8JhXT/PHW7GDtViagIgMko8Qoj9gDGPK3+O9E8DC3wGiiF9CObM4LN387ok\n" +
        "J+g=\n" +
        "-----END CERTIFICATE-----"
        // -----BEGIN PRIVATE KEY-----
        // MIICZQIBADCCAjkGByqGSM44BAEwggIsAoIBAQDuRsj4UptM3ty0rvoInHVCy49O
        // Yjn0y1L2UNvbGM+Nc5qqywUG3Soh6oCbtkXvbIE2+bLizYxdOO0ziz51N44T7Vpt
        // l/F84lRiHB8pv0PzHmcUrbf/PLHqq6CN1jisyDC9hnK4rIU/pXBq7R9+k39frntk
        // p59z0lsscoDaj45ZT+blI2JhS7wH+ECzJXZHfw9lcXMLB4wfMuYlGbF3s3PyxIUW
        // Uv9Npor6pUcNwDJKvzlXv2bcRVrD9J7H8RBnWbmlBaaaRn4scRb8pqwd/iedXE7f
        // qWQQ6g9stA2H3PvPIgrOI8B033AyKS2+d/BwYwY9qxILUBEkJgdKKA//sLD7AiEA
        // tW2HIYBJTbQ2C9xYKS4DUlGKe4CFr/XoWguyzts+24kCggEAYrbxTpw5FI2vDQBe
        // 5Q6TlW+YqUPEonAS1t5EGpsYcrssYGcsvz6I2f6D6K5Klv0/wNK4ZxujLLBKQLXY
        // sFQfnKwWBqN23MJhMSYXTienWLihaaT1oQsCjB7PZ6SvpagTJfJeY+fMZXBXgu2X
        // xn/BmBpIRVtHOOLxEITDG2nOmU0z+JsPtbe8SX5gzJSxMiKU12gAJPLqFS1s0Fp0
        // eNLB4sm70tLtycUJG+/8vLTjmbvKpc/2Dku5UOlMVBl9uwntRNPmoHz6YXraEQJU
        // tXHs6lmu6+uBmtJ5I9ZMJHEao4E4icdDcJ1F6+/FQFxYVRfefjt5X6ob3bRBrZIQ
        // xj4OzQQjAiEAsceWOM8do4etxp2zgnoNXV8PUUyqWhz1+0srcKV7FR4=
        // ----