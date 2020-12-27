/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static jdk.jpackage.internal.StandardBundlerParam.RESOURCE_DIR;
import jdk.jpackage.internal.resources.ResourceLocator;


/**
 * Resource file that may have the default value supplied by jpackage. It can be
 * overridden by a file from resource directory set with {@code --resource-dir}
 * jpackage parameter.
 *
 * Resource has default name and public name. Default name is the name of a file
 * in {@code jdk.jpackage.internal.resources} package that provides the default
 * value of the resource.
 *
 * Public name is a path relative to resource directory to a file with custom
 * value of the resource.
 *
 * Use #setPublicName to set the public name.
 *
 * If #setPublicName was not called, name of file passed in #saveToFile function
 * will be used as a public name.
 *
 * Use #setExternal to set arbitrary file as a source of resource. If non-null
 * value was passed in #setExternal call that value will be used as a path to file
 * to copy in the destination file passed in #saveToFile function call.
 */
final class OverridableResource {

    OverridableResource(String defaultName) {
        this.defaultName = defaultName;
        setSourceOrder(Source.values());
    }

    Path getResourceDir() {
        return resourceDir;
    }

    String getDefaultName() {
        return defaultName;
    }

    Path getPublicName() {
        return publicName;
    }

    Path getExternalPath() {
        return externalPath;
    }

    OverridableResource setSubstitutionData(Map<String, String> v) {
        if (v != null) {
            // Disconnect `v`
            substitutionData = new HashMap<>(v);
        } else {
            substitutionData = null;
        }
        return this;
    }

    OverridableResource addSubstitutionDataEntry(String key, String value) {
        var entry = Map.of(key, value);
        Optional.ofNullable(substitutionData).ifPresentOrElse(v -> v.putAll(
                entry), () -> setSubstitutionData(entry));
        return this;
    }

    OverridableResource setCategory(String v) {
        category = v;
        return this;
    }

    OverridableResource setResourceDir(Path v) {
        resourceDir = v;
        return this;
    }

    OverridableResource setResourceDir(File v) {
        return setResourceDir(toPath(v));
    }

    enum Source { External, ResourceDir, DefaultResource };

    OverridableResource setSourceOrder(Source... v) {
        sources = Stream.of(v)
                .map(source -> Map.entry(source, getHandler(source)))
                .toList();
        return this;
    }

    /**
     * Set name of file to look for in resource dir.
     *
     * @return this
     */
    OverridableResource setPublicName(Path v) {
        publicName = v;
        return this;
    }

    OverridableResource setPublicName(String v) {
        return setPublicName(Path.of(v));
    }

    /**
     * Set name of file to look for in resource dir to put in verbose log.
     *
     * @return this
     */
    OverridableResource setLogPublicName(Path v) {
        logPublicName = v;
        return this;
    }

    OverridableResource setLogPublicName(String v) {
        return setLogPublicName(Path.of(v));
    }

    OverridableResource setExternal(Path v) {
        externalPath = v;
        return this;
    }

    OverridableResource setExternal(File v) {
        return setExternal(toPath(v));
    }

    Source saveToStream(OutputStream dest) throws IOException {
        if (dest == null) {
            return sendToConsumer(null);
        }
        return sendToConsumer(new ResourceConsumer() {
            @Override
            public Path publicName() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void consume(InputStream in) throws IOException {
                in.transferTo(dest);
            }
        });
    }

    Source saveInFolder(Path folderPath) throws IOException {
        return saveToFile(folderPath.resolve(getPublicName()));
    }

    Source saveToFile(Path dest) throws IOException {
        if (d