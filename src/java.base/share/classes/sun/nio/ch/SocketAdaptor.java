/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Set;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

// Make a socket channel look like a socket.
//
// The methods in this class are defined in exactly the same order as in
// java.net.Socket so as to simplify tracking future changes to that class.
//

class SocketAdaptor
    extends Socket
{
    // The channel being adapted
    private final SocketChannelImpl sc;

    // Timeout "option" value for reads
    private volatile int timeout;

    private SocketAdaptor(SocketChannelImpl sc) throws SocketException {
        super(DummySocketImpl.create());
        this.sc = sc;
    }

    @SuppressWarnings("removal")
    static Socket create(SocketChannelImpl sc) {
        try {
            if (System.getSecurityManager() == null) {
                return new SocketAdaptor(sc);
            } else {
                PrivilegedExceptionAction<Socket> pa = () -> new SocketAdaptor(sc);
                return AccessController.doPrivileged(pa);
            }
        } catch (SocketException | PrivilegedActionException e) {
            throw new InternalError(e);
        }
    }

    private InetSocketAddress localAddress() {
        return (InetSocketAddress) sc.localAddress();
    }

    private InetSocketAddress remoteAddress() {
        return (InetSocketAddress) sc.remoteAddress();
    }

    @Override
    public void connect(SocketAddress remote) throws IOException {
        connect(remote, 0);
    }

    @Override
    public void connect(SocketAddress remote, int timeout) throws IOException {
        if (remote == null)
            throw new IllegalArgumentException("connect: The address can't be null");
        if (timeout < 0)
            throw new IllegalArgumentException("connect: timeout can't be negative");
        try {
            if (timeout > 0) {
                long nanos = MILLISECONDS.toNanos(timeout);
                sc.blockingConnect(remote, nanos);
            } else {
                sc.blockingConnect(remote, Long.MAX_VALUE);
            }
        } catch (Exception e) {
            Net.translateException(e, true);
        }
    }

    @Override
    public void bind(SocketAddress local) throws IOException {
        try {
            sc.bind(local);
        } catch (Exception x) {
            Net.translateException(x);
        }
    }

    @Override
    public InetAddress getInetAddress() {
        InetSocketAddress remote = remoteAddress();
        if (remote == null) {
            return null;
        } else {
            return remote.getAddress();
        }
    }

    @Override
    public InetAddress getLocalAddress() {
        if (sc.isOpen()) {
            InetSocketAddress local = localAddress();
            if (local != null) {
                return Net.getRevealedLocalAddress(local).getAddress();
            }
        }
        return new InetSocketAddress(0).getAddress();
    }

    @Override
    public int getPort() {
        InetSocketAddress remote = remoteAddress();
        if (remote == null) {
            return 0;
        } else {
            return remote.getPort();
        }
    }

    @Override
    public int getLocalPort() {
        InetSocketAddress local = localAddress();
        if (local == null) {
            return -1;
        } else {
            return local.getPort();
        }
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return sc.remoteAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return Net.getRevealedLocalAddress(sc.localAddress());
    }

    @Override
    public SocketChannel getChannel() {
        return sc;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (!sc.isOpen())
            throw new SocketException("Socket is closed");
        if (!sc.isConnected())
            throw new SocketException("Socket is not connected");
        if (!sc.isInputOpen())
            throw new SocketException("Socket input is shutdown");
        return new SocketInputStream(sc, () -> timeout);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (!sc.isOpen())
            throw new SocketException("Socket is closed");
        if (!sc.isConnected())
            throw new SocketException("Socket is not connected");
        if (!sc.isOutputOpen())
            throw new SocketException("Socket output is shutdown");
        return new SocketOutputStream(sc);
    }

    private void setBooleanOption(SocketOption<Boolean> name, boolean value)
        throws SocketException
    {
        try {
            sc.setOption(name, value);
        } catch (IOException x) {
            Net.translateToSocketException(x);
        }
    }

    private void setIntOption(SocketOption<Integer> name, int value)
        throws SocketException
    {
        try {
            sc.setOption(name, value);
        } catch (IOException x) {
            Net.translateToSocketException(x);
        }
    }

    private boolean getBooleanOption(SocketOption<Boolean> name) throws SocketException {
        try {
            return sc.getOption(name).booleanValue();
        } catch (IOException x) {
            Net.translateToSocketException(x);
            return false;       // keep compiler happy
        }
    }

    private int getIntOption(SocketOption<Integer> name) throws SocketException {
        try {
            return sc.getOption(name).intValue();
        } catch (IOException x) {
            Net.translateToSocketException(x);
            return -1;          // keep comp