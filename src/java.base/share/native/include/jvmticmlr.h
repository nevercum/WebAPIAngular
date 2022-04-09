/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * This header file defines the data structures sent by the VM
 * through the JVMTI CompiledMethodLoad callback function via the
 * "void * compile_info" parameter. The memory pointed to by the
 * compile_info parameter may not be referenced after returning from
 * the CompiledMethodLoad callback. These are VM implementation
 * specific data structures that may evolve in future releases. A
 * JVMTI agent should interpret a non-NULL compile_info as a pointer
 * to a region of memory containing a list of records. In a typical
 * usage scenario, a JVMTI agent would cast each record to a
 * jvmtiCompiledMethodLoadRecordHeader, a struct that represents
 * arbitrary information. This struct contains a kind field to indicate
 * the kind of information being passed, and a pointer to the next
 * record. If the kind field indicates inlining information, then the
 * agent would cast