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
    PS_Builde