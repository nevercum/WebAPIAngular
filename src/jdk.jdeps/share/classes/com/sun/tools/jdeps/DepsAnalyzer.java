/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.jdeps;

import com.sun.tools.classfile.Dependency.Location;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sun.tools.jdeps.Analyzer.Type.CLASS;
import static com.sun.tools.jdeps.Analyzer.Type.VERBOSE;
import static com.sun.tools.jdeps.Module.trace;
import static java.util.stream.Collectors.*;

/**
 * Dependency Analyzer.
 *
 * Type of filters:
 * source filter: -include <pattern>
 * target filter: -package, -regex, --require
 *
 * The initial archive set for analysis includes
 * 1. archives specified in the command line arguments
 * 2. observable modules matching the source filter
 * 3. classpath archives matching the source filter or target filter
 * 4. --add-modules and -m root modules
 */
public class DepsAnalyzer {
    final JdepsConfiguration configuration;
    final JdepsFilter filter;
    final JdepsWriter writer;
    final Analyzer.Type verbose;
    final boolean apiOnly;

    final DependencyFinder finder;
    final Analyzer analyzer;
    final List<Archive> rootArchives = new ArrayList<>();

    // parsed archives
    final Set<Archive> archives = new LinkedHashSet<>();

    public DepsAnalyzer(JdepsConfiguration config,
                        JdepsFilter filter,
                        JdepsWriter writer,
                        Analyzer.Type verbose,
                        boolean apiOnly) {
        this.configuration = config;
        this.filter = filter;
        this.writer = writer;
        this.verbose = verbose;
        this.apiOnly = apiOnly;

        this.finder = new DependencyFinder(config, filter);
        this.analyzer = new Analyzer(configuration, verbose, filter);

        // determine initial archives to be analyzed
        this.rootArchives.addAll(configuration.initialArchives());

        // if -include pattern is specified, add the matching archives on
        // classpath to the root archives
        if (filter.hasIncludePattern() || filter.hasTargetFilter()) {
            configuration.getModules().values().stream()
                .filter(source -> include(source) && filter.matches(source))
                .forEach(this.rootArchives::add);
        }

        // class path archives
        configuration.classPathArchives().stream()
            .filter(filter::matches)
            .forEach(this.rootArchives::add);

        // Include the root modules for analysis
        this.rootArchives.addAll(configuration.rootModules());

        trace("analyze root archives: %s%n", this.rootArchives);
    }

    /*
     * Perform runtime dependency analysis
     */
    public boolean run() throws IOException {
        return run(false, 1);
    }

    /**
     * Perform compile-time view or run-time view dependency analysis.
     *
     * @param compileTimeView
     * @param maxDepth  depth of recursive analysis.  depth == 0 if -R is set
     */
    public boolean run(boolean compileTimeView, int maxDepth) throws IOException {
        try {
            // parse each packaged module or classpath archive
            if (apiOnly) {
                finder.parseExportedAPIs(rootArchives.stream());
            } else {
                finder.parse(rootArchives.stream());
            }
            archives.addAll(rootArchives);

            int depth = maxDepth > 0 ? maxDepth : Integer.MAX_VALUE;

            // transitive analysis
            if (depth > 1) {
                if (compileTimeView)
                    transitiveArchiveDeps(depth-1);
                else
         