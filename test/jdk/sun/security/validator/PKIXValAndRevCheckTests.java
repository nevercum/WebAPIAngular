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
        "MIIFKDCCAxCgAwIBAgIBATANBgkqhkiG9w0BAQsFADAlMRAwDgYDVQQKDAdUZXN0\n" +
        "UEtJMREwDwYDVQQDDAhUZXN0Um9vdDAeFw0xNTA1MDYwMDM2MDNaFw0zNTAxMjEw\n" +
        "MDM2MDNaMCUxEDAOBgNVBAoMB1Rlc3RQS0kxETAPBgNVBAMMCFRlc3RSb290MIIC\n" +
        "IjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAuLCcVhyIaPV5CHjvnyAvK978\n" +
        "TUC2YY5wZ8e21L8C+SvxCoE5U66H+wMsNIC90i1ynlz49G4oKR67GXcijJpVD1fA\n" +
        "Dq3Hpc3WDY9/5jRKWZOC0qLmXMPEF8wrwyC3aQ81sytDJOhEfxEf3KvwFDI9NUQb\n" +
        "tFdWB+IDEvaDCTJgOt/jIJAzLTxzvwPBzP/JHdRCwKdmlQStRp20AmDtpgIlm2RH\n" +
        "v8ywabI/UqncZHe/LVYdmDNxztziM98Zs1I7vsO2/yebWE/QH3g3k9ZgaT6UnBAq\n" +
        "gvV2TQhZOGMmps7RrfNdVEHeeRXmJTFAtmbi/o6Ou7xli+3bDuY5Faxk7u