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

#define SATmlib_u8(DST, val0)                                   \
  DST = ((mlib_s32)(val0 - sat) >> 24) ^ 0x80

#define SATmlib_s16(DST, val0)                                  \
  DST = ((mlib_s32)val0) >> 16

#define SATmlib_u16(DST, val0)                                  \
  DST = ((mlib_s32)(val0 - sat) >> 16) ^ 0x8000

#define SATmlib_s32(DST, val0)                                  \
  DST = val0

#else

#define SATmlib_u8(DST, val0)                                   \
  val0 -= sat;                                                  \
  if (val0 >= MLIB_S32_MAX)                                     \
    val0 = MLIB_S32_MAX;                                        \
  if (val0 <= MLIB_S32_MIN)                                     \
    val0 = MLIB_S32_MIN;                                        \
  DST = ((mlib_s32) val0 >> 24) ^ 0x80

#define SATmlib_s16(DST, val0)                                  \
  if (val0 >= MLIB_S32_MAX)                                     \
    val0 = MLIB_S32_MAX;                                        \
  if (val0 <= MLIB_S32_MIN)                                     \
    val0 = MLIB_S32_MIN;                                        \
  DST = (mlib_s32)val0 >> 16

#define SATmlib_u16(DST, val0)                                  \
  val0 -= sat;                                                  \
  if (val0 >= MLIB_S32_MAX)                                     \
    val0 = MLIB_S32_MAX;                                        \
  if (val0 <= MLIB_S32_MIN)                                     \
    val0 = MLIB_S32_MIN;                                        \
  DST = ((mlib_s32)val0 >> 16) ^ 0x8000

#define SATmlib_s32(DST, val0)                                  \
  if (val0 >= MLIB_S32_MAX)                                     \
    val0 = MLIB_S32_MAX;                                        \
  if (val0 <= MLIB_S32_MIN)                                     \
    val0 = MLIB_S32_MIN;                                        \
  DST = (mlib_s32)val0

#endif

/***************************************************************/
#define SATmlib_f32(DST, val0)                                  \
  DST = (mlib_f32)val0

/***************************************************************/
#define SATmlib_d64(DST, val0)                                  \
  DST = val0

/***************************************************************/
#define MLIB_EDGE_ZERO_LINE(TYPE, Left, Right)                  \
  dp = (TYPE*)data + channels * Left;                           \
  dstLineEnd  = (TYPE*)data + channels * Right;                 \
                                                                \
  for (; dp < dstLineEnd; dp++) {                               \
    *dp = zero;                                                 \
  }

/***************************************************************/
#define MLIB_EDGE_NEAREST_LINE(TYPE, Left, Right)               \
  dp = (TYPE*)data + channels * Left;                           \
  size = Right - Left;                                          \
                                                                \
  for (j = 0; j < size; j++) {                                  \
    ySrc = Y >> MLIB_SHIFT;                                     \
    xSrc = X >> MLIB_SHIFT;                                     \
    sp = (TYPE*)lineAddr[ySrc] + xSrc * channels;               \
                                                                \
    for (k = 0; k < channels; k++) dp[k] = sp[k];               \
                                                                \
    Y += dY;                                                    \
    X += dX;                                                    \
    dp += channels;                                             \
  }

/***************************************************************/
#define MLIB_EDGE_BL(TYPE, Left, Right)                                 \
  dp = (TYPE*)data + channels * Left;                                   \
  size = Right - Left;                                                  \
                                                                        \
  for (j = 0; j < size; j++) {                                          \
    ySrc = ((Y - 32768) >> MLIB_SHIFT);                                 \
    xSrc = ((X - 32768) >> MLIB_SHIFT);                                 \
                                                                        \
    t = ((X - 32768) & MLIB_MASK) * scale;                              \
    u = ((Y - 32768) & MLIB_MASK) * scale;                              \
                                                                        \
    xDelta = (((xSrc + 1 - srcWidth )) >> MLIB_SIGN_SHIFT) & channels;  \
    yDelta = (((ySrc + 1 - srcHeight)) >> MLIB_SIGN_SHIFT) & srcStride; \
                                                                        \
    xFlag = (xSrc >> (MLIB_SIGN_SHIFT - MLIB_SHIFT));                   \
    xSrc = xSrc + (1 & xFlag);                                          \
    xDelta = xDelta &~ xFlag;                                           \
                                                                        \
    yFlag = (ySrc >> (MLIB_SIGN_SHIFT - MLIB_SHIFT));                   \
    ySrc = ySrc + (1 & yFlag);                                          \
    yDelta = yDelta &~ yFlag;                                           \
                                                                        \
    sp = (TYPE*)lineAddr[ySrc] + xSrc * channels;                       \
                                                                        \
    for (k = 0; k < channels; k++) {                                    \
      a00  = D64##TYPE(sp[0]);                                          \
      a01  = D64##TYPE(sp[xDelta]);                                     \
      a10  = D64##TYPE(sp[yDelta]);                                     \
      a11  = D64##TYPE(sp[yDelta + xDelta]);                            \
      pix0 = (a00 * (1 - t) + a01 * t) * (1 - u) +                      \
             (a10 * (1 - t) + a11 * t) * u;                             \
                                                                        \
      dp[k] = (TYPE)pix0;                                               \
      sp++;                                                             \
    }                                                                   \
                                                                        \
    X += dX;                                                            \
    Y += dY;                                                            \
    dp += channels;                                                     \
  }

/***************************************************************/
#define GET_FLT_TBL(X, xf0, xf1, xf2, xf3)                      \
  filterpos = ((X - 32768) >> flt_shift) & flt_mask;            \
  fptr = (mlib_f32 *) ((mlib_u8 *)flt_tbl + filterpos);         \
                                                                \
  xf0 = fptr[0];                                                \
  xf1 = fptr[1];                                                \
  xf2 = fptr[2];                                                \
  xf3 = fptr[3]

/***************************************************************/
#define GET_FLT_BC(X, xf0, xf1, xf2, xf3)                       \
  dx = ((X - 32768) & MLIB_MASK) * scale;                       \
  dx_2  = 0.5 * dx;                                             \
  dx2   = dx * dx;                                              \
  dx3_2 = dx_2 * dx2;                                           \
  dx3_3 = 3.0 * dx3_2;                                          \
                                                                \
  xf0 = dx2 - dx3_2 - dx_2;                                     \
  xf1 = dx3_3 - 2.5 * dx2 + 1.0;                                \
  xf2 = 2.0 * dx2 - dx3_3 + dx_2;                               \
  xf3 = dx3_2 - 0.5 * dx2

/***************************************************************/
#define GET_FLT_BC2(X, xf0, xf1, xf2, xf3)                      \
  dx =  ((X - 32768) & MLIB_MASK) * scale;                      \
  dx2   = dx  * dx;                                             \
  dx3_2 = dx  * dx2;                                            \
  dx3_3 = 2.0 * dx2;                                            \
                                                                \
  xf0 = - dx3_2 + dx3_3 - dx;                                   \
  xf1 =   dx3_2 - dx3_3 + 1.0;                                  \
  xf2 = - dx3_2 + dx2   + dx;                                   \
  xf3 =   dx3_2 - dx2

/***************************************************************/
#define CALC_SRC_POS(X, Y, channels, srcStride)                                    \
  xSrc = ((X - 32768) >> MLIB_SHIFT);                                              \
  ySrc = ((Y - 32768) >> MLIB_SHIFT);                                              \
                                                                                   \
  xDelta0 = ((~((xSrc - 1) >> MLIB_SIGN_SHIFT)) & (- channels));                   \
  yDelta0 = ((~((ySrc - 1) >> MLIB_SIGN_SHIFT)) & (- srcStride));                  \
  xDelta1 = ((xSrc + 1 - srcWidth) >> MLIB_SIGN_SHIFT) & (channels);               \
  yDelta1 = ((ySrc + 1 - srcHeight) >> MLIB_SIGN_SHIFT) & (srcStride);             \
  xDelta2 = xDelta1 + (((xSrc + 2 - srcWidth) >> MLIB_SIGN_SHIFT) & (channels));   \
  yDelta2 = yDelta1 + (((ySrc + 2 - srcHeight) >> MLIB_SIGN_SHIFT) & (srcStride)); \
                                                                                   \
  xFlag = (xSrc >> (MLIB_SIGN_SHIFT - MLIB_SHIFT));                                \
  xSrc = xSrc + (1 & xFlag);                                                       \
  xDelta2 -= (xDelta1 & xFlag);                                                    \
  xDelta1 = (xDelta1 &~ xFlag);                                                    \
                                                                                   \
  yFlag = (ySrc >> (MLIB_SIGN_SHIFT - MLIB_SHIFT));                                \
  ySrc = ySrc + (1 & yFlag);                                                       \
  yDelta2  -= (yDelta1 & yFlag);                                                   \
  yDelta1 = yDelta1 &~ yFlag

/***************************************************************/
#define MLIB_EDGE_BC_LINE(TYPE, Left, Right, GET_FILTER)        \
  dp = (TYPE*)data + channels * Left;                           \
  size = Right - Left;                                          \
                                                                \
  for (j = 0; j < size; j++) {                                  \
    GET_FILTER(X, xf0, xf1, xf2, xf3);                          \
    GET_FILTER(Y, yf0, yf1, yf2, yf3);                          \
                                                                \
    CALC_SRC_POS(X, Y, channels, srcStride);                    \
                                                                \
    sp = (TYPE*)lineAddr[ySrc] + channels*xSrc;                 \
                                                                \
    for (k = 0; k < channels; k++) {                            \
      c0 = D64##TYPE(sp[yDelta0 + xDelta0]) * xf0 +             \
           D64##TYPE(sp[yDelta0          ]) * xf1 +             \
           D64##TYPE(sp[yDelta0 + xDelta1]) * xf2 +             \
           D64##TYPE(sp[yDelta0 + xDelta2]) * xf3;              \
                                                                \
      c1 = D64##TYPE(sp[xDelta0]) * xf0 +                       \
           D64##TYPE(sp[      0]) * xf1 +                       \
           D64##TYPE(sp[xDelta1]) * xf2 +                       \
           D64##TYPE(sp[xDelta2]) * xf3;                        \
                                                                \
      c2 = D64##TYPE(sp[yDelta1 + xDelta0]) * xf0 +             \
           D64##TYPE(sp[yDelta1          ]) * xf1 +             \
           D64##TYPE(sp[yDelta1 + xDelta1]) * xf2 +             \
           D64##TYPE(sp[yDelta1 + xDelta2]) * xf3;              \
                                                                \
      c3 = D64##TYPE(sp[yDelta2 + xDelta0]) * xf0 +             \
           D64##TYPE(sp[yDelta2          ]) * xf1 +             \
           D64##TYPE(sp[yDelta2 + xDelta1]) * xf2 +             \
           D64##TYPE(sp[yDelta2 + xDelta2]) * xf3;              \
                                                                \
      val0 = c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3;                 \
                                                                \
      SAT##TYPE(dp[k], val0);                                   \
                                                                \
      sp++;                                                     \
    }                                                           \
                                                                \
    X += dX;                                                    \
    Y += dY;                                                    \
    dp += channels;                                             \
  }

/***************************************************************/
#define MLIB_EDGE_BC_TBL(TYPE, Left, Right)                     \
  MLIB_EDGE_BC_LINE(TYPE, Left, Right, GET_FLT_TBL)

/***************************************************************/
#define MLIB_EDGE_BC(TYPE, Left, Right)                         \
  MLIB_EDGE_BC_LINE(TYPE, Left, Right, GET_FLT_BC)

/***************************************************************/
#define MLIB_EDGE_BC2(TYPE, Left, Right)                        \
  MLIB_EDGE_BC_LINE(TYPE, Left, Right, GET_FLT_BC2)

/***************************************************************/
#define MLIB_PROCESS_EDGES_ZERO(TYPE) {                         \
  TYPE *dp, *dstLineEnd;                                        \
                                                                \
  for (i = yStartE; i < yStart; i++) {                          \
    xLeftE  = leftEdgesE[i];                                    \
    xRightE = rightEdgesE[i] + 1;                               \
    data   += dstStride;                                        \
                                                                \
    MLIB_EDGE_ZERO_LINE(TYPE, xLeftE, xRightE);                 \
  }                                                             \
                                                                \
  for (; i <= yFinish; i++) {                                   \
    xLeftE  = leftEdgesE[i];                                    \
    xRightE = rightEdgesE[i] + 1;                               \
    xLeft   = leftEdges[i];                                     \
    xRight  = rightEdges[i] + 1;                                \
    data   += dstStride;                                        \
                                                                \
    if (xLeft < xRight) {                                       \
      MLIB_EDGE_ZERO_LINE(TYPE, xLeftE, xLeft);                 \
    } else {                                                    \
      xRight = xLeftE;                                          \
    }                 