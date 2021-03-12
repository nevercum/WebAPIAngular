/*
 * Copyright © 1998-2004  David Turner and Werner Lemberg
 * Copyright © 2004,2007,2009,2010  Red Hat, Inc.
 * Copyright © 2011,2012  Google, Inc.
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
 * Red Hat Author(s): Owen Taylor, Behdad Esfahbod
 * Google Author(s): Behdad Esfahbod
 */

#ifndef HB_BUFFER_HH
#define HB_BUFFER_HH

#include "hb.hh"
#include "hb-unicode.hh"


#ifndef HB_BUFFER_MAX_LEN_FACTOR
#define HB_BUFFER_MAX_LEN_FACTOR 64
#endif
#ifndef HB_BUFFER_MAX_LEN_MIN
#define HB_BUFFER_MAX_LEN_MIN 16384
#endif
#ifndef HB_BUFFER_MAX_LEN_DEFAULT
#define HB_BUFFER_MAX_LEN_DEFAULT 0x3FFFFFFF /* Shaping more than a billion chars? Let us know! */
#endif

#ifndef HB_BUFFER_MAX_OPS_FACTOR
#define HB_BUFFER_MAX_OPS_FACTOR 1024
#endif
#ifndef HB_BUFFER_MAX_OPS_MIN
#define HB_BUFFER_MAX_OPS_MIN 16384
#endif
#ifndef HB_BUFFER_MAX_OPS_DEFAULT
#define HB_BUFFER_MAX_OPS_DEFAULT 0x1FFFFFFF /* Shaping more than a billion operations? Let us know! */
#endif

static_assert ((sizeof (hb_glyph_info_t) == 20), "");
static_assert ((sizeof (hb_glyph_info_t) == sizeof (hb_glyph_position_t)), "");

HB_MARK_AS_FLAG_T (hb_glyph_flags_t);
HB_MARK_AS_FLAG_T (hb_buffer_flags_t);
HB_MARK_AS_FLAG_T (hb_buffer_serialize_flags_t);
HB_MARK_AS_FLAG_T (hb_buffer_diff_flags_t);

enum hb_buffer_scratch_flags_t {
  HB_BUFFER_SCRATCH_FLAG_DEFAULT                        = 0x00000000u,
  HB_BUFFER_SCRATCH_FLAG_HAS_NON_ASCII                  = 0x00000001u,
  HB_BUFFER_SCRATCH_FLAG_HAS_DEFAULT_IGNORABLES         = 0x00000002u,
  HB_BUFFER_SCRATCH_FLAG_HAS_SPACE_FALLBACK             = 0x00000004u,
  HB_BUFFER_SCRATCH_FLAG_HAS_GPOS_ATTACHMENT            = 0x00000008u,
  HB_BUFFER_SCRATCH_FLAG_HAS_CGJ                        = 0x00000010u,
  HB_BUFFER_SCRATCH_FLAG_HAS_GLYPH_FLAGS                = 0x00000020u,
  HB_BUFFER_SCRATCH_FLAG_HAS_BROKEN_SYLLABLE            = 0x00000040u,

  /* Reserved for shapers' internal use. */
  HB_BUFFER_SCRATCH_FLAG_SHAPER0                        = 0x01000000u,
  HB_BUFFER_SCRATCH_FLAG_SHAPER1                        = 0x02000000u,
  HB_BUFFER_SCRATCH_FLAG_SHAPER2                        = 0x04000000u,
  HB_BUFFER_SCRATCH_FLAG_SHAPER3                        = 0x08000000u,
};
HB_MARK_AS_FLAG_T (hb_buffer_scratch_flags_t);


/*
 * hb_buffer_t
 */

struct hb_buffer_t
{
  hb_object_header_t header;

  /*
   * Information about how the text in the buffer should be treated.
   */

  hb_unicode_funcs_t *unicode; /* Unicode functions */
  hb_buffer_flags_t flags; /* BOT / EOT / etc. */
  hb_buffer_cluster_level_t cluster_level;
  hb_codepoint_t replacement; /* U+FFFD or something else. */
  hb_codepoint_t invisible; /* 0 or something else. */
  hb_codepoint_t not_found; /* 0 or something else. */

  /*
   * Buffer contents
   */

  hb_buffer_content_type_t content_type;
  hb_segment_properties_t props; /* Script, language, direction */

  bool successful; /* Allocations successful */
  bool shaping_failed; /* Shaping failure */
  bool have_output; /* Whether we have an output buffer going on */
  bool have_positions; /* Whether we have positions */

  unsigned int idx; /* Cursor into ->info and ->pos arrays */
  unsigned int len; /* Length of ->info and ->pos arrays */
  unsigned int out_len; /* Length of ->out_info array if have_output */

  unsigned int allocated; /* Length of allocated arrays */
  hb_glyph_info_t     *info;
  hb_glyph_info_t     *out_info;
  hb_glyph_position_t *pos;

  /* Text before / after the main buffer contents.
   * Always in Unicode, and ordered outward.
   * Index 0 is for "pre-context", 1 for "post-context". */
  static constexpr unsigned CONTEXT_LENGTH = 5u;
  hb_codepoint_t context[2][CONTEXT_LENGTH];
  unsigned int context_len[2];


  /*
   * Managed by enter / leave
   */

  uint8_t allocated_var_bits;
  uint8_t serial;
  hb_buffer_scratch_flags_t scratch_flags; /* Have space-fallback, etc. */
  unsigned int max_len; /* Maximum allowed len. */
  int max_ops; /* Maximum allowed operations. */
  /* The bits here reflect current allocations of the bytes in glyph_info_t's var1 and var2. */


  /*
   * Messaging callback
   */

#ifndef HB_NO_BUFFER_MESSAGE
  hb_buffer_message_func_t message_func;
  void *message_data;
  hb_destroy_func_t message_destroy;
  unsigned message_depth; /* How deeply are we inside a message callback? */
#else
  static constexpr unsigned message_depth = 0u;
#endif



  /* Methods */

  HB_NODISCARD bool in_error () const { return !successful; }

  void allocate_var (unsigned int start, unsigned int count)
  {
    unsigned int end = start + count;
    assert (end <= 8);
    unsigned int bits = (1u<<end) - (1u<<start);
    assert (0 == (allocated_var_bits & bits));
    allocated_var_bits |= bits;
  }
  bool try_allocate_var (unsigned int start, unsigned int count)
  {
    unsigned int end = start + count;
    assert (end <= 8);
    unsigned int bits = (1u<<end) - (1u<<start);
    if (allocated_var_bits & bits)
      return false;
    allocated_var_bits |= bits;
    return true;
  }
  void deallocate_var (unsigned int start, unsigned int count)
  {
    unsigned int end = start + count;
    assert (end <= 8);
    unsigned int bits = (1u<<end) - (1u<<start);
    assert (bits == (allocated_var_bits & bits));
    allocated_var_bits &= ~bits;
  }
  void assert_var (unsigned int start, unsigned int count)
  {
    unsigned int end = start + count;
    assert (end <= 8);
    HB_UNUSED unsigned int bits = (1u<<end) - (1u<<start);
    assert (bits == (allocated_var_bits & bits));
  }
  void deallocate_var_all ()
  {
    allocated_var_bits = 0;
  }

  hb_glyph_info_t &cur (unsigned int i = 0) { return info[idx + i]; }
  hb_glyph_info_t cur (unsigned int i = 0) const { return info[idx + i]; }

  hb_glyph_position_t &cur_pos (unsigned int i = 0) { return pos[idx + i]; }
  hb_glyph_position_t cur_pos (unsigned int i = 0) const { return pos[idx + i]; }

  hb_glyph_info_t &prev ()      { return out_info[out_len ? out_len - 1 : 0]; }
  hb_glyph_info_t prev () const { return out_info[out_len ? out_len - 1 : 0]; }

  HB_INTERNAL void similar (const hb_buffer_t &src);
  HB_INTERNAL void reset ();
  HB_INTERNAL void clear ();

  /* Called around shape() */
  HB_INTERNAL void enter ();
  HB_INTERNAL void leave ();

#ifndef HB_NO_BUFFER_VERIFY
  HB_INTERNAL
#endif
  bool verify (hb_buffer_t        *text_buffer,
               hb_font_t          *font,
               const hb_feature_t *features,
               unsigned int        num_features,
               const char * const *shapers)
#ifndef HB_NO_BUFFER_VERIFY
  ;
#else
  { return true; }
#endif

  unsigned int backtrack_len () const { return have_output ? out_len : idx; }
  unsigned int lookahead_len () const { return len - idx; }
  uint8_t next_serial () { return ++serial ? serial : ++serial; }

  HB_INTERNAL void add (hb_codepoint_t  codepoint,
                        unsigned int    cluster);
  HB_INTERNAL void add_info (const hb_glyph_info_t &glyph_info);

  void reverse_range (unsigned start, unsigned end)
  {
    hb_array_t<hb_glyph_info_t> (info, len).reverse (start, end);
    if (have_positions)
      hb_array_t<hb_glyph_position_t> (pos, len).reverse (start, end);
  }
  void reverse () { reverse_range (0, len); }

  template <typename FuncType>
  void reverse_groups (const FuncType& group,
                       bool merge_clusters = false)
  {
    if (unlikely (!len))
      return;

    unsigned start = 0;
    unsigned i;
    for (i = 1; i < len; i++)
    {
      if (!group (info[i - 1], info[i]))
      {
        if (merge_clusters)
          this->merge_clusters (start, i);
        reverse_range (start, i);
        start = i;
      }
    }
    if (merge_clusters)
      this->merge_clusters (start, i);
    reverse_range (start, 