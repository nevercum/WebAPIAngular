/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.management.jdp;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * JdpController is responsible to create and manage a broadcast loop.
 *
 * <p> Other part of code has no access to broadcast loop and have to use
 * provided static methods
 * {@link #startDiscoveryService(InetAddress,int,String,String) startDiscoveryService}
 * and {@link #stopDiscoveryService() stopDiscoveryService}
 * <p>{@link #startDiscoveryService(InetAddress,int,String,String) startDiscoveryService} could be called multiple
 * times as it stops the running service if it is necessary.
 * Call to {@link #stopDiscoveryService() stopDiscoveryService}
 * ignored if service isn't run.
 *
 *
 * <p> System properties below could be used to control broadcast loop behavior.
 * Property below have to be set explicitly in command line. It's not possible to
 * set it in management.config file.  Careless changes of these properties could
 * lead to security or network issues.
 * <ul>
 *     <li>com.sun.management.jdp.ttl         - set ttl for broadcast packet</li>
 *     <li>com.sun.management.jdp.pause       - set broadcast interval in seconds</li>
 *     <li>com.sun.management.jdp.source_addr - an address of interface to use for broadcast</li>
 * </ul>
 *
 * <p>null parameters values are filtered out on {@link JdpPacketWriter} level and
 * corresponding keys are not placed to packet.
 */
public final class JdpController {

    priva