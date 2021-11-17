/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.SimpleTypeVisitor9;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.toolkit.util.SummaryAPIListBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

/**
 * Centralized constants and factory methods for HTML ids.
 *
 * <p>To ensure consistency, these constants and methods should be used
 * both when declaring ids (for example, {@code HtmlTree.setId})
 * and creating references (for example, {@code Links.createLink}).
 *
 * <p>Most ids are mostly for internal use within the pages of a documentation
 * bundle. However, the ids for member declarations may be referred to
 * from other documentation using {@code {@link}}, and should not be
 * changed without due consideration for the compatibility impact.
 *
 * <p>The use of punctuating characters is inconsistent and could be improved.
 *
 * <p>Constants and methods are {@code static} where possible.
 * However, some methods require access to {@code utils} and are
 * better provided as instance methods.
 */
public class HtmlIds {
    private final HtmlConfiguration configuration;
    private final Utils utils;

    static final HtmlId ALL_CLASSES_TABLE = HtmlId.of("all-classes-table");
    static final HtmlId ALL_MODULES_TABLE = HtmlId.of("all-modules-table");
    static final HtmlId ALL_PACKAGES_TABLE = HtmlId.of("all-packages-table");
    static final HtmlId ANNOTATION_TYPE_ELEMENT_DETAIL = HtmlId.of("annotation-interface-element-detail");
    static final HtmlId ANNOTATION_TYPE_OPTIONAL_ELEMENT_SUMMARY = HtmlId.of("annotation-interface-optional-element-summary");
    static final HtmlId ANNOTATION_TYPE_REQUIRED_ELEMENT_SUMMARY = HtmlId.of("annotation-interface-required-element-summary");
    static final HtmlId CLASS_DESCRIPTION = HtmlId.of("class-description");
    static final HtmlId CLASS_SUMMARY = HtmlId.of("class-summary");
    static final HtmlId CONSTRUCTOR_DETAIL = HtmlId.of("constructor-detail");
    static final HtmlId CONSTRUCTOR_SUMMARY = HtmlId.of("constructor-summary");
    static final HtmlId ENUM_CONSTANT_DETAIL = HtmlId.of("enum-constant-detail");
    static final HtmlId ENUM_CONSTANT_SUMMARY = HtmlId.of("enum-constant-summary");
    static final HtmlId EXTERNAL_SPECS = HtmlId.of("external-specs");
    static final HtmlId FIELD_DETAIL = HtmlId.of("field-detail");
    static final HtmlId FIELD_SUMMARY = HtmlId.of("field-summary");
    static final HtmlId FOR_REMOVAL = HtmlId.of("for-removal");
    static final HtmlId HELP_NAVIGATION = HtmlId.of("help-navigation");
    static final HtmlId HELP_PAGES = HtmlId.of("help-pages");
    static final HtmlId METHOD_DETAIL = HtmlId.of("method-detail");
    static final HtmlId METHOD_SUMMARY = HtmlId.of("method-summary");
    static final HtmlId METHOD_SUMMARY_TABLE = HtmlId.of("method-summary-table");
    static final HtmlId MODULES = HtmlId.of("modules-summary");
    static final HtmlId MODULE_DESCRIPTION = HtmlId.of("module-description");
    static final HtmlId NAVBAR_SUB_LIST = HtmlId.of("navbar-sub-list");
    static final HtmlId NAVBAR_TOGGLE_BUTTON = HtmlId.of("navbar-toggle-button");
    static final HtmlId NAVBAR_TOP = HtmlId.of("navbar-top");
    static final HtmlId NAVBAR_TOP_FIRSTROW = HtmlId.of("navbar-top-firstrow");
    static final HtmlId NESTED_CLASS_SUMMARY = HtmlId.of("nested-class-summary");
    static final HtmlId PACKAGES = HtmlId.of("packages-summary");
    static final HtmlId PACKAGE_DESCRIPTION = HtmlId.of("package-description");
    static final HtmlId PACKAGE_SUMMARY_TABLE = HtmlId.of("package-summary-table");
    static final HtmlId PROPERTY_DETAIL = HtmlId.of("property-detail");
    static final HtmlId PROPERTY_SUMMARY = HtmlId.of("property-summary");
    static final HtmlId RELATED_PACKAGE_SUMMARY = HtmlId.of("related-package-summary");
    static final HtmlId RESET_BUTTON = HtmlId.of("reset-button");
    static final HtmlId SEARCH_INPUT = HtmlId.of("search-input");
    static final HtmlId SERVICES = HtmlId.of("services-summary");
    static final HtmlId SKIP_NAVBAR_TOP = HtmlId.of("skip-navbar-top");
    static final HtmlId UNNAMED_PACKAGE_ANCHOR = HtmlId.of("unnamed-package");

    private static final String ENUM_CO