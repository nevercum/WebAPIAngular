/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef GraphicsPrimitiveMgr_h_Included
#define GraphicsPrimitiveMgr_h_Included

#ifdef __cplusplus
extern "C" {
#endif

#include <stddef.h>
#include "jni.h"

#include "java_awt_AlphaComposite.h"

#include "SurfaceData.h"
#include "SpanIterator.h"

#include "j2d_md.h"

#include "AlphaMath.h"
#include "GlyphImageRef.h"

/*
 * This structure contains all of the information about a particular
 * type of GraphicsPrimitive, such as a FillRect, a MaskFill, or a Blit.
 *
 * A global collection of these structures is declared and initialized
 * to contain the necessary Java (JNI) information so that appropriate
 * Java GraphicsPrimitive objects can be quickly constructed for a set
 * of native loops simply by referencing the necessary entry from that
 * collection for the type of primitive being registered.
 *
 * See PrimitiveTypes.{Blit,BlitBg,FillRect,...} below.
 */
typedef struct _PrimitiveType {
    char                *ClassName;
    jint                srcflags;
    jint                dstflags;
    jclass              ClassObject;
    jmethodID           Constructor;
} PrimitiveType;

/* The integer constants to identify the compositing rule being defined. */
#define RULE_Xor        (java_awt_AlphaComposite_MIN_RULE - 1)
#define RULE_Clear      java_awt_AlphaComposite_CLEAR
#define RULE_Src        java_awt_AlphaComposite_SRC
#define RULE_SrcOver    java_awt_AlphaComposite_SRC_OVER
#define RULE_DstOver    java_awt_AlphaComposite_DST_OVER
#define RULE_SrcIn      java_awt_AlphaComposite_SRC_IN
#define RULE_DstIn      java_awt_AlphaComposite_DST_IN
#define RULE_SrcOut     java_awt_AlphaComposite_SRC_OUT
#define RULE_DstOut     java_awt_AlphaComposite_DST_OUT

/*
 * This structure holds the information retrieved from a Java
 * Composite object for easy transfer to various C functions
 * that implement the inner loop for a native primitive.
 *
 * Currently only AlphaComposite and XORComposite are supported.
 */
typedef struct _CompositeInfo {
    jint        rule;           /* See RULE_* constants above */
    union {
        jfloat  extraAlpha;     /* from AlphaComposite */
        jint    xorPixel;       /* from XORComposite */
    } details;
    juint       alphaMask;      /* from XORComposite */
} CompositeInfo;

/*
 * This structure is the common header for the two native structures
 * that hold information about a particular SurfaceType or CompositeType.
 *
 * A global collection of these structures is declared and initialized
 * to contain the necessary Java (JNI) information so that appropriate
 * Java GraphicsPrimitive objects can be quickly constructed for a set
 * of native loops simply by referencing the necessary entry from that
 * collection for the type of composite or surface being implemented.
 *
 * See SurfaceTypes.{OpaqueColor,IntArgb,ByteGray,...} below.
 * See CompositeTypes.{Xor,AnyAlpha,...} below.
 */
typedef struct _SurfCompHdr {
    char                *Name;
    jobject             Object;
} SurfCompHdr;

/*
 * The definitions for the SurfaceType structure described above.
 */

/*
 * The signature for a function that returns the specific integer
 * format pixel for a given ARGB color value for a particular
 * SurfaceType implementation.
 * This function is valid only after GetRasInfo call for the
 * associated surface.
 */
typedef jint (PixelForFunc)(SurfaceDataRasInfo *pRasInfo, jint rgb);

/*
 * The additional information needed to manipulate a surface:
 * - The pixelFor function for translating ARGB values.
 *   Valid only after GetRasInfo call for this surface.
 * - The additional flags needed when reading from this surface.
 * - The additional flags needed when writing to this surface.
 */
typedef struct _SurfaceType {
    SurfCompHdr         hdr;
    PixelForFunc        *pixelFor;
    jint                readflags;
    jint                writeflags;
} SurfaceType;

/*
 * The definitions for the CompositeType structure described above.
 */

/*
 * The signature for a function that fills in a CompositeInfo
 * structure from the information present in a given Java Composite
 * object.
 */
typedef void (JNICALL CompInfoFunc)(JNIEnv *env,
                                    CompositeInfo *pCompInfo,
                                    jobject Composite);

/*
 * The additional information needed to implement a primitive that
 * performs a particular composite operation:
 * - The getCompInfo function for filling in a CompositeInfo structure.
 * - The additional flags needed for locking the destination surface.
 */
typedef struct _CompositeType {
    SurfCompHdr         hdr;
    CompInfoFunc        *getCompInfo;
    jint                dstflags;
} CompositeType;

/*
 * The signature of the native functions that register a set of
 * related native GraphicsPrimitive functions.
 */
typedef jboolean (RegisterFunc)(JNIEnv *env);

struct _NativePrimitive;        /* forward reference for function typedefs */

/*
 * This empty function signature represents an "old pre-ANSI style"
 * function declaration which makes no claims about the argument