/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>

#include <jvmti.h>
#include "agent_common.h"

#include "nsk_tools.h"
#include "JVMTITools.h"
#include "jvmti_tools.h"

extern "C" {

#define PASSED  0
#define STATUS_FAILED  2

/* the highly recommended system properties are below */
#define PROP_NUM 6
static const char *expected_props[PROP_NUM] = {
    "java.vm.vendor",
    "java.vm.version",
    "java.vm.name",
    "java.vm.info",
    "java.library.path",
    "java.class.path"
};

static jvmtiEventCallbacks callbacks;
static jint result = PASSED;

static int findProp(char *prop) {
    int i;

    for (i=0; i<PROP_NUM; i++) {
        if (strcmp(expected_props[i], prop) == 0) {
            NSK_DISPLAY1("CHECK PASSED: found highly recommended system property \"%s\" as expected\n",
                expected_props[i]);
            return 1; /* the property 