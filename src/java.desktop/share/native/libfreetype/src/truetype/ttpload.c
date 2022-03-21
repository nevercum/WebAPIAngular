/****************************************************************************
 *
 * ttpload.c
 *
 *   TrueType-specific tables loader (body).
 *
 * Copyright (C) 1996-2022 by
 * David Turner, Robert Wilhelm, and Werner Lemberg.
 *
 * This file is part of the FreeType project, and may only be used,
 * modified, and distributed under the terms of the FreeType project
 * license, LICENSE.TXT.  By continuing to use, modify, or distribute
 * this file you indicate that you have read the license and
 * understand and accept it fully.
 *
 */


#include <freetype/internal/ftdebug.h>
#include <freetype/internal/ftobjs.h>
#include <freetype/internal/ftstream.h>
#include <freetype/tttags.h>

#include "ttpload.h"

#ifdef TT_CONFIG_OPTION_GX_VAR_SUPPORT
#include "ttgxvar.h"
#endif

#include "tterrors.h"


  /**************************************************************************
   *
   * The macro FT_COMPONENT is used in trace mode.  It is an implicit
   * parameter of the FT_TRACE() and FT_ERROR() macros, used to print/log
   * messages during execution.
   */
#undef  FT_COMPONENT
#define FT_COMPONENT  ttpload


  /**************************************************************************
   *
   * @Function:
   *   tt_face_load_loca
   *
   * @Description:
   *   Load the locations table.
   *
   * @InOut:
   *   face ::
   *     A handle to the target face object.
   *
   * @Input:
   *   stream ::
   *     The input stream.
   *
   * @Return:
   *   FreeType error code.  0 means success.
   */
  FT_LOCAL_DEF( FT_Error )
  tt_face_load_loca( TT_Face    face,
                     FT_Stream  stream )
  {
    FT_Error  error;
    FT_ULong  table_len;
    FT_Int    shift;


    /* we need the size of the `glyf' table for malformed `loca' tables */
    error = face->goto_table( face, TTAG_glyf, stream, &face->glyf_len );

    /* it is possible that a font doesn't have a glyf table at all */
    /* or its size is zero                                         */
    if ( FT_ERR_EQ( error, Table_Missing ) )
    {
      face->glyf_len    = 0;
      face->glyf_offset = 0;
    }
    else if ( error )
      goto Exit;
    else
    {
#ifdef FT_CONFIG_OPTION_INCREMENTAL
      if ( face->root.internal->incremental_interface )
        face->glyf_offset = 0;
      else
#endif
        face->glyf_offset = FT_STREAM_POS();
    }

    FT_TRACE2(( "Locations " ));
    error = face->goto_table( face, TTAG_loca, stream, &table_len );
    if ( error )
    {
      error = FT_THROW( Locations_Missing );
      goto Exit;
    }

    shift = face->header.Index_To_Loc_Format != 0 ? 2 : 1;

    if ( table_len > 0x10000UL << shift )
    {
      FT_TRACE2(( "table too large\n" ));
      table_len = 0x10000UL << shift;
    }

    face->num_locations = table_len >> shift;

    if ( face->num_locations != (FT_ULong)face->root.num_glyphs + 1 )
    {
      FT_TRACE2(( "glyph count mismatch!  loca: %ld, maxp: %ld\n",
                  face->num_locations - 1, face->root.num_glyphs ));

      /* we only handle the case where `maxp' gives a larger value */
      if ( face->num_locations < (FT_ULong)face->root.num_glyphs + 1 )
      {
        FT_ULong  new_loca_len =
                    ( (FT_ULong)face->root.num_glyphs + 1 ) << shift;

        TT_Table  entry = face->dir_tables;
        TT_Table  limit = entry + face->num_tables;

        FT_Long  pos   = (FT_Long)FT_STREAM_POS();
        FT_Long  dist  = 0x7FFFFFFFL;
        FT_Bool  found = 0;


        /* 