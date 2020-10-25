/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Defines the Java Debug Interface.
 * <p>
 * The Java Debug Interface (JDI) is a high level Java API providing
 * information useful for debuggers and similar systems needing access to the
 * running state of a (usually remote) virtual machine.
 * <p>
 * JDI provides introspective access to a running virtual machine's state,
 * Class, Array, Interface, and primitive types, and instances of those types.
 * <p>
 * JDI also provides explicit control over a virtual machine's execution.
 * The ability to suspend and resume threads, and to set breakpoints,
 * watchpoints, etc. Notification of exceptions, class loading, thread
 * creation, etc. The ability to inspect a suspended thread's state, local
 * variables, stack backtrace, etc.
 * <p>
 * JDI is the highest-layer of the
 * 