/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * jpegint.h
 *
 * Copyright (C) 1991-1997, Thomas G. Lane.
 * This file is part of the Independent JPEG Group's software.
 * For conditions of distribution and use, see the accompanying README file.
 *
 * This file provides common declarations for the various JPEG modules.
 * These declarations are considered internal to the JPEG library; most
 * applications using the library shouldn't need to include this file.
 */


/* Declarations for both compression & decompression */

typedef enum {                  /* Operating modes for buffer controllers */
        JBUF_PASS_THRU,         /* Plain stripwise operation */
        /* Remaining modes require a full-image buffer to have been created */
        JBUF_SAVE_SOURCE,       /* Run source subobject only, save output */
        JBUF_CRANK_DEST,        /* Run dest subobject only, using saved data */
        JBUF_SAVE_AND_PASS      /* Run both subobjects, save output */
} J_BUF_MODE;

/* Values of global_state field (jdapi.c has some dependencies on ordering!) */
#define CSTATE_START    100     /* after create_compress */
#define CSTATE_SCANNING 101     /* start_compress done, write_scanlines OK */
#define CSTATE_RAW_OK   102     /* start_compress done, write_raw_data OK */
#define CSTATE_WRCOEFS  103     /* jpeg_write_coefficients done */
#define DSTATE_START    200     /* after create_decompress */
#define DSTATE_INHEADER 201     /* reading header markers, no SOS yet */
#define DSTATE_READY    202     /* found SOS, ready for start_decompress */
#define DSTATE_PRELOAD  203     /* reading multiscan file in start_decompress*/
#define DSTATE_PRESCAN  204     /* performing dummy pass for 2-pass quant */
#define DSTATE_SCANNING 205     /* start_decompress done, read_scanlines OK */
#define DSTATE_RAW_OK   206     /* start_decompress done, read_raw_data OK */
#define DSTATE_BUFIMAGE 207     /* expecting jpeg_start_output */
#define DSTATE_BUFPOST  208     /* looking for SOS/EOI in jpeg_finish_output */
#define DSTATE_RDCOEFS  209     /* reading file in jpeg_read_coefficients */
#define DSTATE_STOPPING 210     /* looking for EOI in jpeg_finish_decompress */


/* Declarations for compression modules */

/* Master control module */
struct jpeg_comp_master {
  JMETHOD(void, prepare_for_pass, (j_compress_ptr cinfo));
  JMETHOD(void, pass_startup, (j_compress_ptr cinfo));
  JMETHOD(void, finish_pass, (j_compress_ptr cinfo));

  /* State variables made visible to other modules */
  boolean call_pass_startup;    /* True if pass_startup must be called */
  boolean is_last_pass;         /* True during last pass */
};

/* Main buffer control (downsampled-data buffer) */
struct jpeg_c_main_controller {
  JMETHOD(void, start_pass, (j_compress_ptr cinfo, J_BUF_MODE pass_mode));
  JMETHOD(void, process_data, (j_compress_ptr cinfo,
                               JSAMPARRAY input_buf, JDIMENSION *in_row_ctr,
                               JDIMENSION in_rows_avail));
};

/* Compression preprocessing (downsampling input buffer control) */
struct jpeg_c_prep_controller {
  JMETHOD(void, start_pass, (j_compress_ptr cinfo, J_BUF_MODE pass_mode));
  JMETHOD(void, pre_process_data, (j_compress_ptr cinfo,
                                   JSAMPARRAY input_buf,
                                   JDIMENSION *in_row_ctr,
                                   JDIMENSION in_rows_avail,
                                   JSAMPIMAGE output_buf,
                                   JDIMENSION *out_row_group_ctr,
                                   JDIMENSION out_row_groups_avail));
};

/* Coefficient buffer control */
struct jpeg_c_coef_controller {
  JMETHOD(void, start_pass, (j_compress_ptr cinfo, J_BUF_MODE pass_mode));
  JMETHOD(boolean, compress_data, (j_compress_ptr cinfo,
                                   JSAMPIMAGE input_buf));
};

/* Colorspace conversion */
struct jpeg_color_converter {
  JMETHOD(void, start_pass, (j_compress_ptr cinfo));
  JMETHOD(void, color_convert, (j_compress_ptr cinfo,
                                JSAMPARRAY input_buf, JSAMPIMAGE output_buf,
                                JDIMENSION output_row, int num_rows));
};

/* Downsampling */
struct jpeg_downsampler {
  JMETHOD(void, start_pass, (j_compress_ptr cinfo));
  JMETHOD(void, downsample, (j_compress_ptr cinfo,
                             JSAMPIMAGE input_buf, JDIMENSION in_row_index,
                             JSAMPIMAGE output_buf,
                             JDIMENSION out_row_group_index));

  boolean need_context_rows;    /* TRUE if need rows above & below */
};

/* Forward DCT (also controls coefficient quantization) */
struct jpeg_forward_dct {
  JMETHOD(void, start_pass, (j_compress_ptr cinfo));
  /* perhaps this should be an array??? */
  JMETHOD(void, forward_DCT, (j_compress_ptr cinfo,
                              jpeg_component_info * compptr,
                              JSAMPARRAY sample_data, JBLOCKROW coef_blocks,
                              JDIMENSION start_row, JDIMENSION start_col,
                              JDIMENSION num_blocks));
};

/* Entropy encoding */
struct jpeg_entropy_encoder {
  JMETHOD(void, start_pass, (j_compress_ptr cinfo, boolean gather_statistics));
  JMETHOD(boolean, encode_mcu, (j_compress_ptr cinfo, JBLOCKROW *MCU_data));
  JMETHOD(void, finish_pass, (j_compress_ptr cinfo));
};

/* Marker writing */
struct jpeg_marker_writer {
  JMETHOD(void, write_file_header, (j_compress_ptr cinfo));
  JMETHOD(void, write_frame_header, (j_compress_ptr cinfo));
  JMETHOD(void, write_scan_header, (j_compress_ptr cinfo));
  JMETHOD(void, write_file_trailer, (j_compress_ptr cinfo));
  JMETHOD(void, write_tables_only, (j_compress_ptr cinfo));
  /* These routines are exported to allow insertion of extra markers */
  /* Probably only COM and APPn markers should be written this way */
  JMETHOD(void, write_marker_header, (j_compress_ptr cinfo, int marker,
                        