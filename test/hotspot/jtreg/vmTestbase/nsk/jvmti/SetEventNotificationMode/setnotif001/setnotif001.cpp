/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include "jvmti.h"
#include "agent_common.h"
#include "JVMTITools.h"

extern "C" {


#define PASSED 0
#define STATUS_FAILED 2
#define SCALE_SIZE (JVMTI_MAX_EVENT_TYPE_VAL + 1)

static jvmtiEnv *jvmti = NULL;
static jvmtiCapabilities caps;
static jvmtiEventCallbacks callbacks;
static jrawMonitorID access_lock;
static jobject notifyFramePopThread = NULL;
static jint result = PASSED;
static jboolean printdump = JNI_FALSE;
static int flag = 0;
static unsigned char enbl_scale[SCALE_SIZE];
static unsigned char ev_scale[SCALE_SIZE];

void mark(jvmtiEnv *jvmti_env, jvmtiEvent kind) {
    jvmtiError err;

    if (printdump == JNI_TRUE) {
        printf(">>> catching %s\n", TranslateEvent(kind));
    }
    err = jvmti_env->RawMonitorEnter(access_lock);
    if (err != JVMTI_ERROR_NONE) {
        printf("(RawMonitorEnter) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }
    if (enbl_scale[kind] != 1) {
        printf("Wrong notification: event %s (%d) has not been enabled\n",
               TranslateEvent(kind), kind);
        result = STATUS_FAILED;
    }
    ev_scale[kind] = 1;
    err = jvmti_env->RawMonitorExit(access_lock);
    if (err != JVMTI_ERROR_NONE) {
        printf("(R