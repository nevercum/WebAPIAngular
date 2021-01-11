/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.fs;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.channels.SeekableByteChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

import static sun.nio.fs.UnixNativeDispatcher.*;
import static sun.nio.fs.UnixConstants.*;

/**
 * Unix implementation of SecureDirectoryStream.
 */

class UnixSecureDirectoryStream
    implements SecureDirectoryStream<Path>
{
    private final UnixDirectoryStream ds;
    private final int dfd;

    UnixSecureDirectoryStream(UnixPath dir,
                              long dp,
                              int dfd,
                              DirectoryStream.Filter<? super Path> filter)
    {
        this.ds = new UnixDirectoryStream(dir, dp, filter);
        this.dfd = dfd;
    }

    @Override
    public void close()
        throws IOException
    {
        ds.writeLock().lock();
        try {
            if (ds.closeImpl()) {
                UnixNativeDispatcher.close(dfd, e -> e.asIOException(ds.directory()));
            }
        } finally {
            ds.writeLock().unlock();
        }
    }

    @Override
    public Iterator<Path> iterator() {
        return ds.iterator(this);
    }

    private UnixPath getName(Path obj) {
        if (obj == null)
            throw new NullPointerException();
        if (!(obj instanceof UnixPath))
            throw new ProviderMismatchException();
        return (UnixPath)obj;
    }

    /**
     * Opens sub-directory in this directory
     */
    @Override
    public SecureDirectoryStream<Path> newDirectoryStream(Path obj,
                                                          LinkOption... options)
        throws IOException
    {
        UnixPath file = getName(obj);
        UnixPath child = ds.directory().resolve(file);
        boolean followLinks = Util.followLinks(options);

        // permission check using name resol