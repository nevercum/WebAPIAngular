/****************************************************************************
 *
 * ftobjs.c
 *
 *   The FreeType private base classes (body).
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


#include <freetype/ftlist.h>
#include <freetype/ftoutln.h>
#include <freetype/ftfntfmt.h>
#include <freetype/otsvg.h>

#include <freetype/internal/ftvalid.h>
#include <freetype/internal/ftobjs.h>
#include <freetype/internal/ftdebug.h>
#include <freetype/internal/ftrfork.h>
#include <freetype/internal/ftstream.h>
#include <freetype/internal/sfnt.h>          /* for SFNT_Load_Table_Func */
#include <freetype/internal/psaux.h>         /* for PS_Driver            */
#include <freetype/internal/svginterface.h>

#include <freetype/tttables.h>
#include <freetype/tttags.h>
#include <freetype/ttnameid.h>

#include <freetype/internal/services/svprop.h>
#include <freetype/internal/services/svsfnt.h>
#include <freetype/internal/services/svpostnm.h>
#include <freetype/internal/services/svgldict.h>
#include <freetype/internal/services/svttcmap.h>
#include <freetype/internal/services/svkern.h>
#include <freetype/internal/services/svtteng.h>

#include <freetype/ftdriver.h>

#ifdef FT_CONFIG_OPTION_MAC_FONTS
#include "ftbase.h"
#endif


#ifdef FT_DEBUG_LEVEL_TRACE

#include <freetype/ftbitmap.h>

#if defined( _MSC_VER )      /* Visual C++ (and Intel C++)   */
  /* We disable the warning `conversion from XXX to YYY,     */
  /* possible loss of data' in order to compile cleanly with */
  /* the maximum level of warnings: `md5.c' is non-FreeType  */
  /* code, and it gets used during development builds only.  */
#pragma warning( push )
#pragma warning( disable : 4244 )
#endif /* _MSC_VER */

  /* It's easiest to include `md5.c' directly.  However, since OpenSSL */
  /* also provides the same functions, there might be conflicts if     */
  /* both FreeType and OpenSSL are built as static libraries.  For     */
  /* this reason, we put the MD5 stuff into the `FT_' namespace.       */
#define MD5_u32plus  FT_MD5_u32plus
#define MD5_CTX      FT_MD5_CTX
#define MD5_Init     FT_MD5_Init
#define MD5_Update   FT_MD5_Update
#define MD5_Final    FT_MD5_Final

#undef  HAVE_OPENSSL

#include "md5.c"

#if defined( _MSC_VER )
#pragma warning( pop )
#endif

  /* This array must stay in sync with the @FT_Pixel_Mode enumeration */
  /* (in file `ftimage.h`).                                           */

  static const char* const  pixel_modes[] =
  {
    "none",
    "monochrome bitmap",
    "gray 8-bit bitmap",
    "gray 2-bit bitmap",
    "gray 4-bit bitmap",
    "LCD 8-bit bitmap",
    "vertical LCD 8-bit bitmap",
    "BGRA 32-bit color image bitmap",
    "SDF 8-bit bitmap"
  };

#endif /* FT_DEBUG_LEVEL_TRACE */


#define GRID_FIT_METRICS


  /* forward declaration */
  static FT_Error
  ft_open_face_internal( FT_Library           library,
                         const FT_Open_Args*  args,
                         FT_Long              face_index,
                         FT_Face             *aface,
                         FT_Bool              test_mac_fonts );


  FT_BASE_DEF( FT_Pointer )
  ft_service_list_lookup( FT_ServiceDesc  service_descriptors,
                          const char*     service_id )
  {
    FT_Pointer      result = NULL;
    FT_ServiceDesc  desc   = service_descriptors;


    if ( desc && service_id )
    {
      for ( ; desc->serv_id != NULL; desc++ )
      {
        if ( ft_strcmp( desc->serv_id, service_id ) == 0 )
        {
          result = (FT_Pointer)desc->serv_data;
          break;
        }
      }
    }

    return result;
  }


  FT_BASE_DEF( void )
  ft_validator_init( FT_Validator        valid,
                     const FT_Byte*      base,
                     const FT_Byte*      limit,
                     FT_ValidationLevel  level )
  {
    valid->base  = base;
    valid->limit = limit;
    valid->level = level;
    valid->error = FT_Err_Ok;
  }


  FT_BASE_DEF( FT_Int )
  ft_validator_run( FT_Validator  valid )
  {
    /* This function doesn't work!  None should call it. */
    FT_UNUSED( valid );

    return -1;
  }


  FT_BASE_DEF( void )
  ft_validator_error( FT_Validator  valid,
                      FT_Error      error )
  {
    /* since the cast below also disables the compiler's */
    /* type check, we introduce a dummy variable, which  */
    /* will be optimized away                            */
    volatile ft_jmp_buf* jump_buffer = &valid->jump_buffer;


    valid->error = error;

    /* throw away volatileness; use `jump_buffer' or the  */
    /* compiler may warn about an unused local variable   */
    ft_longjmp( *(ft_jmp_buf*) jump_buffer, 1 );
  }


  /*************************************************************************/
  /*************************************************************************/
  /*************************************************************************/
  /****                                                                 ****/
  /****                                                                 ****/
  /****                           S T R E A M                           ****/
  /****                                                                 ****/
  /****                                                                 ****/
  /*************************************************************************/
  /*************************************************************************/
  /*************************************************************************/


  /* create a new input stream from an FT_Open_Args structure */
  /*                                                          */
  FT_BASE_DEF( FT_Error )
  FT_Stream_New( FT_Library           library,
                 const FT_Open_Args*  args,
                 FT_Stream           *astream )
  {
    FT_Error   error;
    FT_Memory  memory;
    FT_Stream  stream = NULL;
    FT_UInt    mode;


    *astream = NULL;

    if ( !library )
      return FT_THROW( Invalid_Library_Handle );

    if ( !args )
      return FT_THROW( Invalid_Argument );

    memory = library->memory;
    mode   = args->flags &
               ( FT_OPEN_MEMORY | FT_OPEN_STREAM | FT_OPEN_PATHNAME );

    if ( mode == FT_OPEN_MEMORY )
    {
      /* create a memory-based stream */
      if ( FT_NEW( stream ) )
        goto Exit;

      FT_Stream_OpenMemory( stream,
                            (const FT_Byte*)args->memory_base,
                            (FT_ULong)args->memory_size );
      stream->memory = memory;
    }

#ifndef FT_CONFIG_OPTION_DISABLE_STREAM_SUPPORT

    else if ( mode == FT_OPEN_PATHNAME )
    {
      /* create a normal system stream */
      if ( FT_NEW( stream ) )
        goto Exit;

      stream->memory = memory;
      error = FT_Stream_Open( stream, args->pathname );
      if ( error )
        FT_FREE( stream );
    }
    else if ( ( mode == FT_OPEN_STREAM ) && args->stream )
    {
      /* use an existing, user-provided stream */

      /* in this case, we do not need to allocate a new stream object */
      /* since the caller is responsible for closing it himself       */
      stream         = args->stream;
      stream->memory = memory;
      error          = FT_Err_Ok;
    }

#endif

    else
    {
      error = FT_THROW( Invalid_Argument );
      if ( ( args->flags & FT_OPEN_STREAM ) && args->stream )
        FT_Stream_Close( args->stream );
    }

    if ( !error )
      *astream       = stream;

  Exit:
    return error;
  }


  FT_BASE_DEF( void )
  FT_Stream_Free( FT_Stream  stream,
                  FT_Int     external )
  {
    if ( stream )
    {
      FT_Memory  memory = stream->memory;


      FT_Stream_Close( stream );

      if ( !external )
        FT_FREE( stream );
    }
  }


  /**************************************************************************
   *
   * The macro FT_COMPONENT is used in trace mode.  It is an implicit
   * parameter of the FT_TRACE() and FT_ERROR() macros, used to print/log
   * messages during execution.
   */
#undef  FT_COMPONENT
#define FT_COMPONENT  objs


  /*************************************************************************/
  /*************************************************************************/
  /*************************************************************************/
  /****                                                                 ****/
  /****                                                                 ****/
  /****               FACE, SIZE & GLYPH SLOT OBJECTS                   ****/
  /****                                                                 ****/
  /****                                                                 ****/
  /*************************************************************************/
  /*************************************************************************/
  /*************************************************************************/


  static FT_Error
  ft_glyphslot_init( FT_GlyphSlot  slot )
  {
    FT_Driver         driver   = slot->face->driver;
    FT_Driver_Class   clazz    = driver->clazz;
    FT_Memory         memory   = driver->root.memory;
    FT_Error          error    = FT_Err_Ok;
    FT_Slot_Internal  internal = NULL;


    slot->library = driver->root.library;

    if ( FT_NEW( internal ) )
      goto Exit;

    slot->internal = internal;

    if ( FT_DRIVER_USES_OUTLINES( driver ) )
      error = FT_GlyphLoader_New( memory, &internal->loader );

    if ( !error && clazz->init_slot )
      error = clazz->init_slot( slot );

#ifdef FT_CONFIG_OPTION_SVG
    /* if SVG table exists, allocate the space in `slot->other` */
    if ( slot->face->face_flags & FT_FACE_FLAG_SVG )
    {
      FT_SVG_Document  document = NULL;


      if ( FT_NEW( document ) )
        goto Exit;
      slot->other = document;
    }
#endif

  Exit:
    return error;
  }


  FT_BASE_DEF( void )
  ft_glyphslot_free_bitmap( FT_GlyphSlot  slot )
  {
    if ( slot->internal && ( slot->internal->flags & FT_GLYPH_OWN_BITMAP ) )
    {
      FT_Memory  memory = FT_FACE_MEMORY( slot->face );


      FT_FREE( slot->bitmap.buffer );
      slot->internal->flags &= ~FT_GLYPH_OWN_BITMAP;
    }
    else
    {
      /* assume that the bitmap buffer was stolen or not */
      /* allocated from the heap                         */
      slot->bitmap.buffer = NULL;
    }
  }


  /* overflow-resistant presetting of bitmap position and dimensions; */
  /* also check whether the size is too large for rendering           */
  FT_BASE_DEF( FT_Bool )
  ft_glyphslot_preset_bitmap( FT_GlyphSlot      slot,
                              FT_Render_Mode    mode,
                              const FT_Vector*  origin )
  {
    FT_Outline*  outline = &slot->outline;
    FT_Bitmap*   bitmap  = &slot->bitmap;

    FT_Pixel_Mode  pixel_mode;

    FT_BBox  cbox, pbox;
    FT_Pos   x_shift = 0;
    FT_Pos   y_shift = 0;
    FT_Pos   x_left, y_top;
    FT_Pos   width, height, pitch;


    if ( slot->format == FT_GLYPH_FORMAT_SVG )
    {
      FT_Module    module;
      SVG_Service  svg_service;


      module      = FT_Get_Module( slot->library, "ot-svg" );
      svg_service = (SVG_Service)module->clazz->module_interface;

      return (FT_Bool)svg_service->preset_slot( module, slot, FALSE );
    }
    else if ( slot->format != FT_GLYPH_FORMAT_OUTLINE )
      return 1;

    if ( origin )
    {
      x_shift = origin->x;
      y_shift = origin->y;
    }

    /* compute the control box, and grid-fit it, */
    /* taking into account the origin shift      */
    FT_Outline_Get_CBox( outline, &cbox );

    /* rough estimate of pixel box */
    pbox.xMin = ( cbox.xMin >> 6 ) + ( x_shift >> 6 );
    pbox.yMin = ( cbox.yMin >> 6 ) + ( y_shift >> 6 );
    pbox.xMax = ( cbox.xMax >> 6 ) + ( x_shift >> 6 );
    pbox.yMax = ( cbox.yMax >> 6 ) + ( y_shift >> 6 );

    /* tiny remainder box */
    cbox.xMin = ( cbox.xMin & 63 ) + ( x_shift & 63 );
    cbox.yMin = ( cbox.yMin & 63 ) + ( y_shift & 63 );
    cbox.xMax = ( cbox.xMax & 63 ) + ( x_shift & 63 );
    cbox.yMax = ( cbox.yMax & 63 ) + ( y_shift & 63 );

    switch ( mode )
    {
    case FT_RENDER_MODE_MONO:
      pixel_mode = FT_PIXEL_MODE_MONO;
#if 1
      /* x */

      /* undocumented but confirmed: bbox values get rounded;    */
      /* we do asymmetric rounding so that the center of a pixel */
      /* gets always included                                    */

      pbox.xMin += ( cbox.xMin + 31 ) >> 6;
      pbox.xMax += ( cbox.xMax + 32 ) >> 6;

      /* if the bbox collapsed, we add a pixel based on the total */
      /* rounding remainder to cover most of the original cbox    */

      if ( pbox.xMin == pbox.xMax )
      {
        if ( ( ( cbox.xMin + 31 ) & 63 ) - 31 +
             ( ( cbox.xMax + 32 ) & 63 ) - 32 < 0 )
          pbox.xMin -= 1;
        else
          pbox.xMax += 1;
      }

      /* y */

      pbox.yMin += ( cbox.yMin + 31 ) >> 6;
      pbox.yMax += ( cbox.yMax + 32 ) >> 6;

      if ( pbox.yMin == pbox.yMax )
      {
        if ( ( ( cbox.yMin + 31 ) & 63 ) - 31 +
             ( ( cbox.yMax + 32 ) & 63 ) - 32 < 0 )
          pbox.yMin -= 1;
        else
          pbox.yMax += 1;
      }

      break;
#else
      goto Adjust;
#endif

    case FT_RENDER_MODE_LCD:
      pixel_mode = FT_PIXEL_MODE_LCD;
      ft_lcd_padding( &cbox, slot, mode );
      goto Adjust;

    case FT_RENDER_MODE_LCD_V:
      pixel_mode = FT_PIXEL_MODE_LCD_V;
      ft_lcd_padding( &cbox, slot, mode );
      goto Adjust;

    case FT_RENDER_MODE_NORMAL:
    case FT_RENDER_MODE_LIGHT:
    default:
      pixel_mode = FT_PIXEL_MODE_GRAY;
    Adjust:
      pbox.xMin += cbox.xMin >> 6;
      pbox.yMin += cbox.yMin >> 6;
      pbox.xMax += ( cbox.xMax + 63 ) >> 6;
      pbox.yMax += ( cbox.yMax + 63 ) >> 6;
    }

    x_left = pbox.xMin;
    y_top  = pbox.yMax;

    width  = pbox.xMax - pbox.xMin;
    height = pbox.yMax - pbox.yMin;

    switch ( pixel_mode )
    {
    case FT_PIXEL_MODE_MONO:
      pitch = ( ( width + 15 ) >> 4 ) << 1;
      break;

    case FT_PIXEL_MODE_LCD:
      width *= 3;
      pitch  = FT_PAD_CEIL( width, 4 );
      break;

    case FT_PIXEL_MODE_LCD_V:
      height *= 3;
      /* fall through */

    case FT_PIXEL_MODE_GRAY:
    default:
      pitch = width;
    }

    slot->bitmap_left = (FT_Int)x_left;
    slot->bitmap_top  = (FT_Int)y_top;

    bitmap->pixel_mode = (unsigned char)pixel_mode;
    bitmap->num_grays  = 256;
    bitmap->width      = (unsigned int)width;
    bitmap->rows       = (unsigned int)height;
    bitmap->pitch      = pitch;

    if ( pbox.xMin < -0x8000 || pbox.xMax > 0x7FFF ||
         pbox.yMin < -0x8000 || pbox.yMax > 0x7FFF )
    {
      FT_TRACE3(( "ft_glyphslot_preset_bitmap: [%ld %ld %ld %ld]\n",
                  pbox.xMin, pbox.yMin, pbox.xMax, pbox.yMax ));
      return 1;
    }

    return 0;
  }


  FT_BASE_DEF( void )
  ft_glyphslot_set_bitmap( FT_GlyphSlot  slot,
                           FT_Byte*      buffer )
  {
    ft_glyphslot_free_bitmap( slot );

    slot->bitmap.buffer = buffer;

    FT_ASSERT( (slot->internal->flags & FT_GLYPH_OWN_BITMAP) == 0 );
  }


  FT_BASE_DEF( FT_Error )
  ft_glyphslot_alloc_bitmap( FT_GlyphSlot  slot,
                             FT_ULong      size )
  {
    FT_Memory  memory = FT_FACE_MEMORY( slot->face );
    FT_Error   error;


    if ( slot->internal->flags & FT_GLYPH_OWN_BITMAP )
      FT_FREE( slot->bitmap.buffer );
    else
      slot->internal->flags |= FT_GLYPH_OWN_BITMAP;

    FT_MEM_ALLOC( slot->bitmap.buffer, size );
    return error;
  }


  static void
  ft_glyphslot_clear( FT_GlyphSlot  slot )
  {
    /* free bitmap if needed */
    ft_glyphslot_free_bitmap( slot );

    /* clear all public fields in the glyph slot */
    slot->glyph_index = 0;

    FT_ZERO( &slot->metrics );
    FT_ZERO( &slot->outline );

    slot->bitmap.width      = 0;
    slot->bitmap.rows       = 0;
    slot->bitmap.pitch      = 0;
    slot->bitmap.pixel_mode = 0;
    /* `slot->bitmap.buffer' has been handled by ft_glyphslot_free_bitmap */

    slot->bitmap_left   = 0;
    slot->bitmap_top    = 0;
    slot->num_subglyphs = 0;
    slot->subglyphs     = NULL;
    slot->control_data  = NULL;
    slot->control_len   = 0;

#ifndef FT_CONFIG_OPTION_SVG
    slot->other = NULL;
#else
    if ( !( slot->face->face_flags & FT_FACE_FLAG_SVG ) )
      slot->other = NULL;
    else
    {
      if ( slot->internal->flags & FT_GLYPH_OWN_GZIP_SVG )
      {
        FT_Memory        memory = slot->face->memory;
        FT_SVG_Document  doc    = (FT_SVG_Document)slot->other;


        FT_FREE( doc->svg_document );
        slot->internal->load_flags &= ~FT_GLYPH_OWN_GZIP_SVG;
      }
    }
#endif

    slot->format = FT_GLYPH_FORMAT_NONE;

    slot->linearHoriAdvance = 0;
    slot->linearVertAdvance = 0;
    slot->advance.x         = 0;
    slot->advance.y         = 0;
    slot->lsb_delta         = 0;
    slot->rsb_delta         = 0;
  }


  static void
  ft_glyphslot_done( FT_GlyphSlot  slot )
  {
    FT_Driver        driver = slot->face->driver;
    FT_Driver_Class  clazz  = driver->clazz;
    FT_Memory        memory = driver->root.memory;

#ifdef FT_CONFIG_OPTION_SVG
    if ( slot->face->face_flags & FT_FACE_FLAG_SVG )
    {
      /* free memory in case SVG was there */
      if ( slot->internal->flags & FT_GLYPH_OWN_GZIP_SVG )
      {
        FT_SVG_Document  doc = (FT_SVG_Document)slot->other;


        FT_FREE( doc->svg_document );

        slot->internal->flags &= ~FT_GLYPH_OWN_GZIP_SVG;
      }

      FT_FREE( slot->other );
    }
#endif

    if ( clazz->done_slot )
      clazz->done_slot( slot );

    /* free bitmap buffer if needed */
    ft_glyphslot_free_bitmap( slot );

    /* slot->internal might be NULL in out-of-memory situations */
    if ( slot->internal )
    {
      /* free glyph loader */
      if ( FT_DRIVER_USES_OUTLINES( driver ) )
      {
        FT_GlyphLoader_Done( slot->internal->loader );
        slot->internal->loader = NULL;
      }

      FT_FREE( slot->internal );
    }
  }


  /* documentation is in ftobjs.h */

  FT_BASE_DEF( FT_Error )
  FT_New_GlyphSlot( FT_Face        face,
                    FT_GlyphSlot  *aslot )
  {
    FT_Error         error;
    FT_Driver        driver;
    FT_Driver_Class  clazz;
    FT_Memory        memory;
    FT_GlyphSlot     slot = NULL;


    if ( !face )
      return FT_THROW( Invalid_Face_Handle );

    if ( !face->driver )
      return FT_THROW( Invalid_Argument );

    driver = face->driver;
    clazz  = driver->clazz;
    memory = driver->root.memory;

    FT_TRACE4(( "FT_New_GlyphSlot: Creating new slot object\n" ));
    if ( !FT_ALLOC( slot, clazz->slot_object_size ) )
    {
      slot->