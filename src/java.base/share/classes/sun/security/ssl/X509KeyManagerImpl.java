/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.lang.ref.*;
import java.net.Socket;
import java.security.AlgorithmConstraints;
import java.security.KeyStore;
import java.security.KeyStore.Builder;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.*;
import sun.security.provider.certpath.AlgorithmChecker;
import sun.security.validator.Validator;
import sun.security.util.KnownOIDs;

/**
 * The new X509 key manager implementation. The main differences to the
 * old SunX509 key manager are:
 *  . it is based around the KeyStore.Builder API. This allows it to use
 *    other forms of KeyStore protection or password input (e.g. a
 *    CallbackHandler) or to have keys within one KeyStore protected by
 *    different keys.
 *  . it can use multiple KeyStores at the same time.
 *  . it is explicitly designed to accommodate KeyStores that change over
 *    the lifetime of the process.
 *  . it makes an effort to choose the key that matches best, i.e. one that
 *    is not expired and has the appropriate certificate extensions.
 *
 * Note that this code is not explicitly performance optimized yet.
 *
 * @author  Andreas Sterbenz
 */
final class X509KeyManagerImpl extends X509ExtendedKeyManager
        implements X509KeyManager {

    // for unit testing only, set via privileged reflection
    private static Date verificationDate;

    // list of the builders
    private final List<Builder> builders;

    // counter to generate unique ids for the aliases
    private final AtomicLong uidCounter;

    // cached entries
    private final Map<String,Reference<PrivateKeyEntry>> entryCacheMap;

    X509KeyManagerImpl(Builder builder) {
        this(Collections.singletonList(builder));
    }

    X509KeyManagerImpl(List<Builder> builders) {
        this.builders = builders;
        uidCounter = new AtomicLong();
        entryCacheMap = Collections.synchronizedMap
                        (new SizedMap<>());
    }

    // LinkedHashMap with a max size of 10
    // see LinkedHashMap JavaDocs
    private static class SizedMap<K,V> extends LinkedHashMap<K,V> {
        @java.io.Serial
        private static final long serialVersionUID = -8211222668790986062L;

        @Override protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
            return size() > 10;
        }
    }

    //
    // public methods
    //

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        PrivateKeyEntry entry = getEntry(alias);
        return entry == null ? null :
                (X509Certificate[])entry.getCertificateChain();
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        PrivateKeyEntry entry = getEntry(alias);
        return entry == null ? null : entry.getPrivateKey();
    }

    @Override
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers,
            Socket socket) {
        return chooseAlias(getKeyTypes(keyTypes), issuers, CheckType.CLIENT,
                        getAlgorithmConstraints(socket));
    }

    @Override
    public String chooseEngineClientAlias(String[] keyTypes,
            Principal[] issuers, SSLEngine engine) {
        return chooseAlias(getKeyTypes(keyTypes), issuers, CheckType.CLIENT,
                        getAlgorithmConstraints(engine));
    }

    @Override
    public String chooseServerAlias(String keyType,
            Principal[] issuers, Socket socket) {
        return chooseAlias(getKeyTypes(keyType), issuers, CheckType.SERVER,
            getAlgorithmConstraints(socket),
            X509TrustManagerImpl.getRequestedServerNames(socket),
            "HTTPS");    // The SNI HostName is a fully qualified domain name.
                         // The certificate selection scheme for SNI HostName
                         // is similar to HTTPS endpoint identification scheme
                         // implemented in this provider.
                         //
                         // Using HTTPS endpoint identification scheme to guide
                         // the selection of an appropriate authentication
                         // certificate according to requested SNI extension.
                         //
                         // It is not a really HTTPS endpoint identification.
    }

    @Override
    public String chooseEngineServerAlias(String keyType,
            Principal[] issuers, SSLEngine engine) {
        return chooseAlias(getKeyTypes(keyType), issuers, CheckType.SERVER,
            getAlgorithmConstraints(engine),
            X509TrustManagerImpl.getRequestedServerNames(engine),
            "HTTPS");    // The SNI HostName is a fully qualified domain name.
                         // The certificate selection scheme for SNI HostName
                         // is similar to HTTPS endpoint identification scheme
                         // implemented in this provider.
                         //
                         // Using HTTPS endpoint identification scheme to guide
                         // the selection of an appropriate authentication
                         // certificate according to requested SNI extension.
                         //
                         // It is not a really HTTPS endpoint identification.
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return getAliases(keyType, issuers, CheckType.CLIENT, null);
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return getAliases(keyType, issuers, CheckType.SERVER, null);
    }

    //
    // implementation private methods
    //

    // Gets algorithm constraints of the socket.
    private AlgorithmConstraints getAlgorithmConstraints(Socket socket) {
        if (socket != null && socket.isConnected() &&
                socket instanceof SSLSocket sslSocket) {

            SSLSession session = sslSocket.getHandshakeSession();

            if (session != null) {
                if (ProtocolVersion.useTLS12PlusSpec(session.getProtocol())) {
                    String[] peerSupportedSignAlgs = null;

                    if (session instanceof ExtendedSSLSession extSession) {
                        peerSupportedSignAlgs =
                            extSession.getPeerSupportedSignatureAlgorithms();
                    }

                    return SSLAlgorithmConstraints.forSocket(
                        sslSocket, peerSupportedSignAlgs, true);
                }
            }

            return SSLAlgorithmConstraints.forSocket(sslSocket, true);
        }

        return SSLAlgorithmConstraints.DEFAULT;
    }

    // Gets algorithm constraints of the engine.
    private AlgorithmConstraints getAlgorithmConstraints(SSLEngine engine) {
        if (engine != null) {
            SSLSession session = engine.getHandshakeSession();
            if (session != null) {
                if (ProtocolVersion.useTLS12PlusSpec(session.getProtocol())) {
                    String[] peerSupportedSignAlgs = null;

                    if (session instanceof ExtendedSSLSession extSession) {
                        peerSupportedSignAlgs =
                            extSession.getPeerSupportedSignatureAlgorithms();
                    }

                    return SSLAlgorithmConstraints.forEngine(
                        engine, peerSupportedSignAlgs, true);
                }
            }
        }

        return SSLAlgorithmConstraints.forEngine(engine, true);
    }

    // we construct the alias we return to JSSE as seen in the code below
    // a unique id is included to allow us to reliably cache entries
    // between the calls to getCertificateChain() and getPrivateKey()
    // even if tokens are inserted or removed
    private String makeAlias(EntryStatus entry) {
        return uidCounter.incrementAndGet() + "." + entry.builderIndex + "."
                + entry.alias;
    }

    private PrivateKeyEntry getEntry(String alias) {
        // if the alias is null, return immediately
        if (alias == null) {
            return null;
        }

        // try to get the entry from cache
        Reference<PrivateKeyEntry> ref = entryCacheMap.get(alias);
        PrivateKeyEntry entry = (ref != null) ? ref.get() : null;
        if (entry != null) {
            return entry;
        }

        // parse the alias
        int firstDot = alias.indexOf('.');
        int secondDot = alias.indexOf('.', firstDot + 1);
        if ((firstDot == -1) || (secondDot == firstDot)) {
            // invalid alias
            return null;
        }
        try {
            int builderIndex = Integer.parseInt
                                (alias.substring(firstDot + 1, secondDot));
            String keyStoreAlias = alias.substring(secondDot + 1);
            Builder builder = builders.get(builderIndex);
            KeyStore ks = builder.getKeyStore();
            Entry newEntry = ks.getEntry(keyStoreAlias,
                    builder.getProtectionParameter(keyStoreAlias));
            if (!(newEntry instanceof PrivateKeyEntry)) {
                // unexpected type of entry
                return null;
            }
            entry = (PrivateKeyEntry)newEntry;
            entryCacheMap.put(alias, new SoftReference<>(entry));
            return entry;
        } catch (Exception e) {
            // ignore
            return null;
        }
    }

    // Class to help verify that the public key algorithm (and optionally
    // the signature algorithm) of a certificate matches what we need.
    private static class KeyType {

        final String keyAlgorithm;

        // In TLS 1.2, the signature algorithm  has been obsoleted by the
        // supported_signature_algorithms, and the certificate type no longer
        // restricts the algorithm used to sign the certificate.
        //
        // However, because we don't support certificate type checking other
        // than rsa_sign, dss_sign and ecdsa_sign, we don't have to check the
        // protocol version here.
        final String sigKeyAlgorithm;

        KeyType(String algorithm) {
            int k = algorithm.indexOf('_');
            if (k == -1) {
                keyAlgorithm = algorithm;
                sigKeyAlgorithm = null;
            } else {
                keyAlgorithm = algorithm.substring(0, k);
                sigKeyAlgorithm = algorithm.substring(k + 1);
            }
        }

        boolean matches(Certificate[] chain) {
            if (!chain[0].getPublicKey().getAlgorithm().equals(keyAlgorithm)) {
                return false;
            }
            if (sigKeyAlgorithm == null) {
                return true;
            }
            if (chain.length > 1) {
                // if possible, check the public key in the issuer cert
                return sigKeyAlgorithm.equals(
                        chain[1].getPublicKey().getAlgorithm());
            } else {
                // Check the signature algorithm of the certificate itself.
                // Look for the "withRSA" in "SHA1withRSA", etc.
                X509Certificate issuer = (X509Certificate)chain[0];
                String sigAlgName =
                        issuer.getSigAlgName().toUpperCase(Locale.ENGLISH);
                String pattern =
                        "WITH" + sigKeyAlgorithm.toUpperCase(Locale.ENGLISH);
                return sigAlgName.contains(pattern);
            }
        }
    }

    private static List<KeyType> getKeyTypes(String ... keyTypes) {
        if ((keyTypes == null) ||
                (keyTypes.length == 0) || (keyTypes[0] == null)) {
            return null;
        }
        List<KeyType> list = new ArrayList<>(keyTypes.length);
        for (String keyType : keyTypes) {
            list.add(new KeyType(keyType));
        }
        return list;
    }

    /*
     * Return the best alias that fits the given parameters.
     * The algorithm we use is:
     *   . scan through all the aliases in all builders in order
     *   . as soon as we find a perfect match, return
     *     (i.e. a match with a cert that has appropriate key usage,
     *      qualified endpoint identity, and is not expired).
     *   . if we do not find a perfect match, keep looping and remember
     *     the imperfect matches
     *   . at the end, sort the imperfect matches. we prefer expired certs
     *     with appropriate key usage to certs with the wrong key usage.
     *     return the first one of them.
     */
    private String chooseAlias(List<KeyType> keyTypeList, Principal[] issuers,
            CheckType checkType, AlgorithmConstraints constraints) {

        return chooseAlias(keyTypeList, issuers,
                                    checkType, constraints, null, null);
    }

    private String chooseAlias(List<KeyType> keyTypeList, Principal[] issuers,
            CheckType checkType, AlgorithmConstraints constraints,
            List<SNIServerName> requestedServerNames, String idAlgorithm) {

        if (keyTypeList == null || keyTypeList.isEmpty()) {
            return null;
        }

        Set<Principal> issuerSet = getIssuerSet(issuers);
        List<EntryStatus> allResults = null;
        for (int i = 0, n = builders.size(); i < n; i++) {
            try {
                List<EntryStatus> results = getAliases(i, keyTypeList,
                            issuerSet, false, checkType, constraints,
                            requestedServerNames, idAlgorithm);
                if (results != null) {
                    for (EntryStatus status : results) {
                        if (status.checkResult == CheckResult.OK) {
                            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                                SSLLogger.fine("KeyMgr: choosing key: " + status);
                            }
                            return makeAlias(status);
                        }
                    }
                    if (allResults == null) {
                        allResults = new ArrayList<>();
                    }
                    allResults.addAll(results);
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (allResults == null) {
            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                SSLLogger.fine("KeyMgr: no matching key found");
            }
            return null;
        }
        Collections.sort(allResults);
        if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
            SSLLogger.fine(
                    "KeyMgr: no good matching key found, "
                    + "returning best match out of", allResults);
        }
        return makeAlias(allResults.get(0));
    }

    /*
     * Return all aliases that (approximately) fit the parameters.
     * These are perfect matches plus imperfect matches (expired certificates
     * and certificates with the wrong extensions).
     * The perfect matches will be first in the array.
     */
    public String[] getAliases(String keyType, Principal[] issuers,
            CheckType checkType, AlgorithmConstraints constraints) {
        if (keyType == null) {
            return null;
        }

        Set<Principal> issuerSet = getIssuerSet(issuers);
        List<KeyType> keyTypeList = getKeyTypes(keyType);
        List<EntryStatus> allResults = null;
        for (int i = 0, n = builders.size(); i < n; i++) {
            try {
                List<EntryStatus> results = getAliases(i, keyTypeList,
                                    issuerSet, true, checkType, constraints,
                                    null, null);
                if (results != null) {
                    if (allResults == null) {
                        allResults = new ArrayList<>();
                    }
                    allResults.addAll(results);
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (allResults == null || allResults.isEmpty()) {
            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                SSLLogger.fine("KeyMgr: no matching alias found");
            }
            return null;
        }
        Collections.sort(allResults);
        if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
            SSLLogger.fine("KeyMgr: getting aliases", allResults);
        }
        return toAliases(allResults);
    }

    // turn candidate entries into unique aliases we can return to JSSE
    private String[] toAliases(List<EntryStatus> results) {
        String[] s = new String[results.size()];
        int i = 0;
        for (EntryStatus result : results) {
            s[i++] = makeAlias(result);
        }
        return s;
    }

    // make a Set out of the array
    private Set<Principal> getIssuerSet(Principal[] issuers) {
        if ((issuers != null) && (issuers.length != 0)) {
            return new HashSet<>(Arrays.asList(issuers));
        } else {
            return null;
        }
    }

    // a candidate match
    // identifies the entry by builder and alias
    // and includes the result of the certificate check
    private static class EntryStatus implements Comparable<EntryStatus> {

        final int builderIndex;
        final int keyIndex;
        final String alias;
        final CheckResult checkResult;

        EntryStatus(int builderIndex, int keyIndex, String alias,
                Certificate[] chain, CheckResult checkResult) {
            this.builderIndex = builderIndex;
            this.keyIndex = keyIndex;
            this.alias = alias;
            this.checkResult = checkResult;
        }

        @Override
        public int compareTo(EntryStatus other) {
            int result = this.checkResult.compareTo(other.checkResult);
            return (result == 0) ? (this.keyIndex - other.keyIndex) : result;
        }

        @Override
        public String toString() {
            String s = alias + " (verified: " + checkResult + ")";
            if (builderIndex == 0) {
                return s;
            } else {
                return "Builder #" + builderIndex + ", alias: " + s;
            }
        }
    }

    // enum for the type of certificate check we want to perform
    // (client or server)
    // also includes the check code itself
    private enum CheckType {

        // enum constant for "no check" (currently not used)
        NONE(Collections.emptySet()),

        // enum constant for "tls client" check
        // valid EKU for TLS client: any, tls_client
        CLIENT(new HashSet<>(List.of(
                KnownOIDs.anyExtendedKeyUsage.value(),
                KnownOIDs.clientAuth.value()
        ))),

        // enum constant for "tls server" check
        // valid EKU for TLS server: any, tls_server, ns_sgc, ms_sgc
        SERVER(new HashSet<>(List.of(
                KnownOIDs.anyExtendedKeyUsage.value(),
                KnownOIDs.serverAuth.value(),
                KnownOIDs.NETSCAPE_ExportApproved.value(),
                KnownOIDs.MICROSOFT_ExportApproved.value()
        )));

        // set of valid EKU values for this type
        final Set<String> validEku;

        CheckType(Set<String> validEku) {
            this.validEku = validEku;
        }

        private static boolean getBit(boolean[] keyUsage, int bit) {
            return (bit < keyUsage.length) && keyUsage[bit];
        }

        // Check if this certificate is appropriate for this type of use
        // first check extensions, if they match, check expiration.
        //
        // Note: we may want to move this code into the sun.security.validator
        // package
        CheckResult check(X509Certificate cert, Date date,
                List<SNIServerName> serverNames, String idAlgorithm) {

            if (this == NONE) {
                return CheckResult.OK;
            }

            // check extensions
            try {
                // check extended key usage
                List<String> certEku = cert.getExtendedKeyUsage();
                if ((certEku != null) &&
                        Collections.disjoint(validEku, certEku)) {
                    // if extension is present and does not contain any of
                    // the valid EKU OIDs, return extension_mismatch
                    return CheckResult.EXTENSION_MISMATCH;
                }

                // check key usage
                boolean[] ku = cert.getKeyUsage();
                if (ku != null) {
                    String algorithm = cert.getPublicKey().getAlgorithm();
                    boolean supportsDigitalSignature = getBit(ku, 0);
                    switch (algorithm) {
                        case "RSA":
                            // require either signature bit
                            // or if server also allow key encipherment bit
                            if (!supportsDigitalSignature) {
                             