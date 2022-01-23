/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.loader;

import jdk.internal.misc.VM;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RawNativeLibraries has the following properties:
 * 1. Native libraries loaded in this RawNativeLibraries instance are
 *    not JNI native libraries.  Hence JNI_OnLoad and JNI_OnUnload will
 *    be ignored.  No support for linking of native method.
 * 2. Native libraries not auto-unloaded.  They may be explicitly unloaded
 *    via NativeLibraries::unload.
 * 3. No relationship with class loaders.
 */
public final class RawNativeLibraries {
    final Set<RawNativeLibraryImpl> libraries = ConcurrentHashMap.newKeySet();
    final Class<?> caller;

    private RawNativeLibraries(MethodHandles.Lookup trustedCaller) {
        this.caller = trustedCaller.lookupClass();
    }

    /**
     * Creates a RawNativeLibraries instance that has no relationship with
     * any class loaders and disabled auto unloading.
     *
     * This static factory method is restricted for JDK trusted class use.
     */
    public static RawNativeLibraries newInstance(MethodHandles.Lookup trustedCaller) {
        if (!trustedCaller.hasFullPrivilegeAccess() ||
                !VM.isSystemDomainLoader(trustedCaller.lookupClass().getClassLoader())) {
            throw new InternalError(trustedCaller + " does not have access to raw native library loading");
        }
        return new RawNativeLibraries(trustedCaller);
    }

    /*
     * Load a native library from the given path.  Returns null if the given
     * library is determined to be non-loadable, which is system-dependent.
     *
     * The library is opened with the platform-specific library loading
     * mechanism. If this method is called with the same path multiple times,
     * the library is opened the same number of times. To close the library
     * of the given path, {@code #unload} must be called on all the
     * {@code NativeLibrary} instances that load it.
     *
     * @param path the path of the native library
     */
    @SuppressWarnings("removal")
    public NativeLibrary load(Path path) {
        String name = AccessController.doPrivileged(new PrivilegedAction<>() {
            public String run() {
                try {
                    return path.toRealPath().toString();
                } catch (IOException e) {
                    return null;
                }
            }
        });
        if (name == null) {
            return null;
        }
        return load(name);
    }

    /**
     * Load a native library of the given pathname, which is platform-specific.
     * Returns null if it fails to load the given pathname.
     *
     * If the given pathname does not contain a name-separator character,
     * for example on Unix a slash character, the library search strategy
     * is system-dependent for example on Unix, see dlopen.
     *
     * @apiNote
     * The {@code pathname} argument is platform-specific.
     * {@link System#mapLibraryName} can be used to convert a name to
     * a platform-specific pathname:
     * {@snippet
     *     RawNativeLibraries libs = RawNativeLibraries.newInstance(MethodHandles.lookup());
     *     NativeLibrary lib = libs.load(System.mapLibraryName("blas"));
     * }
     *
     * The library is opened with the platform-specific library loading
     * mechanism. If this method is called with the same pathname multiple times,
     * the library is opened the same number of times. To close the library
     * of the given path, {@code #unload} must be called on all the
     * {@code NativeLibrary} instances that load it.
     *
     * @param pathname the pathname of the native library
     * @see System#mapLibraryName(String)
     */
    public NativeLibrary load(String pathname) {
        