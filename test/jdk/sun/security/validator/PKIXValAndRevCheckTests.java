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

/**
 * @test
 * @bug 8225436
 * @summary Stapled OCSPResponses should be added to PKIXRevocationChecker
 *          irrespective of revocationEnabled flag
 * @library /test/lib
 * @modules java.base/sun.security.validator
 * @build jdk.test.lib.Convert
 * @run main PKIXValAndRevCheckTests
 */

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertPathValidatorException.BasicReason;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sun.security.validator.Validator;

public class PKIXValAndRevCheckTests {

    // subject: CN=Good Server,O=TestPKI
    // issuer: CN=CA1 Intermediate,O=TestPKI
    // serial: 01000015
    // notBefore: Aug 16 02:42:32 2019 GMT
    // notAfter: Aug 15 02:42:32 2020 GMT
    static final String GOOD_SERVER_PEM =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDjTCCAnWgAwIBAgIEAQAAFTANBgkqhkiG9w0BAQsFADAtMRAwDgYDVQQKDAdU\n" +
        "ZXN0UEtJMRkwFwYDVQQDDBBDQTEgSW50ZXJtZWRpYXRlMB4XDTE5MDgxNjAyNDIz\n" +
        "MloXDTIwMDgxNTAyNDIzMlowKDEQMA4GA1UECgwHVGVzdFBLSTEUMBIGA1UEAwwL\n" +
        "R29vZCBTZXJ2ZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDSQSUF\n" +
        "L5th5P21Vijy5pm0WnC0AWCSHX5003f9um70k/IdaAg2rsj/aKHnsm+r4xXGD236\n" +
        "S7DxBR2w8NTnAofgRWlsAn74lWQhV2p3SU/JKEtFbJV1YAnNOUPKsCnVKDfe3Gev\n" +
        "zxOLpZ/VKSx9u20bOUbh6QxqlIdIuJ6AW/cgyjdvuN16sIWGWzl17lm81T1cy89x\n" +
        "TvvsHHqfAh+y3jMwqvIRxoaNQoOjcmxSldRnCwBfhg8xHxB4wKa4z+6Y3gndzne1\n" +
        "Ms0itbtdYlSF3ADOtwoBrftYDpvsG8VhA4x4QqFAAKx1FPO6OJBYGNfZvnoDDi9g\n" +
        "i0PgDNftm0l/6FGlAgMBAAGjgbkwgbYwHQYDVR0OBBYEFJNBzLRxgb0znmeuYXc3\n" +
        "UaFGd9m3MB8GA1UdIwQYMBaAFJraQNM+W62lwqzcSEc6VjNXAaSaMA4GA1UdDwEB\n" +
        "/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwEwYDVR0RBAww\n" +
        "CoIIdGVyaXlha2kwMAYIKwYBBQUHAQEEJDAiMCAGCCsGAQUFBzABhhRodHRwOi8v\n" +
        "dGVyaXlha2k6NTIwMDANBgkqhkiG9w0BAQsFAAOCAQEAadl0EQdBJw6dBBelxtox\n" +
        "id18HMt+MsXssHun1nx8My0VZ3uQBNJ4GgKipNFa+s8nPZIzRr0Ls65dIUiBcg3R\n" +
        "ep0he/gnkaowRRxGqMkALl3VzUz8INSRzdCIVm0EBeDCaHGLzE6G3uIqWwldei8k\n" +
        "IOHtiVLESAJvCvSEOAnoJHRVD8+tbEIxRsSFkoKGqc5U7bsCVC5uSXOkiHEP/3zm\n" +
        "6YixiT+hLk6QKegkQxQPZ+irGBeN2q2PAq5vTh1hJDciwqE3h8GxZ15iR3WIedc8\n" +
        "6EHJ7+N27nWZLtFgcLKNXEsm1Eh/YNIrpeN0OQBGSLD3lIju5IO0mD3oQfA4miqT\n" +
        "wQ==\n" +
        "-----END CERTIFICATE-----";

    // subject: CN=Bad Server,O=TestPKI
    // issuer: CN=CA1 Intermediate,O=TestPKI
    // serial: 01000016
    // notBefore: Aug 16 02:43:11 2019 GMT
    // notAfter: Aug 15 02:43:11 2020 GMT
    static final String BAD_SERVER_PEM =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDjDCCAnSgAwIBAgIEAQAAFjANBgkqhkiG9w0BAQsFADAtMRAwDgYDVQQKDAdU\n" +
        "ZXN0UEtJMRkwFwYDVQQDDBBDQTEgSW50ZXJtZWRpYXRlMB4XDTE5MDgxNjAyNDMx\n" +
        "MVoXDTIwMDgxNTAyNDMxMVowJzEQMA4GA1UECgwHVGVzdFBLSTETMBEGA1UEAwwK\n" +
        "QmFkIFNlcnZlcjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL9syEok\n" +
        "K/8E/hm8Q/cLhSwirDIGFC9nqS8p1bVNTClkMsqxkQAcQptP1zLZiMBdgLjOH3cF\n" +
        "60UAaz2Y+7WYU5MB6AE8IloDgUUKKUTUmXHzM31OiSVu21+ooo59XzV/cCEu+Qlu\n" +
        "AiaDuTDhIEtM58zs/3RZN0h+v8M2NXUU4bwYmYVeqP8UW9BEjgznIIrvGpqpHKz5\n" +
        "EwctL+u/h5Z/DoCOnVq3irMCpInY5/VbIuxfkdfawsFROzUWl6fZ3+CTfQfHhKSM\n" +
        "sz1/zY/BtQLDTKY120M2FaLmmIoOLrqZo8Pi+JL8IVentNfSHvUX5rrnPKB2/JVS\n" +
        "8Jc2qvLPk4PWbwECAwEAAaOBuTCBtjAdBgNVHQ4EFgQU8z9qWpJ/FDmKOgQI2vY7\n" +
        "0OwCNFEwHwYDVR0jBBgwFoAUmtpA0z5braXCrNxIRzpWM1cBpJowDgYDVR0PAQH/\n" +
        "BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjATBgNVHREEDDAK\n" +
        "ggh0ZXJpeWFraTAwBggrBgEFBQcBAQQkMCIwIAYIKwYBBQUHMAGGFGh0dHA6Ly90\n" +
        "ZXJpeWFraTo1MjAwMA0GCSqGSIb3DQEBCwUAA4IBAQBzi8U/3b6hfGwE/przqyha\n" +
        "Y40Nhh1uCm1rz4bZ27z2Q3vzlg2ay4V3I2NaR4eY/wsuO8AW0qdBJExmYqgi+l9U\n" +
        "S6i9WqyI22jAKUPsx9WmCZltyU589VDU40h2g6C4+8VnOZm6OKKKTjkKrDn/IFJF\n" +
        "jU4yIvXrEBHNJr/tcQW0+dF2okIBAnVLUNs8CZZJyWesQtu6J0OBj4tE8s0ET4ep\n" +
        "XC/3mZkGjziEZw8/dDZ0/+CQbrkDP2vs6iNjz/LUIA9dVXUs9sNeqW+VEHI3vZvJ\n" +
        "gYVDJn5tWZSIY/O2zV97dz9VeDH3aukuoEm5aAxxhazxRDntcnl2DYrrr2bGuS2Y\n" +
        "-----END CERTIFICATE-----";

    // subject: CN=CA1 Intermediate,O=TestPKI
    // issuer: CN=TestRoot,O=TestPKI
    // serial: 0100
    // notBefore: May  6 06:00:00 2015 GMT
    // notAfter: Jan 21 12:00:00 2025 GMT
    static final String INT_CA_PEM =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIEbTCCAlWgAwIBAgICAQAwDQYJKoZIhvcNAQELBQAwJTEQMA4GA1UECgwHVGVz\n" +
        "dFBLSTERMA8GA1UEAwwIVGVzdFJvb3QwHhcNMTUwNTA2MDYwMDAwWhcNMjUwMTIx\n" +
        "MTIwMDAwWjAtMRAwDgYDVQQKDAdUZXN0UEtJMRkwFwYDVQQDDBBDQTEgSW50ZXJt\n" +
        "ZWRpYXRlMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtdKjBpeuJJEv\n" +
        "di4wMGHE5y7inXuDvMCjkjFRv9XOH20BVAIDMTMeIByk6NQJYeeaTRGXTawZN8/c\n" +
        "aXtQuqsRGz/q2va/I+A5HIvtu+vujdVksu2baafGM0Ql8Gdzj8MdLGb+kGFji/FX\n" +
        "f+2PL8UfpnmUikLN728lF9bzcA046I8B43SriFJeYOlLPfE/yjNg5eccdMPDBw7h\n" +
        "KQPVbXfpcmWRJm/vGlCR38Rd7ceYF3/ctf/0J8Dab7q98ITpH9q5NFD+o2NJZoFq\n" +
        "7HBPdGTIJ73m3WPzLRrU+JPD7xs9wgmuuRq6hU/lPSd5IJSkJ/cyXkma1RwBO4Lm\n" +
        "rU2aWDGhNwIDAQABo4GeMIGbMB0GA1UdDgQWBBSa2kDTPlutpcKs3EhHOlYzVwGk\n" +
        "mjAfBgNVHSMEGDAWgBTwWIIuUEAneAXJeud3ioakmTg32zAPBgNVHRMBAf8EBTAD\n" +
        "AQH/MA4GA1UdDwEB/wQEAwIBhjA4BggrBgEFBQcBAQQsMCowKAYIKwYBBQUHMAGG\n" +
        "HGh0dHA6Ly9qaWFuLm9zdGFwbGUub3JnOjcxMDAwDQYJKoZIhvcNAQELBQADggIB\n" +
        "ADRoginKFigLOKz1NJN86w66eP3r4D/5Qq8+G9DiasmThLQfaVYBvuaR9kL3D9Vr\n" +
        "1EEXRGmCxMTHetW0SQ/SsMeRbBf8Ihck4MOeAC9cMysvtNfjpwxaAh6zF5bX4pjj\n" +
        "33gJpjPLNAZru09rSF0GIo9CxPh9rBOkmttrnPDX7rLR9962i/P4KHyHknGM7gY0\n" +
        "U88ddugkANiFIiAfBRGFz3AqMiMi3VP5STCP0k0ab/frkev6C/qq3th4gQ/Bog/5\n" +
        "YaoWvzGAs7QoQ7+r0BIRZhG71WQKD4Yx1a43RnG3tFPLFznk0odeh8sr/CI3H/+b\n" +
        "eyyJLd02ApujZoAfMHzTcq/27mO1ZvA5qSt4wsb7gswnIYwXbJZBBRoixGFD7VP0\n" +
        "NEXREljpEuGIIy2lkHb5wNV3OEMmAmoKwx1GXWXRfQRHqn1f2/XLYInDg0u9u+G6\n" +
        "UX3edn6rwP+vlIX2Cx4qC/yX4zg7YxMXCwrol91/7wugkUGPjmU6qmK+TtuwZNQG\n" +
        "2wtCB4FJXa0YZyDd7U/FH7nWZtG9BgzpLit90hC4+m5V4E/7I6slvwxpkE7y0Nju\n" +
        "tjy/qcuil6imrOR/apuwT1ecAmyjm1UmpKPLLzYnE6AtSKOTndGa2iNyPDrseFLy\n" +
        "7TUF/fg/dvZ46OmouSX3upAFRnvpXYXwSQRQ2S+wEnbp\n" +
        "-----END CERTIFICATE-----";

    // subject: CN=TestRoot,O=TestPKI
    // issuer: CN=TestRoot,O=TestPKI
    // serial: 01
    // notBefore: May  6 00:36:03 2015 GMT
    // notAfter: Jan 21 00:36:03 2035 GMT
    static final String ROOT_CA_PEM =
        "-----BEGIN CERTIFICATE-----\n" +
        "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\n" +
        "uvSM40WEaUsmfpxU+tF2LJvWmNNbDDtEmbFsQQ==\n" +
        "-----END CERTIFICATE-----";

    // OCSP Response Status: successful (0x0)
    // Response Type: Basic OCSP Response
    // Version: 1 (0x0)
    // Responder Id: O = TestPKI, CN = TestRoot
    // Produced At: Aug 16 06:06:27 2019 GMT
    // Responses:
    // Certificate ID:
    //   Hash Algorithm: sha1
    //   Issuer Name Hash: 622C4B816C42E2E99FF41B5CED388DAA33A6B9B3
    //   Issuer Key Hash: F058822E5040277805C97AE7778A86A4993837DB
    //   Serial Number: 0100
    // Cert Status: good
    // This Update: Aug 16 06:06:27 2019 GMT
    // Next Update: Aug 17 06:06:27 2019 GMT
    static final String INT_CA_OCSP_PEM =
        "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";

    // OCSP Response Status: successful (0x0)
    // Response Type: Basic OCSP Response
    // Version: 1 (0x0)
    // Responder Id: O = TestPKI, CN = CA1 Intermediate
    // Produced At: Aug 16 05:03:09 2019 GMT
    // Responses:
    // Certificate ID:
    //   Hash Algorithm: sha1
    //   Issuer Name Hash: FE48D59BAF624773549AE209AA14FD20DCE6B8F4
    //   Issuer Key Hash: 9ADA40D33E5BADA5C2ACDC48473A56335701A49A
    //   Serial Number: 01000015
    // Cert Status: good
    // This Update: Aug 16 05:03:09 2019 GMT
    // Next Update: Aug 17 05:03:09 2019 GMT
    static final String GOOD_GUY_OCSP_PEM =
        "MIIGfgoBAKCCBncwggZzBgkrBgEFBQcwAQEEggZkMIIGYDCB0KEvMC0xEDAOBgNV\n" +
        "BAoMB1Rlc3RQS0kxGTAXBgNVBAMMEENBMSBJbnRlcm1lZGlhdGUYDzIwMTkwODE2\n" +
        "MDUwMzA5WjBnMGUwPTAJBgUrDgMCGgUABBT+SNWbr2JHc1Sa4gmqFP0g3Oa49AQU\n" +
        "mtpA0z5braXCrNxIRzpWM1cBpJoCBAEAABWAABgPMjAxOTA4MTYwNTAzMDlaoBEY\n" +
        "DzIwMTkwODE3MDUwMzA5WqEjMCEwHwYJKwYBBQUHMAECBBIEEN087n3ef92+4d2K\n" +
        "+XaudDUwDQYJKoZIhvcNAQELBQADggEBAErIOOkLGwbDWgrpl3lQbsnaoVY6YNYV\n" +
        "x1bfJ89S8twBouei6a/HmAIDqUPmlVF7gm8sNvgANXuZGkWXmqadSpWxLA36ZT4d\n" +
        "70iRLmdTaPnKVpUEO5dYMg7nWW+D4hp9wupkPaB3PsEPb4pwrcTOUH1FAi3pZ+hF\n" +
        "oeNDaE3jHQGEz4dVK1XgK2pxFNf4aTIgj+w40xN5yaCcTYicbLmumNGCzrGwnRqh\n" +
        "tyoiz27+rTxFrEeWGnNslJfScD9O4oe/KhvYBusurNVrFgG4VcxB5NNemrCW4/cf\n" +
        "dehv8z50FaZvq1xklqkZ4hgbjNxtI8lAHp+wYDQJub0mhXWmb9K/4kOgggR1MIIE\n" +
        "cTCCBG0wggJVoAMCAQICAgEAMA0GCSqGSIb3DQEBCwUAMCUxEDAOBgNVBAoMB1Rl\n" +
        "c3RQS0kxETAPBgNVBAMMCFRlc3RSb290MB4XDTE1MDUwNjA2MDAwMFoXDTI1MDEy\n" +
        "MTEyMDAwMFowLTEQMA4GA1UECgwHVGVzdFBLSTEZMBcGA1UEAwwQQ0ExIEludGVy\n" +
        "bWVkaWF0ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALXSowaXriSR\n" +
        "L3YuMDBhxOcu4p17g7zAo5IxUb/Vzh9tAVQCAzEzHiAcpOjUCWHnmk0Rl02sGTfP\n" +
        "3Gl7ULqrERs/6tr2vyPgORyL7bvr7o3VZLLtm2mnxjNEJfBnc4/DHSxm/pBhY4vx\n" +
        "V3/tjy/FH6Z5lIpCze9vJRfW83ANOOiPAeN0q4hSXmDpSz3xP8ozYOXnHHTDwwcO\n" +
        "4SkD1W136XJlkSZv7xpQkd/EXe3HmBd/3LX/9CfA2m+6vfCE6R/