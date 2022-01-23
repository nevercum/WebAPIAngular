/*
 * Copyright (c) 1996, 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;

/**
 * Signals that an unexpected exception has occurred in a static initializer.
 * An {@code ExceptionInInitializerError} is thrown to indicate that an
 * exception occurred during evaluation of a static initializer or the
 * initializer for a static variable.
 *
 * @author  Frank Yellin
 * @since   1.1
 */
public class ExceptionInInitializerError extends LinkageError {
    /**
     * Use serialVersionUID from JDK 1.1.X for interoperability
     */
    @java.io.Serial
    private static final long serialVersionUID = 1521711792217232256L;

    /**
     * Constructs an {@code ExceptionInInitializerError} with
     * {@code null} as its detail message string and with no saved
     * throwable object.
     * A detail message is a String that describes this particular exception.
     */
    public ExceptionInInitializerError() {
        initCause(nu