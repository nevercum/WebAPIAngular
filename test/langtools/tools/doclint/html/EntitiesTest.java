/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * @test
 * @bug 8006263
 * @summary Supplementary test cases needed for doclint
 * @library ..
 * @modules jdk.javadoc/jdk.javadoc.internal.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-html EntitiesTest.java
 * @run main DocLintTester -Xmsgs:html -ref EntitiesTest.out EntitiesTest.java
 */

/** */
class EntitiesTest {

    /**
     * &#32;  &#x20;
     * &#2126; &#x84e; &#x84E;
     */
    void range_test() { }

    /**
    * &nbsp; &#160;
    * &iexcl; &#161;
    * &cent; &#162;
    * &pound; &#163;
    * &curren; &#164;
    * &yen; &#165;
    * &brvbar; &#166;
    * &sect; &#167;
    * &uml; &#168;
    * &copy; &#169;
    * &ordf; &#170;
    * &laquo; &#171;
    * &not; &#172;
    * &shy; &#173;
    * &reg; &#174;
    * &macr; &#175;
    * &deg; &#176;
    * &plusmn; &#177;
    * &sup2; &#178;
    * &sup3; &#179;
    * &acute; &#180;
    * &micro; &#181;
    * &para; &#182;
    * &middot; &#183;
    * &cedil; &#184;
    * &sup1; &#185;
    * &ordm; &#186;
    * &raquo; &#187;
    * &frac14; &#188;
    * &frac12; &#189;
    * &frac34; &#190;
    * &iquest; &#191;
    * &Agrave; &#192;
    * &Aacute; &#193;
    * &Acirc; &#194;
    * &Atilde; &#195;
    * &Auml; &#196;
    * &Aring; &#197;
    * &AElig; &#198;
    * &Ccedi