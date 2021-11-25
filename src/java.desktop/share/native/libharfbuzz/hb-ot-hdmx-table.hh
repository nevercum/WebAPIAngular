/*
 * Copyright © 2018  Google, Inc.
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
 * Google Author(s): Garret Rieger
 */

#ifndef HB_OT_HDMX_TABLE_HH
#define HB_OT_HDMX_TABLE_HH

#include "hb-open-type.hh"

/*
 * hdmx -- Horizontal Device Metrics
 * https://docs.microsoft.com/en-us/typography/opentype/spec/hdmx
 */
#define HB_OT_TAG_hdmx HB_TAG('h','d','m','x')


namespace OT {


struct DeviceRecord
{
  static unsigned int get_size (unsigned count)
  { return hb_ceil_to_4 (min_size + count * HBUINT8::static_size); }

  template<typename Iterator,
           hb_requires (hb_is_iterator (Iterator))>
  bool serialize (hb_serialize_context_t *c, unsigned pixelSize, Iterator it)
  {
    TRACE_SERIALIZE (this);

    unsigned length = it.len ();

    if (unlikely (!c->extend (this, length)))  return_trace (false);

    this->pixelSize = pixelSize;
    this->maxWidth =
    + it
    | hb_reduce (hb_max, 0u);

    + it
    | hb_sink (widthsZ.as_array (length));

    return_trace (true);
  }

  bool sanitize (hb_sanitize_context_t *c, unsigned sizeDeviceRecord) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this) &&
                          c->check_range (this, sizeDeviceRecord)));
  }

  HBUINT8                       pixelSize;      /* Pixel size for following widths (as ppem). */
  HBUINT8                       maxWidth;       /* Maximum width. */
  UnsizedArrayOf<HBUINT8>       widthsZ;        /* Array of widths (numGlyphs is fro