/****************************************************************************
 *
 * psft.c
 *
 *   FreeType Glue Component to Adobe's Interpreter (body).
 *
 * Copyright 2013-2014 Adobe Systems Incorporated.
 *
 * This software, and all works of authorship, whether in source or
 * object code form as indicated by the copyright notice(s) included
 * herein (collectively, the "Work") is made available, and may only be
 * used, modified, and distributed under the FreeType Project License,
 * LICENSE.TXT.  Additionally, subject to the terms and conditions of the
 * FreeType Project License, each contributor to the Work hereby grants
 * to any individual or legal entity exercising permissions granted by
 * the FreeType Project License and this section (hereafter, "You" or
 * "Your") a perpetual, worldwide, non-exclusive, no-charge,
 * royalty-free, irrevocable (except as stated in this section) patent
 * license to make, have made, use, offer to sell, sell, import, and
 * otherwise transfer the Work, where such license applies only to those
 * patent claims licensable by such contributor that are necessarily
 * infringed by their contribution(s) alone or by combination of their
 * contribution(s) with the Work to which such contribution(s) was
 * submitted.  If You institute patent litigation against any entity
 * (including a cross-claim or counterclaim in a lawsuit) alleging that
 * the Work or a contribution incorporated within the Work constitutes
 * direct or contributory patent infringement, then any patent licenses
 * granted to You under this License for that Work shall terminate as of
 * the date such litigation is filed.
 *
 * By using, modifying, or distributing the Work you indicate that you
 * have read and understood the terms and conditions of the
 * FreeType Project License as well as those provided in this section,
 * and you accept them fully.
 *
 */


#include "psft.h"
#include <freetype/internal/ftdebug.h>

#include "psfont.h"
#include "pserror.h"
#include "psobjs.h"
#include "cffdecode.h"

#ifdef TT_CONFIG_OPTION_GX_VAR_SUPPORT
#include <freetype/ftmm.h>
#include <freetype/internal/services/svmm.h>
#endif

#include <freetype/internal/services/svcfftl.h>


#define CF2_MAX_SIZE  cf2_intToFixed( 2000 )    /* max ppem */


  /*
   * This check should avoid most internal overflow cases.  Clients should
   * generally respond to `Glyph_Too_Big' by getting a glyph outline
   * at EM size, scaling it and filling it as a graphics operation.
   *
   */
  static FT_Error
  cf2_checkTransform( const CF2_Matrix*  transform,
                      CF2_Int            unitsPerEm )
  {
    CF2_Fixed  maxScale;


    FT_ASSERT( unitsPerEm > 0 );

    if ( transform->a <= 0 || transform->d <= 0 )
      return FT_THROW( Invalid_Size_Handle );

    FT_ASSERT( transform->b == 0 && transform->c == 0 );
    FT_ASSERT( transform->tx == 0 && transform->ty == 0 );

    if ( unitsPerEm > 0x7FFF )
      return FT_THROW( Glyph_Too_Big );

    maxScale = FT_DivFix( CF2_MAX_SIZE, cf2_intToFixed( unitsPerEm ) );

    if ( transform->a > maxScale || transform->d > maxScale )
      return FT_THROW( Glyph_Too_Big );

    return FT_Err_Ok;
  }


  static void
  cf2_setGlyphWidth( CF2_Outline  outline,
                     CF2_Fixed    width )
  {
    PS_Decoder*  decoder = outline->decoder;


    FT_ASSERT( decoder );

    if ( !decoder->builder.is_t1 )
      *decoder->glyph_width = cf2_fixedToInt( width );
  }


  /* Clean up font instance. */
  static void
  cf2_free_instance( void*  ptr )
  {
    CF2_Font  font = (CF2_Font)ptr;


    if ( font )
    {
      FT_Memory  memory = font->memory;


      FT_FREE( font->blend.lastNDV );
      FT_FREE( font->blend.BV );
    }
  }


  /*********************************************
   *
   * functions for handling client outline;
   * FreeType uses coordinates in 26.6 format
   *
   */

  static void
  cf2_builder_moveTo( CF2_OutlineCallbacks      callbacks,
                      const CF2_CallbackParams  params )
  {
    /* downcast the object pointer */
    CF2_Outline  outline = (CF2_Outline)callbacks;
    PS_Builder*  builder;

    (void)params;        /* only used in debug mode */


    FT_ASSERT( outline && outline->decoder );
    FT_ASSERT( params->op == CF2_PathOpMoveTo );

    builder = &outline->decoder->builder;

    /* note: two successive moves simply close the contour twice */
    ps_builder_close_contour( builder );
    builder->path_begun = 0;
  }


  static void
  cf2_builder_lineTo( CF2_OutlineCallbacks      callbacks,
                      const CF2_CallbackParams  params )
  {
    FT_Error  error;

    /* downcast the object pointer */
    CF2_Outline  outline = (CF2_Outline)callbacks;
    PS_Builder*  builder;


    FT_ASSERT( outline && outline->decoder );
    FT_ASSERT( params->op == CF2_PathOpLineTo );

    builder = &outline->decoder->builder;

    if ( !builder->path_begun )
    {
      /* record the move before the line; also check points and set */
      /* `path_begun'                                               */
      error = ps_builder_start_point( builder,
                                      params->pt0.x,
                                      params->pt0.y );
      if ( error )
      {
        if ( !*callbacks->error )
          *callbacks->error =  error;
        return;
      }
    }

    /* `ps_builder_add_point1' includes a check_points call for one point */
    error = ps_builder_add_point1( builder,
                                   params->pt1.x,
                                   params->pt1.y );
    if ( error )
    {
      if ( !*callbacks->error )
        *callbacks->error =  error;
      return;
    }
  }


  static void
  cf2_builder_cubeTo( CF2_OutlineCallbacks      callbacks,
                      const CF2_CallbackParams  params )
  {
    FT_Error  error;

    /* downcast the object pointer */
    CF2_Outline  outline = (CF2_Outline)callbacks;
    PS_Builder*  builder;


    FT_ASSERT( outline && outline->decoder );
    FT_ASSERT( params->op == CF2_PathOpCubeTo );

    builder = &outline->decoder->builder;

    if ( !builder->path_begun )
    {
      /* record the move before the line; also check points and set */
      /* `path_begun'                                               */
      error = ps_builder_start_point( builder,
                                      params->pt0.x,
                                      params->pt0.y );
      if ( error )
      {
        if ( !*callbacks->error )
          *callbacks->error =  error;
        return;
      }
    }

    /* prepare room for 3 points: 2 off-curve, 1 on-curve */
    error = ps_builder_check_points( builder, 3 );
    if ( error )
    {
      if ( !*callbacks->error )
        *callbacks->error =  error;
      return;
    }

    ps_builder_add_point( builder,
                          params->pt1.x,
                          params->pt1.y, 0 );
    ps_builder_add_point( builder,
                          params->pt2.x,
                          params->pt2.y, 0 );
    ps_builder_add_point( builder,
                          params->pt3.x,
                          params->pt3.y, 1 );
  }


  static void
  cf2_outline_init( CF2_Outline  outline,
                    FT_Memory    memory,
                    FT_Error*    error )
  {
    FT_ZERO( outline );

    outline->root.memory = memory;
    outline->root.error  = error;

    outline->root.moveTo = cf2_builder_moveTo;
    outline->root.lineTo = cf2_builder_lineTo;
    outline->root.cubeTo = cf2_builder_cubeTo;
  }


  /* get scaling and hint flag from GlyphSlot */
  static void
  cf2_getScaleAndHintFlag( PS_Decoder*  decoder,
                           CF2_Fixed*   x_scale,
                           CF2_Fixed*   y_scale,
                           FT_Bool*     hinted,
                           FT_Bool*     scaled )
  {
    FT_ASSERT( decoder && decoder->builder.glyph );

    /* note: FreeType scale includes a factor of 64 */
    *hinted = decoder->builder.glyph->hint;
    *scaled = decoder->builder.glyph->scaled;

    if ( *hinted )
    {
      *x_scale = ADD_INT32( decoder->builder.glyph->x_scale, 32 ) / 64;
      *y_scale = ADD_INT32( decoder->builder.glyph->y_scale, 32 ) / 64;
    }
    else
    {
      /* for unhinted outlines, `cff_slot_load' does the scaling, */
      /* thus render at `unity' scale                             */

      *x_scale = 0x0400;   /* 1/64 as 16.16 */
      *y_scale = 0x0400;
    }
  }


  /* get units per em from `FT_Face' */
  /* TODO: should handle font matrix concatenation? */
  static FT_UShort
  cf2_getUnitsPerEm( PS_Decoder*  decoder )
  {
    FT_ASSERT( decoder && decoder->builder.face );
    FT_ASSERT( decoder->builder.face->units_per_EM );

    return decoder->builder.face->units_per_EM;
  }


  /* Main entry point: Render one glyph. */
  FT_LOCAL_DEF( FT_Error )
  cf2_decoder_parse_charstrings( PS_Decoder*  decoder,
                                 FT_Byte*     charstring_base,
                                 FT_ULong     charstring_len )
  {
    FT_Memory  memory;
    FT_Error   error = FT_Err_Ok;
    CF2_Font   font;

    FT_Bool  is_t1 = decoder->builder.is_t1;


    FT_ASSERT( decoder &&
               ( is_t1 || decoder->cff ) );

    if ( is_t1 && !decoder->current_subfont )
    {
      FT_ERROR(( "cf2_decoder_parse_charstrings (Type 1): "
                 "SubFont missing. Use `t1_make_subfont' first\n" ));
      return FT_THROW( Invalid_Table );
    }

    memory = decoder->builder.memory;

    /* CF2 data is saved here across glyphs */
    font = (CF2_Font)decoder->cf2_instance->data;

    /* on first glyph, allocate instance structure */
    if ( !decoder->cf2_instance->data )
    {
      decoder->cf2_instance->finalizer =
        (FT_Generic_Finalizer)cf2_free_instance;

      if ( FT_ALLOC( decoder->cf2_instance->data,
                     sizeof ( CF2_FontRec ) ) )
        return FT_THROW( Out_Of_Memory );

      font = (CF2_Font)decoder->cf2_instance->data;

      font->memory = memory;

      if ( !is_t1 )
        font->cffload = (FT_Service_CFFLoad)decoder->cff->cffload;

      /* initialize a client outline, to be shared by each glyph rendered */
      cf2_outline_init( &font->outline, font->memory, &font->error );
    }

    /* save decoder; it is a stack variable and will be different on each */
    /* call                                                               */
    font->decoder         = decoder;
    font->outline.decoder = decoder;

    {
      /* build parameters for Adobe engine */

      PS_Builder*  builder = &decoder->builder;
      PS_Driver    driver  = (PS_Driver)FT_FACE_DRIVER( builder->face );

      FT_Bool  no_stem_darkening_driver =
                 driver->no_stem_darkening;
      FT_Char  no_stem_darkening_font =
                 builder->face->internal->no_stem_darkening;

      /* local error */
      FT_Error       error2 = FT_Err_Ok;
      CF2_BufferRec  buf;
      CF2_Matrix     transform;
      CF2_F16Dot16   glyphWidth;

      FT_Bool  hinted;
      FT_Bool  scaled;


      /* FreeType has already looked up the GID; convert to         */
      /* `RegionBuffer', assuming that the input has been validated */
      FT_ASSERT( charstring_base + charstring_len >= charstring_base );

      FT_ZERO( &buf );
      buf.start =
      buf.ptr   = charstring_base;
      buf.end   = FT_OFFSET( charstring_base, charstring_len );

      FT_ZERO( &transform );

      cf2_getScaleAndHintFlag( decoder,
                               &transform.a,
                               &transform.d,
                               &hinted,
                               &scaled 