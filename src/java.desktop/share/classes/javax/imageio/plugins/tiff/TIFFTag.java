/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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
package javax.imageio.plugins.tiff;

import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A class defining the notion of a TIFF tag.  A TIFF tag is a key
 * that may appear in an Image File Directory (IFD).  In the IFD
 * each tag has some data associated with it, which may consist of zero
 * or more values of a given data type. The combination of a tag and a
 * value is known as an IFD Entry or TIFF Field.
 *
 * <p> The actual tag values used in the root IFD of a standard ("baseline")
 * tiff stream are defined in the {@link BaselineTIFFTagSet
 * BaselineTIFFTagSet} class.
 *
 * @since 9
 * @see   BaselineTIFFTagSet
 * @see   TIFFField
 * @see   TIFFTagSet
 */
public class TIFFTag {

    // TIFF 6.0 + Adobe PageMaker(R) 6.0 TIFF Technical Notes 1 IFD data type

    /** Flag for 8 bit unsigned integers. */
    public static final int TIFF_BYTE        =  1;

    /** Flag for null-terminated ASCII strings. */
    public static final int TIFF_ASCII       =  2;

    /** Flag for 16 bit unsigned integers. */
    public static final int TIFF_SHORT       =  3;

    /** Flag for 32 bit unsigned integers. */
    public static final int TIFF_LONG        =  4;

    /** Flag for pairs of 32 bit unsigned integers. */
    public static final int TIFF_RATIONAL    =  5;

    /** Flag for 8 bit signed integers. */
    public static final int TIFF_SBYTE       =  6;

    /** Flag for 8 bit uninterpreted bytes. */
    public static final int TIFF_UNDEFINED   =  7;

    /** Flag for 16 bit signed integers. */
    public static final int TIFF_SSHORT      =  8;

    /** Flag for 32 bit signed integers. */
    public static final int TIFF_SLONG       =  9;

    /** Flag for pairs of 32 bit signed integers. */
    public static final int TIFF_SRATIONAL   = 10;

    /** Flag for 32 bit IEEE floats. */
    public static final int TIFF_FLOAT       = 11;

    /** Flag for 64 bit IEEE doubles. */
    public static final int TIFF_DOUBLE      = 12;

    /**
     * Flag for IFD pointer defined in TIFF Tech Note 1 in
     * TIFF Specification Supplement 1.
     */
    public static final int TIFF_IFD_POINTER = 13;

    /**
     * The numerically smallest constant representing a TIFF data type.
     */
    public static final int MIN_DATATYPE = TIFF_BYTE;

    /**
     * The numerically largest constant representing a TIFF data type.
     */
    public static final int MAX_DATATYPE = TIFF_IFD_POINTER;

    /**
     * The name assigned to a tag with an unknown tag number. Such
     * a tag may be created for example when reading an IFD and a
     * tag number is encountered which is not in any of the
     * {@code TIFFTagSet}s known to the reader.
     */
    public static final String UNKNOWN_TAG_NAME = "UnknownTag";

    /**
     * Disallowed data type mask.
     */
    private static final int DISALLOWED_DATATYPES_MASK = ~0x3fff;

    private static final int[] SIZE_OF_TYPE = {
        0, //  0 = n/a
   