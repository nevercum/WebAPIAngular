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
    private AlgorithmConstraints getAlgorithmConstraints(Soc