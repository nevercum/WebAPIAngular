/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.httpserver.simpleserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpHandlers;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A basic HTTP file server handler for static content.
 *
 * <p> Must be given an absolute pathname to the directory to be served.
 * Supports only HEAD and GET requests. Directory listings and files can be
 * served, content types are supported on a best-guess basis.
 */
public final class FileServerHandler implements HttpHandler {

    private static final List<String> SUPPORTED_METHODS = List.of("HEAD", "GET");
    private static final List<String> UNSUPPORTED_METHODS =
            List.of("CONNECT", "DELETE", "OPTIONS", "PATCH", "POST", "PUT", "TRACE");

    private final Path root;
    private final UnaryOperator<String> mimeTable;
    private final Logger logger;

    private FileServerHandler(Path root, UnaryOperator<String> mimeTable) {
        root = root.normalize();

        @SuppressWarnings("removal")
        var securityManager = System.getSecurityManager();
        if (securityManager != null)
            securityManager.checkRead(pathForSecurityCheck(root.toString()));

        if (!Files.exists(root))
            throw new IllegalArgumentException("Path does not exist: " + root);
        if (!root.isAbsolute())
            throw new IllegalArgumentException("Path is not absolute: " + root);
        if (!Files.isDirectory(root))
            throw new IllegalArgumentException("Path is not a directory: " + root);
        if (!Files.isReadable(root))
            throw new IllegalArgumentException("Path is not readable: " + root);
        this.root = root;
        this.mimeTable = mimeTable;
        this.logger = System.getLogger("com.sun.net.httpserver");
    }

    private static String pathForSecurityCheck(String path) {
        var separator = String.valueOf(File.separatorChar);
        return path.endsWith(separator) ? (path + "-") : (path + separator + "-");
    }

    private static final HttpHandler NOT_IMPLEMENTED_HANDLER =
            HttpHandlers.of(501, Headers.of(), "");

    private static final HttpHandler METHOD_NOT_ALLOWED_HANDLER =
            HttpHandlers.of(405, Headers.of("Allow", "HEAD, GET"), "");

    public static HttpHandler create(Path root, UnaryOperator<String> mimeTable) {
        var fallbackHandler = HttpHandlers.handleOrElse(
                r -> UNSUPPORTED_METHODS.contains(r.getRequestMethod()),
                METHOD_NOT_ALLOWED_HANDLER,
                NOT_IMPLEMENTED_HANDLER);
        return HttpHandlers.handleOrElse(
                r -> SUPPORTED_METHODS.contains(r.getRequestMethod()),
                new FileServerHandler(root, mimeTable), fallbackHandler);
    }

    private void handleHEAD(HttpExchange exchange, Path path) throws IOException {
        handleSupportedMethod(exchange, path, false);
    }

    private void handleGET(HttpExchange exchange, Path path) throws IOException {
        handleSupportedMethod(exchange, path, true);
    }

    private void handleSupportedMethod(HttpExchange exchange, Path path, boolean writeBody)
        throws IOException {
        if (Files.isDirectory(path)) {
            if (missingSlash(exchange)) {
                handleMovedPermanently(exchange);
                return;
            }
            if (indexFile(path) != null) {
                serveFile(exchange, indexFile(path), writeBody);
            } e