/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.*;

import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.util.HexDumpEncoder;

/**
 * SSL/(D)TLS extensions in a handshake message.
 */
final class SSLExtensions {
    private final HandshakeMessage handshakeMessage;
    private final Map<SSLExtension, byte[]> extMap = new LinkedHashMap<>();
    private int encodedLength;

    // Extension map for debug logging
    private final Map<Integer, byte[]> logMap =
            SSLLogger.isOn ? new LinkedHashMap<>() : null;

    SSLExtensions(HandshakeMessage handshakeMessage) {
        this.handshakeMessage = handshakeMessage;
        this.encodedLength = 2;         // 2: the length of the extensions.
    }

    SSLExtensions(HandshakeMessage hm,
            ByteBuffer m, SSLExtension[] extensions) throws IOException {
        this.handshakeMessage = hm;

        if (m.remaining() < 2) {
            throw hm.handshakeContext.conContext.fatal(
                    Alert.DECODE_ERROR,
                    "Incorrect extensions: no length field");
        }

        int len = Record.getInt16(m);
        if (len > m.remaining()) {
            throw hm.handshakeContext.conContext.fatal(
                    Alert.DECODE_ERROR,
                    "Insufficient extensions data");
        }

        encodedLength = len + 2;        // 2: the length of the extensions.
        while (len > 0) {
            int extId = Record.getInt16(m);
            int extLen = Record.getInt16(m);
            if (extLen > m.remaining()) {
                throw hm.handshakeContext.conContext.fatal(
                        Alert.DECODE_ERROR,
                        "Error parsing extension (" + extId +
                        "): no sufficient data");
            }

            boolean isSupported = true;
            SSLHandshake handshakeType = hm.handshakeType();
            if (SSLExtension.isConsumable(extId) &&
                    SSLExtension.valueOf(handshakeType, extId) == null) {
                if (extId == SSLExtension.CH_SUPPORTED_GROUPS.id &&
                        handshakeType == SSLHandshake.SERVER_HELLO) {
                    // Note: It does not comply to the specification.  However,
                    // there are servers that send the supported_groups
                    // extension in ServerHello handshake message.
                    //
                    // TLS 1.3 should not send this extension.   We may want to
                    // limit the workaround for TLS 1.2 and prior version only.
                    // However, the implementation of the limit is complicated
                    // and inefficient, and may not worthy the maintenance.
                    isSupported = false;
                    if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                        SSLLogger.warning(
                                "Received buggy supported_groups extension " +
                                "in the ServerHello handshake message");
                    }
                } else if (handshakeType == SSLHandshake.SERVER_HELLO) {
                    throw hm.handshakeContext.conContext.fatal(
                            Alert.UNSUPPORTED_EXTENSION, "extension (" +
                                    extId + ") should not be presented in " +
                                    handshakeType.name);
                } else {
                    isSupported = false;
                    // debug log to ignore unknown extension for handshakeType
                }
            }

            if (isSupported) {
                isSupported = false;
                for (SSLExtension extension : extensions) {
                    if ((extension.id != extId) ||
                            (extension.onLoadConsumer == null)) {
                        continue;
                    }