/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * FUNCTION
 *      mlib_ImageAffineEdgeZero - implementation of MLIB_EDGE_DST_FILL_ZERO
 *                                 edge condition
 *      mlib_ImageAffineEdgeNearest - implementation of MLIB_EDGE_OP_NEAREST
 *                                    edge condition
 *      void mlib_ImageAffineEdgeExtend_BL - implementation of MLIB_EDGE_SRC_EXTEND
 *                                           edge condition for MLIB_BILINEAR filter
 *      void mlib_ImageAffineEdgeExtend_BC - implementation of MLIB_EDGE_SRC_EXTEND
 *                                           edge condition for MLIB_BICUBIC filter
 *      void mlib_ImageAffineEdgeExtend_BC2 - implementation of MLIB_EDGE_SRC_EXTEND
 *                                            edge condition for MLIB_BICUBIC2 filter
 *
 * DESCRIPTION
 *      mlib_ImageAffineEdgeZero:
 *         This function fills the edge pixels (i.e. thouse one which can not
 *         be interpolated with given resampling filter because their prototypes
 *         in the source image lie too close to the border) in the destination
 *         image with zeroes.
 *
 *      mlib_ImageAffineEdgeNearest:
 *         This function fills the edge pixels (i.e. thouse one which can not
 *         be interpolated with given resampling filter because their prototypes
 *         in the source image lie too close to the border) in the destination
 *         image according to the nearest neighbour interpolation.
 *
 *      mlib_ImageAffineEdgeExtend_BL:
 *         This function fills the edge pixels (i.e. thouse one which can not
 *         be interpolated with given resampling filter because their prototypes
 *         in the source image lie too close to the border) in the destination
 *         image according to the bilinear interpolation with border pixels extend
 *         of source image.
 *
 *      mlib_ImageAffineEdgeExtend_BC:
 *         This function fills the edge pixels (i.e. thouse one which can not
 *         be interpolated with given resampling filter because their prototypes
 *         in the source image lie too close to the border) in the destination
 *         image according to the bicubic interpolation with border pixels extend
 *         of source image.
 *
 *      mlib_ImageAffineEdgeExtend_BC2:
 *         This function fills the edge pixels (i.e. thouse one which can not
 *         be interpolated with given resampling filter because their prototypes
 *         in the source image lie too close to the border) in the destination
 *         image according to the bicubic2 interpolation with border pixels extend
 *         of source image.
 */

#include "mlib_image.h"
#include "mlib_ImageAffine.h"

/***************************************************************/
#define FLT_SHIFT_U8  4
#define FLT_MASK_U8   (((1 << 8) - 1) << 4)
#define FLT_SHIFT_S16 3
#define FLT_MASK_S16  (((1 << 9) - 1) << 4)

#define MLIB_SIGN_SHIFT 31

/***************************************************************/
#define D64mlib_u8(X)   mlib_U82D64[X]
#define D64mlib_s16(X)  ((mlib_d64)(X))
#define D64mlib_u16(X)  ((mlib_d64)(X))
#define D64mlib_s32(X)  ((mlib_d64)(X))
#define D64mlib_f32(X)  ((mlib_d64)(X))
#define D64mlib_d64(X)  ((mlib_d64)(X))

/***************************************************************/
#ifdef MLIB_USE_FTOI_CLAMPING

#define SA