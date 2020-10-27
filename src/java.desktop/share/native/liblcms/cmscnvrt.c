
/*
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

// This file is available under and governed by the GNU General Public
// License version 2 only, as published by the Free Software Foundation.
// However, the following notice accompanied the original version of this
// file:
//
//---------------------------------------------------------------------------------
//
//  Little Color Management System
//  Copyright (c) 1998-2022 Marti Maria Saguer
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the Software
// is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
//---------------------------------------------------------------------------------
//

#include "lcms2_internal.h"


// This is the default routine for ICC-style intents. A user may decide to override it by using a plugin.
// Supported intents are perceptual, relative colorimetric, saturation and ICC-absolute colorimetric
static
cmsPipeline* DefaultICCintents(cmsContext     ContextID,
                               cmsUInt32Number nProfiles,
                               cmsUInt32Number Intents[],
                               cmsHPROFILE     hProfiles[],
                               cmsBool         BPC[],
                               cmsFloat64Number AdaptationStates[],
                               cmsUInt32Number dwFlags);

//---------------------------------------------------------------------------------

// This is the entry for black-preserving K-only intents, which are non-ICC. Last profile have to be a output profile
// to do the trick (no devicelinks allowed at that position)
static
cmsPipeline*  BlackPreservingKOnlyIntents(cmsContext     ContextID,
                                          cmsUInt32Number nProfiles,
                                          cmsUInt32Number Intents[],
                                          cmsHPROFILE     hProfiles[],
                                          cmsBool         BPC[],
                                          cmsFloat64Number AdaptationStates[],
                                          cmsUInt32Number dwFlags);

//---------------------------------------------------------------------------------

// This is the entry for black-plane preserving, which are non-ICC. Again, Last profile have to be a output profile
// to do the trick (no devicelinks allowed at that position)
static
cmsPipeline*  BlackPreservingKPlaneIntents(cmsContext     ContextID,
                                           cmsUInt32Number nProfiles,
                                           cmsUInt32Number Intents[],
                                           cmsHPROFILE     hProfiles[],
                                           cmsBool         BPC[],
                                           cmsFloat64Number AdaptationStates[],
                                           cmsUInt32Number dwFlags);

//---------------------------------------------------------------------------------


// This is a structure holding implementations for all supported intents.
typedef struct _cms_intents_list {

    cmsUInt32Number Intent;
    char            Description[256];
    cmsIntentFn     Link;
    struct _cms_intents_list*  Next;

} cmsIntentsList;


// Built-in intents
static cmsIntentsList DefaultIntents[] = {

    { INTENT_PERCEPTUAL,                            "Perceptual",                                   DefaultICCintents,            &DefaultIntents[1] },
    { INTENT_RELATIVE_COLORIMETRIC,                 "Relative colorimetric",                        DefaultICCintents,            &DefaultIntents[2] },
    { INTENT_SATURATION,                            "Saturation",                                   DefaultICCintents,            &DefaultIntents[3] },
    { INTENT_ABSOLUTE_COLORIMETRIC,                 "Absolute colorimetric",                        DefaultICCintents,            &DefaultIntents[4] },
    { INTENT_PRESERVE_K_ONLY_PERCEPTUAL,            "Perceptual preserving black ink",              BlackPreservingKOnlyIntents,  &DefaultIntents[5] },
    { INTENT_PRESERVE_K_ONLY_RELATIVE_COLORIMETRIC, "Relative colorimetric preserving black ink",   BlackPreservingKOnlyIntents,  &DefaultIntents[6] },
    { INTENT_PRESERVE_K_ONLY_SATURATION,            "Saturation preserving black ink",              BlackPreservingKOnlyIntents,  &DefaultIntents[7] },
    { INTENT_PRESERVE_K_PLANE_PERCEPTUAL,           "Perceptual preserving black plane",            BlackPreservingKPlaneIntents, &DefaultIntents[8] },
    { INTENT_PRESERVE_K_PLANE_RELATIVE_COLORIMETRIC,"Relative colorimetric preserving black plane", BlackPreservingKPlaneIntents, &DefaultIntents[9] },
    { INTENT_PRESERVE_K_PLANE_SATURATION,           "Saturation preserving black plane",            BlackPreservingKPlaneIntents, NULL }
};


// A pointer to the beginning of the list
_cmsIntentsPluginChunkType _cmsIntentsPluginChunk = { NULL };

// Duplicates the zone of memory used by the plug-in in the new context
static
void DupPluginIntentsList(struct _cmsContext_struct* ctx,
                                               const struct _cmsContext_struct* src)
{
   _cmsIntentsPluginChunkType newHead = { NULL };
   cmsIntentsList*  entry;
   cmsIntentsList*  Anterior = NULL;
   _cmsIntentsPluginChunkType* head = (_cmsIntentsPluginChunkType*) src->chunks[IntentPlugin];

    // Walk the list copying all nodes
   for (entry = head->Intents;
        entry != NULL;
        entry = entry ->Next) {

            cmsIntentsList *newEntry = ( cmsIntentsList *) _cmsSubAllocDup(ctx ->MemPool, entry, sizeof(cmsIntentsList));

            if (newEntry == NULL)
                return;

            // We want to keep the linked list order, so this is a little bit tricky
            newEntry -> Next = NULL;
            if (Anterior)
                Anterior -> Next = newEntry;

            Anterior = newEntry;

            if (newHead.Intents == NULL)
                newHead.Intents = newEntry;
    }

  ctx ->chunks[IntentPlugin] = _cmsSubAllocDup(ctx->MemPool, &newHead, sizeof(_cmsIntentsPluginChunkType));
}

void  _cmsAllocIntentsPluginChunk(struct _cmsContext_struct* ctx,
                                         const struct _cmsContext_struct* src)
{
    if (src != NULL) {

        // Copy all linked list
        DupPluginIntentsList(ctx, src);
    }
    else {
        static _cmsIntentsPluginChunkType IntentsPluginChunkType = { NULL };
        ctx ->chunks[IntentPlugin] = _cmsSubAllocDup(ctx ->MemPool, &IntentsPluginChunkType, sizeof(_cmsIntentsPluginChunkType));
    }
}


// Search the list for a suitable intent. Returns NULL if not found
static
cmsIntentsList* SearchIntent(cmsContext ContextID, cmsUInt32Number Intent)
{
    _cmsIntentsPluginChunkType* ctx = ( _cmsIntentsPluginChunkType*) _cmsContextGetClientChunk(ContextID, IntentPlugin);
    cmsIntentsList* pt;

    for (pt = ctx -> Intents; pt != NULL; pt = pt -> Next)
        if (pt ->Intent == Intent) return pt;

    for (pt = DefaultIntents; pt != NULL; pt = pt -> Next)
        if (pt ->Intent == Intent) return pt;

    return NULL;
}

// Black point compensation. Implemented as a linear scaling in XYZ. Black points
// should come relative to the white point. Fills an matrix/offset element m
// which is organized as a 4x4 matrix.
static
void ComputeBlackPointCompensation(const cmsCIEXYZ* BlackPointIn,
                                   const cmsCIEXYZ* BlackPointOut,
                                   cmsMAT3* m, cmsVEC3* off)
{
  cmsFloat64Number ax, ay, az, bx, by, bz, tx, ty, tz;

   // Now we need to compute a matrix plus an offset m and of such of
   // [m]*bpin + off = bpout
   // [m]*D50  + off = D50
   //
   // This is a linear scaling in the form ax+b, where
   // a = (bpout - D50) / (bpin - D50)
   // b = - D50* (bpout - bpin) / (bpin - D50)
