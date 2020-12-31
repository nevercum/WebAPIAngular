/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @summary converted from VM Testbase nsk/jdi/LocalVariable/typeName/typename001.
 * VM Testbase keywords: [jpda, jdi]
 * VM Testbase readme:
 * DESCRIPTION:
 *     The test for the implementation of an object of the type
 *     LocalVariable.
 *     The test checks up that a result of the method
 *     com.sun.jdi.LocalVariable.typeName()
 *     complies with its spec:
 *     public java.lang.String typeName()
 *     Returns: a text representation of the declared type of this variable.
 *     when the declared type is one of primitive types and
 *     a tested program is prepared with full information (see COMMENTS),
 *     hence, AbsentInformationException is not expected to happen.
 *     The test works as follows:
 *     The debugger program - nsk.jdi.LocalVariable.typeName.typename001;
 *     the debuggee program - nsk.jdi.LocalVariable.typeName.typename001