/*
 * Copyright (c) 1996, 2021, Oracle and/or its affiliates. All rights reserved.
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
package java.rmi.server;

import java.io.*;
import java.util.*;

/**
 * <code>LogStream</code> provides a mechanism for logging errors that are
 * of possible interest to those monitoring a system.
 *
 * @author  Ann Wollrath (lots of code stolen from Ken Arnold)
 * @since   1.1
 * @deprecated no replacement
 */
@Deprecated
public class LogStream extends PrintStream {

    /** table mapping known log names to log stream objects */
    private static Map<String,LogStream> known = new HashMap<>(5);
    /** default output stream for new logs */
    private static PrintStream  defaultStream = System.err;

    /** log name for this log */
    private String name;

    /** stream where output of this log is sent to */
    private OutputStream logOut;

    /** string writer for writing message prefixes to log stream */
    private OutputStreamWriter logWriter;

    /** string buffer used for constructing log message prefixes */
    private StringBuffer buffer = new StringBuffer();

    /** stream used for buffering lines */
    private ByteArrayOutputStream bufOut;

    /**
     * Create a new LogStream object.  Since this only constructor is
     * private, users must have a LogStream created through the "log"
     * method.
     * @param name string identifying messages from this log
     * @out output stream that log messages will be sent to
     * @since 1.1
     * @deprecated no replacement
     */
    @Deprecated
    private LogStream(String name, OutputStream out)
    {
        super(new ByteArrayOutputStream());
        bufOut = (ByteArrayOutputStream) super.out;

        this.name = name;
        setOutputStream(out);
    }

    /**
     * Return the LogStream identified by the given name.  If
     * a log corresponding to "name" does not exist, a log using
     * the default stream is created.
     * @param name name identifying the desired LogStream
     * @return log associated with given name
     * @since 1.1
     * @deprecated no replacement
     */
    @Deprecated
    public static LogStream log(String name) {
        LogStream stream;
        synchronized (known) {
            stream = known.get(name);
            if (stream == null) {
                stream = new LogStream(name, defaultStream);
            }
            known.put(name, stream);
        }
        return stream;
    }

    /**
     * Return the current default stream for new logs.
     * @return default log stream
     * @see #setDefaultStream
     * @since 1.1
     * @deprecated no replacement
     */
    @Deprecated
    public static synchronized PrintStream getDefaultStream() {
        return defaultStream;
    }

    /**
     * Set the default stream for new logs.
     * @param newDefault new default log stream
     * @see #getDefaultStream
     * @since 1.1
     * @deprecated no replacement
     */
    @Deprecated
    public static synchronized void setDefaultStream(PrintStream newDefault) {
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();

        if (sm != null) {
            sm.checkPermission(
                new java.util.logging.LoggingPermission("control", null));
        }

        defaultStream = newDefault;
    }

    /**
     * Return the current stream to which output from this log is sent.
     * @return output stream for this log
     * @see #setOutputStream
     * @since 1.1
     * @deprecated no replacement
     */
    @Deprecated
    public synchronized OutputStream getOutputStream()
    {
        return logOut;
    }

    /**
     * Set the stream to which output from this log is sent.
     * @param out new output stream for this log
     * @see #getOutputStream
     * @since 1.1
     * @deprecated no replacement
     */
    @Deprecated
    public synchronized void setOutputStream(OutputStream out)
    {
        logOut = out;
        // Maintain an OutputStreamWriter with default CharToByteConvertor
        // (just like new PrintStre