/*
 * Copyright © 2009  Red Hat, Inc.
 *
 *  This is part of HarfBuzz, a text shaping library.
 *
 * Permission is hereby granted, without written agreement and without
 * license or royalty fees, to use, copy, modify, and distribute this
 * software and its documentation for any purpose, provided that the
 * above copyright notice and the following two paragraphs appear in
 * all copies of this software.
 *
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE TO ANY PARTY FOR
 * DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
 * ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN
 * IF THE COPYRIGHT HOLDER HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 *
 * THE COPYRIGHT HOLDER SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE PROVIDED HEREUNDER IS
 * ON AN "AS IS" BASIS, AND THE COPYRIGHT HOLDER HAS NO OBLIGATION TO
 * PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *
 * Red Hat Author(s): Behdad Esfahbod
 */

#if !defined(HB_H_IN) && !defined(HB_NO_SINGLE_HEADER_ERROR)
#error "Include <hb.h> instead."
#endif

#ifndef HB_FONT_H
#define HB_FONT_H

#include "hb-common.h"
#include "hb-face.h"
#include "hb-draw.h"

HB_BEGIN_DECLS

/**
 * hb_font_t:
 *
 * Data type for holding fonts.
 *
 */
typedef struct hb_font_t hb_font_t;


/*
 * hb_font_funcs_t
 */

/**
 * hb_font_funcs_t:
 *
 * Data type containing a set of virtual methods used for
 * working on #hb_font_t font objects.
 *
 * HarfBuzz provides a lightweight default function for each of
 * the methods in #hb_font_funcs_t. Client programs can implement
 * their own replacements for the individual font functions, as
 * needed, and replace the default by calling the setter for a
 * method.
 *
 **/
typedef struct hb_font_funcs_t hb_font_funcs_t;

HB_EXTERN hb_font_funcs_t *
hb_font_funcs_create (void);

HB_EXTERN hb_font_funcs_t *
hb_font_funcs_get_empty (void);

HB_EXTERN hb_font_funcs_t *
hb_font_funcs_reference (hb_font_funcs_t *ffuncs);

HB_EXTERN void
hb_font_funcs_destroy (hb_font_funcs_t *ffuncs);

HB_EXTERN hb_bool_t
hb_font_funcs_set_user_data (hb_font_funcs_t    *ffuncs,
                             hb_user_data_key_t *key,
                             void *              data,
                             hb_destroy_func_t   destroy,
                             hb_bool_t           replace);


HB_EXTERN void *
hb_font_funcs_get_user_data (hb_font_funcs_t    *ffuncs,
                             hb_user_data_key_t *key);


HB_EXTERN void
hb_font_funcs_make_immutable (hb_font_funcs_t *ffuncs);

HB_EXTERN hb_bool_t
hb_font_funcs_is_immutable (hb_font_funcs_t *ffuncs);


/* font and glyph extents */

/**
 * hb_font_extents_t:
 * @ascender: The height of typographic ascenders.
 * @descender: The depth of typographic descenders.
 * @line_gap: The suggested line-spacing gap.
 *
 * Font-wide extent values, measured in font units.
 *
 * Note that typically @ascender is positive and @descender
 * negative, in coordinate systems that grow up.
 **/
typedef struct hb_font_extents_t {
  hb_position_t ascender;
  hb_position_t descender;
  hb_position_t line_gap;
  /*< private >*/
  hb_position_t reserved9;
  hb_position_t reserved8;
  hb_position_t reserved7;
  hb_position_t reserved6;
  hb_position_t reserved5;
  hb_position_t reserved4;
  hb_position_t reserved3;
  hb_position_t res