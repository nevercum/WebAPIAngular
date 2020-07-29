/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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

/*
 */

/*
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 */

/*
 * Since JDK 1.5.0, this file no longer goes to runtime and is used at J2SE
 * build phase in order to create [Word|Line]BreakIteratorData_th files which
 * are used on runtime instead.
 */

package sun.text.resources.ext;

import java.util.ListResourceBundle;
import java.util.MissingResourceException;
import java.net.URL;

public class BreakIteratorRules_th extends ListResourceBundle {
    protected final Object[][] getContents() {
        return new Object[][] {
            { "WordBreakRules",
              // this rule breaks the iterator with mixed Thai and English
                "<dictionary>=[\u0e01-\u0e2e\u0e30-\u0e3a\u0e40-\u0e44\u0e47-\u0e4e];"

                + "<ignore>=[:Mn::Me::Cf:^<dictionary>];"
                + "<paiyannoi>=[\u0e2f];"
                + "<maiyamok>=[\u0e46];"
                + "<danda>=[\u0964\u0965];"
                + "<kanji>=[\u3005\u4e00-\u9fa5\uf900-\ufa2d];"
                + "<kata>=[\u30a1-\u30fa];"
                + "<hira>=[\u3041-\u3094];"
                + "<cjk-diacrit>=[\u3099-\u309c];"
     