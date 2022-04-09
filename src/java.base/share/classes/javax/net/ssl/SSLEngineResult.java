/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javax.net.ssl;

import java.nio.ByteBuffer;

/**
 * An encapsulation of the result state produced by
 * {@code SSLEngine} I/O calls.
 *
 * <p> A {@code SSLEngine} provides a means for establishing
 * secure communication sessions between two peers.  {@code SSLEngine}
 * operations typically consume bytes from an input buffer and produce
 * bytes in an output buffer.  This class provides operational result
 * values describing the state of the {@code SSLEngine}, including
 * indications of what operations are needed to finish an
 * ongoing handshake.  Lastly, it reports the number of bytes consumed
 * and produced as a result of this operation.
 *
 * @see SSLEngine
 * @see SSLEngine#wrap(ByteBuffer, ByteBuffer)
 * @see SSLEngine#unwrap(ByteBuffer, ByteBuffer)
 *
 * @author Brad R. Wetmore
 * @since 1.5
 */

public class SSLEngineResult {

    /**
     * An {@code SSLEngineResult} enum describing the overall result
     * of the {@code SSLEngine} operation.
     *
     * The {@code Status} value does not reflect the
     * state of a {@code SSLEngine} handshake currently
     * in progress.  The {@code SSLEngineResult's HandshakeStatus}
     * should be consulted for that information.
     *
     * @author Brad R. Wetmore
     * @since 1.5
     */
    public enum Status {

        /**
         * The {@code SSLEngine} was not able to unwrap the
         * incoming data because there were not enough source bytes
         * available to make a complete packet.
         *
         * <P>
         * Repeat the call once more bytes are available.
         */
        BUFFER_UNDERFLOW,

        /**
         * The {@code SSLEngine} was not able to process the
         * operation because there are not enough bytes available in the
         * destination buffer to hold the result.
         * <P>
         * Repeat the call once more bytes are available.
         *
         * @see SSLSession#getPacketBufferSize()
         * @see SSLSession#getApplicationBufferSize()
         */
        BUFFER_OVERFLOW,

        /**
         * The {@code SSLEngine} completed the operation, and
         * is available to process similar calls.
         */
        OK,

        /**
         * The operation just closed this side of the
         * {@code SSLEngine}, or the operation
         * could not be completed because it was already 