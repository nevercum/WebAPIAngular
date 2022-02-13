
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

package jdk.javadoc.internal.doclets.formats.html.markup;

import jdk.javadoc.internal.doclets.toolkit.Content;

import java.io.IOException;
import java.io.Writer;

/**
 * A representation of HTML entities.
 */
public class Entity extends Content {
    public static final Entity LESS_THAN = new Entity("&lt;");
    public static final Entity GREATER_THAN = new Entity("&gt;");
    public static final Entity AMPERSAND = new Entity("&amp;");
    public static final Entity NO_BREAK_SPACE = new Entity("&nbsp;");

    public final String text;

    /**
     * Creates an entity with a given name or numeric value.
     *
     * @param name the name, or numeric value
     * @return the entity
     */
    public static Entity of(CharSequence name) {
        return new Entity("&" + name + ";");
    }

    private Entity(String text) {
        this.text = text;
    }

    @Override
    public boolean write(Writer writer, String newline, boolean atNewline) throws IOException {
        writer.write(text);
        return false;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public int charCount() {
        return 1;
    }