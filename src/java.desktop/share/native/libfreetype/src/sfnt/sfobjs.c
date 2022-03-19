/****************************************************************************
 *
 * sfobjs.c
 *
 *   SFNT object management (base).
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


#include "sfobjs.h"
#include "ttload.h"
#include "ttcmap.h"
#include "ttkern.h"
#include "sfwoff.h"
#include "sfwoff2.h"
#include <freetype/internal/sfnt.h>
#include <freetype/internal/ftdebug.h>
#include <freetype/ttnameid.h>
#include <freetype/tttags.h>
#include <freetype/internal/services/svpscmap.h>
#include <freetype/ftsnames.h>

#ifdef TT_CONFIG_OPTION_GX_VAR_SUPPORT
#include <freetype/internal/services/svmm.h>
#include <freetype/internal/services/svmetric.h>
#endif

#include "sferrors.h"

#ifdef TT_CONFIG_OPTION_BDF
#include "ttbdf.h"
#endif


  /**************************************************************************
   *
   * The macro FT_COMPONENT is used in trace mode.  It is an implicit
   * parameter of the FT_TRACE() and FT_ERROR() macros, used to print/log
   * messages during execution.
   */
#undef  FT_COMPONENT
#define FT_COMPONENT  sfobjs



  /* convert a UTF-16 name entry to ASCII */
  static FT_String*
  tt_name_ascii_from_utf16( TT_Name    entry,
                            FT_Memory  memory )
  {
    FT_String*  string = NULL;
    FT_UInt     len, code, n;
    FT_Byte*    read   = (FT_Byte*)entry->string;
    FT_Error    error;


    len = (FT_UInt)entry->stringLength / 2;

    if ( FT_QNEW_ARRAY( string, len + 1 ) )
      return NULL;

    for ( n = 0; n < len; n++ )
    {
      code = FT_NEXT_USHORT( read );

      if ( code == 0 )
        break;

      if ( code < 32 || code > 127 )
        code = '?';

      string[n] = (char)code;
    }

    string[n] = 0;

    return string;
  }


  /* convert an Apple Roman or symbol name entry to ASCII */
  static FT_String*
  tt_name_ascii_from_other( TT_Name    entry,
                            FT_Memory  memory )
  {
    FT_String*  string = NULL;
    FT_UInt     len, code, n;
    FT_Byte*    read   = (FT_Byte*)entry->string;
    FT_Error    error;


    len = (FT_UInt)entry->stringLength;

    if ( FT_QNEW_ARRAY( string, len + 1 ) )
      return NULL;

    for ( n = 0; n < len; n++ )
    {
      code = *read++;

      if ( code == 0 )
        break;

      if ( code < 32 || code > 127 )
        code = '?';

      string[n] = (char)code;
    }

    string[n] = 0;

    return string;
  }


  typedef FT_String*  (*TT_Name_ConvertFunc)( TT_Name    entry,
                                              FT_Memory  memory );


  /* documentation is in sfnt.h */

  FT_LOCAL_DEF( FT_Error )
  tt_face_get_name( TT_Face      face,
                    FT_UShort    nameid,
                    FT_String**  name )
  {
    FT_Memory   memory = face->root.memory;
    FT_Error    error  = FT_Err_Ok;
    FT_String*  result = NULL;
    FT_UShort   n;
    TT_Name     rec;

    FT_Int  found_apple         = -1;
    FT_Int  found_apple_roman   = -1;
    FT_Int  found_apple_english = -1;
    FT_Int  found_win           = -1;
    FT_Int  found_unicode       = -1;

    FT_Bool  is_english = 0;

    TT_Name_ConvertFunc  convert;


    FT_ASSERT( name );

    rec = face->name_table.names;
    for ( n = 0; n < face->num_names; n++, rec++ )
    {
      /* According to the OpenType 1.3 specification, only Microsoft or  */
      /* Apple platform IDs might be used in the `name' table.  The      */
      /* `Unicode' platform is reserved for the `cmap' table, and the    */
      /* `ISO' one is deprecated.                                        */
      /*                                                                 */
      /* However, the Apple TrueType specification doesn't say the same  */
      /* thing and goes to suggest that all Unicode `name' table entries */
      /* should be coded in UTF-16 (in big-endian format I suppose).     */
      /*                                                                 */
      if ( rec->nameID == nameid && rec->stringLength > 0 )
      {
        switch ( rec->platformID )
        {
        case TT_PLATFORM_APPLE_UNICODE:
        case TT_PLATFORM_ISO:
          /* there is `languageID' to check there.  We should use this */
          /* field only as a last solution when nothing else is        */
          /* available.                                                */
          /*                                                           */
          found_unicode = n;
          break;

        case TT_PLATFORM_MACINTOSH:
          /* This is a bit special because some fonts will use either    */
          /* an English language id, or a Roman encoding id, to indicate */
          /* the English version of its font name.                       */
          /*                                                             */
          if ( rec->languageID == TT_MAC_LANGID_ENGLISH )
            found_apple_english = n;
          else if ( rec->encodingID == TT_MAC_ID_ROMAN )
            found_apple_roman = n;
          break;

        case TT_PLATFORM_MICROSOFT:
          /* we only take a non-English name when there is nothing */
          /* else available in the font                            */
          /*                                                       */
          if ( found_win == -1 || ( rec->languageID & 0x3FF ) == 0x009 )
          {
            switch ( rec->encodingID )
            {
            case TT_MS_ID_SYMBOL_CS:
            case TT_MS_ID_UNICODE_CS:
            case TT_MS_ID_UCS_4:
              is_english = FT_BOOL( ( rec->languageID & 0x3FF ) == 0x009 );
              found_win  = n;
              break;

            default:
              ;
            }
          }
          break;

        default:
          ;
        }
      }
    }

    found_apple = found_apple_roman;
    if ( found_apple_english >= 0 )
      found_apple = found_apple_english;

    /* some fonts contain invalid Unicode or Macintosh formatted entries; */
    /* we will thus favor names encoded in Windows formats if available   */
    /* (provided it is an English name)                                   */
    /*                                                                    */
    convert = NULL;
    if ( found_win >= 0 && !( found_apple >= 0 && !is_english ) )
    {
      rec = face->name_table.names + found_win;
      switch ( rec->encodingID )
      {
        /* all Unicode strings are encoded using UTF-16BE */
      case TT_MS_ID_UNICODE_CS:
      case TT_MS_ID_SYMBOL_CS:
        convert = tt_name_ascii_from_utf16;
        break;

      case TT_MS_ID_UCS_4:
        /* Apparently, if this value is found in a name table entry, it is */
        /* documented as `full Unicode repertoire'.  Experience with the   */
        /* MsGothic font shipped with Windows Vista shows that this really */
        /* means UTF-16 encoded names (UCS-4 values are only used within   */
        /* charmaps).                                                      */
        convert = tt_name_ascii_from_utf16;
        break;

      default:
        ;
      }
    }
    else if ( found_apple >= 0 )
    {
      rec     = face->name_table.names + found_apple;
      convert = tt_name_ascii_from_other;
    }
    else if ( found_unicode >= 0 )
    {
      rec     = face->name_table.names + found_unicode;
      convert = tt_name_ascii_from_utf16;
    }

    if ( rec && convert )
    {
      if ( !rec->string )
      {
        FT_Stream  stream = face->name_table.stream;


        if ( FT_QNEW_ARRAY ( rec->string, rec->stringLength ) ||
             FT_STREAM_SEEK( rec->stringOffset )              ||
             FT_STREAM_READ( rec->string, rec->stringLength ) )
        {
          FT_FREE( rec->string );
          rec->stringLength = 0;
          result            = NULL;
          goto Exit;
        }
      }

      result = convert( rec, memory );
    }

  Exit:
    *name = result;
    return error;
  }


  static FT_Encoding
  sfnt_find_encoding( int  platform_id,
                      int  encoding_id )
  {
    typedef struct  TEncoding_
    {
      int          platform_id;
      int          encoding_id;
      FT_Encoding  encoding;

    } TEncoding;

    static
    const TEncoding  tt_encodings[] =
    {
      { TT_PLATFORM_ISO,           -1,                  FT_ENCODING_UNICODE },

      { TT_PLATFORM_APPLE_UNICODE, -1,                  FT_ENCODING_UNICODE },

      { TT_PLATFORM_MACINTOSH,     TT_MAC_ID_ROMAN,     FT_ENCODING_APPLE_ROMAN },

      { TT_PLATFORM_MICROSOFT,     TT_MS_ID_SYMBOL_CS,  FT_ENCODING_MS_SYMBOL },
      { TT_PLATFORM_MICROSOFT,     TT_MS_ID_UCS_4,      FT_ENCODING_UNICODE },
      { TT_PLATFORM_MICROSOFT,     TT_MS_ID_UNICODE_CS, FT_ENCODING_UNICODE },
      { TT_PLATFORM_MICROSOFT,     TT_MS_ID_SJIS,       FT_ENCODING_SJIS },
      { TT_PLATFORM_MICROSOFT,     TT_MS_ID_PRC,        FT_ENCODING_PRC },
      { TT_PLATFORM_MICROSOFT,     TT_MS_ID_BIG_5,      FT_ENCODING_BIG5 },
      { TT_PLATFORM_MICROSOFT,     TT_MS_ID_WANSUNG,    FT_ENCODING_WANSUNG },
      { TT_PLATFORM_MICROSOFT,     TT_MS_ID_JOHAB,      FT_ENCODING_JOHAB }
    };

    const TEncoding  *cur, *limit;


    cur   = tt_encodings;
    limit = cur + sizeof ( tt_encodings ) / sizeof ( tt_encodings[0] );

    for ( ; cur < limit; cur++ )
    {
      if ( cur->platform_id == platform_id )
      {
        if ( cur->encoding_id == encoding_id ||
             cur->encoding_id == -1          )
          return cur->encoding;
      }
    }

    return FT_ENCODING_NONE;
  }


  /* Fill in face->ttc_header.  If the font is not a TTC, it is */
  /* synthesized into a TTC with one offset table.              */
  static FT_Error
  sfnt_open_font( FT_Stream  stream,
                  TT_Face    face,
                  FT_Int*    face_instance_index,
                  FT_Long*   woff2_num_faces )
  {
    FT_Memory  memory = stream->memory;
    FT_Error   error;
    FT_ULong   tag, offset;

    static const FT_Frame_Field  ttc_header_fields[] =
    {
#undef  FT_STRUCTURE
#define FT_STRUCTURE  TTC_HeaderRec

      FT_FRAME_START( 8 ),
        FT_FRAME_LONG( version ),
        FT_FRAME_LONG( count   ),  /* this is ULong in the specs */
      FT_FRAME_END
    };

#ifndef FT_CONFIG_OPTION_USE_BROTLI
    FT_UNUSED( face_instance_index );
    FT_UNUSED( woff2_num_faces );
#endif


    face->ttc_header.tag     = 0;
    face->ttc_header.version = 0;
    face->ttc_header.count   = 0;

#if defined( FT_CONFIG_OPTION_USE_ZLIB )   || \
    defined( FT_CONFIG_OPTION_USE_BROTLI )
  retry:
#endif

    offset = FT_STREAM_POS();

    if ( FT_READ_ULONG( tag ) )
      return error;

#ifdef FT_CONFIG_OPTION_USE_ZLIB
    if ( tag == TTAG_wOFF )
    {
      FT_TRACE2(( "sfnt_open_font: file is a WOFF; synthesizing SFNT\n" ));

      if ( FT_STREAM_SEEK( offset ) )
        return error;

      error = woff_open_font( stream, face );
      if ( error )
        return error;

      /* Swap out stream and retry! */
      stream = face->root.stream;
      goto retry;
    }
#endif

#ifdef FT_CONFIG_OPTION_USE_BROTLI
    if ( tag == TTAG_wOF2 )
    {
      FT_TRACE2(( "sfnt_open_font: file is a WOFF2; synthesizing SFNT\n" ));

      if ( FT_STREAM_SEEK( offset ) )
        return error;

      error = woff2_open_font( stream,
                               face,
                               face_instance_index,
                               woff2_num_faces );
      if ( error )
        return error;

      /* Swap out stream and retry! */
      stream = face->root.stream;
      goto retry;
    }
#endif

    if ( tag != 0x00010000UL &&
         tag != TTAG_ttcf    &&
         tag != TTAG_OTTO    &&
         tag != TTAG_true    &&
         tag != TTAG_typ1    &&
         tag != TTAG_0xA5kbd &&
         tag != TTAG_0xA5lst &&
         tag != 0x00020000UL )
    {
      FT_TRACE2(( "  not a font using the SFNT container format\n" ));
      return FT_THROW( Unknown_File_Format );
    }

    face->ttc_header.tag = TTAG_ttcf;

    if ( tag == TTAG_ttcf )
    {
      FT_Int  n;


      FT_TRACE3(( "sfnt_open_font: file is a collection\n" ));

      if ( FT_STREAM_READ_FIELDS( ttc_header_fields, &face->ttc_header ) )
        return error;

      FT_TRACE3(( "                with %ld subfonts\n",
                  face->ttc_header.count ));

      if ( face->ttc_header.count == 0 )
        return FT_THROW( Invalid_Table );

      /* a rough size estimate: let's conservatively assume that there   */
      /* is just a single table info in each subfont header (12 + 16*1 = */
      /* 28 bytes), thus we have (at least) `12 + 4*count' bytes for the */
      /* size of the TTC header plus `28*count' bytes for all subfont    */
      /* headers                                                         */
      if ( (FT_ULong)face->ttc_header.count > stream->size / ( 28 + 4 ) )
        return FT_THROW( Array_Too_Large );

      /* now read the offsets of each font in the file */
      if ( FT_QNEW_ARRAY( face->ttc_header.offsets, face->ttc_header.count ) )
        return error;

      if ( FT_FRAME_ENTER( face->ttc_header.count * 4L ) )
        return error;

      for ( n = 0; n < face->ttc_header.count; n++ )
        face->ttc_header.offsets[n] = FT_GET_ULONG();

      FT_FRAME_EXIT();
    }
    else
    {
      FT_TRACE3(( "sfnt_open_font: synthesize TTC\n" ));

      face->ttc_header.version = 1 << 16;
      face->ttc_header.count   = 1;

      if ( FT_QNEW( face->ttc_header.offsets ) )
        return error;

      face->ttc_header.offsets[0] = offset;
    }

    return error;
  }


  FT_LOCAL_DEF( FT_Error )
  sfnt_init_face( FT_Stream      stream,
                  TT_Face        face,
                  FT_Int         face_instance_index,
                  FT_Int         num_params,
                  FT_Parameter*  params )
  {
    FT_Error      error;
    FT_Library    library         = face->root.driver->root.library;
    SFNT_Service  sfnt;
    FT_Int        face_index;
    FT_Long       woff2_num_faces = 0;


    /* for now, parameters are unused */
    FT_UNUSED( num_params );
    FT_UNUSED( params );


    sfnt = (SFNT_Service)face->sfnt;
    if ( !sfnt )
    {
      sfnt = (SFNT_Service)FT_Get_Module_Interface( library, "sfnt" );
      if ( !sfnt )
      {
        FT_ERROR(( "sfnt_init_face: cannot access `sfnt' module\n" ));
        return FT_THROW( Missing_Module );
      }

      face->sfnt       = sfnt;
      face->goto_table = sfnt->goto_table;
    }

    FT_FACE_FIND_GLOBAL_SERVICE( face, face->psnames, POSTSCRIPT_CMAPS );

#ifdef TT_CONFIG_OPTION_GX_VAR_SUPPORT
    if ( !face->mm )
    {
      /* we want the MM interface from the `truetype' module only */
      FT_Module  tt_module = FT_Get_Module( library, "truetype" );


      face->mm = ft_module_get_service( tt_module,
                                        FT_SERVICE_ID_MULTI_MASTERS,
                                        0 );
    }

    if ( !face->var )
    {
      /* we want the metrics variations interface */
      /* from the `truetype' module only          */
      FT_Module  tt_module = FT_Get_Module( library, "truetype" );


      face->var = ft_module_get_service( tt_module,
                                         FT_SERVICE_ID_METRICS_VARIATIONS,
                                         0 );
    }
#endif

    FT_TRACE2(( "SFNT driver\n" ));

    error = sfnt_open_font( stream,
                            face,
                            &face_instance_index,
                            &woff2_num_faces );
    if ( error )
      return error;

    /* Stream may have changed in sfnt_open_font. */
    stream = face->root.stream;

    FT_TRACE2(( "sfnt_init_face: %p (index %d)\n",
                (void *)face,
                face_instance_index ));

    face_index = FT_ABS( face_instance_index ) & 0xFFFF;

    /* value -(N+1) requests information on index N */
    if ( face_instance_index < 0 && face_index > 0 )
      face_index--;

    if ( face_index >= face->ttc_header.count )
    {
      if ( face_instance_index >= 0 )
        return FT_THROW( Invalid_Argument );
      else
        face_index = 0;
    }

    if ( FT_STREAM_SEEK( face->ttc_header.offsets[face_index] ) )
      return error;

    /* check whether we have a valid TrueType file */
    error = sfnt->load_font_dir( face, stream );
    if ( error )
      return error;

#ifdef TT_CONFIG_OPTION_GX_VAR_SUPPORT
    {
      FT_Memory  memory = face->root.memory;

      FT_ULong  fvar_len;

      FT_ULong  version;
      FT_ULong  offset;

      FT_UShort  num_axes;
      FT_UShort  axis_size;
      FT_UShort  num_instances;
      FT_UShort  instance_size;

      FT_Int  instance_index;

      FT_Byte*  default_values  = NULL;
      FT_Byte*  instance_values = NULL;


      instance_index = FT_ABS( face_instance_index ) >> 16;

      /* test whether current face is a GX font with named instances */
      if ( face->goto_table( face, TTAG_fvar, stream, &fvar_len ) ||
           fvar_len < 20                                          ||
           FT_READ_ULONG( version )                               ||
           FT_READ_USHORT( offset )                               ||
           FT_STREAM_SKIP( 2 ) /* reserved */                     ||
           FT_READ_USHORT( num_axes )                             ||
           FT_READ_USHORT( axis_size )                            ||
           FT_READ_USHORT( num_instances )                        ||
           FT_READ_USHORT( instance_size )                        )
      {
        version       = 0;
        offset        = 0;
        num_axes      = 0;
        axis_size     = 0;
        num_instances = 0;
        instance_size = 0;
      }

      /* check that the data is bound by the table length */
      if ( version != 0x00010000UL                    ||
           axis_size != 20                            ||
           num_axes == 0                              ||
           /* `num_axes' limit implied by 16-bit `instance_size' */
           num_axes > 0x3FFE                          ||
           !( instance_size == 4 + 4 * num_axes ||
              instance_size == 6 + 4 * num_axes )     ||
           /* `num_instances' limit implied by limited range of name IDs */
           num_instances > 0x7EFF                     ||
           offset                          +
             axis_size * num_axes          +
             instance_size * num_instances > fvar_len )
        num_instances = 0;
      else
        face->variation_support |= TT_FACE_FLAG_VAR_FVAR;

      /*
       * As documented in the OpenType specification, an entry for the
       * default instance may be omitted in the named instance table.  In
       * particular this means that even if there is no named instance
       * table in the font we actually do have a named instance, namely the
       * default instance.
       *
       * For consistency, we always want the default instance in our list
       * of named instances.  If it is missing, we try to synthesize it
       * later on.  Here, we have to adjust `num_instances' accordingly.
       */

      if ( ( face->variation_support & TT_FACE_FLAG_VAR_FVAR ) &&
           !( FT_QALLOC(  default_values, num_axes * 4 ) ||
              FT_QALLOC( instance_values, num_axes * 4 ) )     )
      {
        /* the current stream position is 16 bytes after the table start */
        FT_ULong  array_start = FT_STREAM_POS() - 16 + offset;
        FT_ULong  default_value_offset, instance_offset;

        FT_Byte*  p;
        FT_UInt   i;


        default_value_offset = array_start + 8;
        p                    = default_values;

        for ( i = 0; i < num_axes; i++ )
        {
          (void)FT_STREAM_READ_AT( default_value_offset, p, 4 );

          default_value_offset += axis_size;
          p                    += 4;
        }

        instance_offset = array_start + axis_size * num_axes + 4;

        for ( i = 0; i < num_instances; i++ )
        {
          (void)FT_STREAM_READ_AT( instance_offset,
                                   instance_values,
                                   num_axes * 4 );

          if ( !ft_memcmp( default_values, instance_values, num_axes * 4 ) )
            break;

          instance_offset += instance_size;
        }

        if ( i == num_instances )
        {
          /* no default instance in named instance table; */
          /* we thus have to synthesize it                */
          num_instances++;
        }
      }

      FT_FREE( default_values );
      FT_FREE( instance_values );

      /* we don't support Multiple Master CFFs yet; */
      /* note that `glyf' or `CFF2' have precedence */
      if ( face->goto_table( face, TTAG_glyf, stream, 0 ) &&
           face->goto_table( face, TTAG_CFF2, stream, 0 ) &&
           !face->goto_table( face, TTAG_CFF, stream, 0 ) )
        num_instances = 0;

      /* instance indices in `face_instance_index' start with index 1, */
      /* thus `>' and not `>='                                         */
      if ( instance_index > num_instances )
      {
        if ( face_instance_index >= 0 )
          return FT_THROW( Invalid_Argument );
        else
          num_instances = 0;
      }

      face->root.style_flags = (FT_Long)num_instances << 16;
    }
#endif

    face->root.num_faces  = face->ttc_header.count;
    face->root.face_index = face_instance_index;

    /* `num_faces' for a WOFF2 needs to be handled separately. */
    if ( woff2_num_faces )
      face->root.num_faces = woff2_num_faces;

    return error;
  }


#define LOAD_( x )                                          \
  do                                                        \
  {                                                         \
    FT_TRACE2(( "`" #x "' " ));                             \
    FT_TRACE3(( "-->\n" ));                                 \
                                                            \
    error = sfnt->load_ ## x( face, stream );               \
                                                            \
    FT_TRACE2(( "%s\n", ( !error )                          \
                        ? "loaded"                          \
                        : FT_ERR_EQ( error, Table_Missing ) \
                          ? "missing"                       \
                          : "failed to load" ));            \
    FT_TRACE3(( "\n" ));                                    \
  } while ( 0 )

#define LOADM_( x, vertical )                               \
  do                                                        \
  {                                                         \
    FT_TRACE2(( "`%s" #x "' ",                              \
                vertical ? "vertical " : "" ));             \
    FT_TRACE3(( "-->\n" ));                                 \
                                                            \
    error = sfnt->load_ ## x( face, stream, vertical );     \
                                                            \
    FT_TRACE2(( "%s\n", ( !error )                          \
                        ? "loaded"                          \
                        : FT_ERR_EQ( error, Table_Missing ) \
                          ? "missing"                       \
                          : "failed to load" ));            \
    FT_TRACE3(( "\n" ));                                    \
  } while ( 0 )

#define GET_NAME( id, field )                                   \
  do                                                            \
  {                                                             \
    error = tt_face_get_name( face, TT_NAME_ID_ ## id, field ); \
    if ( error )                                                \
      goto Exit;                                                \
  } while ( 0 )


  FT_LOCAL_DEF( FT_Error )
  sfnt_load_face( FT_Stream      stream,
                  TT_Face        face,
                  FT_Int         face_instance_index,
                  FT_Int         num_params,
                  FT_Parameter*  params )
  {
    FT_Error  error;
#ifdef TT_CONFIG_OPTION_POSTSCRIPT_NAMES
    FT_Error  psnames_error;
#endif

    FT_Bool  has_outline;
    FT_Bool  is_apple_sbit;

    FT_Bool  has_CBLC;
    FT_Bool  has_CBDT;
    FT_Bool  has_EBLC;
    FT_Bool  has_bloc;
    FT_Bool  has_sbix;

    FT_Bool  ignore_typographic_family    = FALSE;
    FT_Bool  ignore_typographic_subfamily = FALSE;
    FT_Bool  ignore_sbix                  = FALSE;

    SFNT_Service  sfnt = (SFNT_Service)face->sfnt;

    FT_UNUSED( face_instance_index );


    /* Check parameters */

    {
      FT_Int  i;


      for ( i = 0; i < num_params; i++ )
      {
        if ( params[i].tag == FT_PARAM_TAG_IGNORE_TYPOGRAPHIC_FAMILY )
          ignore_typographic_family = TRUE;
        else if ( params[i].tag == FT_PARAM_TAG_IGNORE_TYPOGRAPHIC_SUBFAMILY )
          ignore_typographic_subfamily = TRUE;
        else if ( params[i].tag == FT_PARAM_TAG_IGNORE_SBIX )
          ignore_sbix = TRUE;
      }
    }

    /* Load tables */

    /* We now support two SFNT-based bitmapped font formats.  They */
    /* are recognized easily as they do not include a `glyf'       */
    /* table. 