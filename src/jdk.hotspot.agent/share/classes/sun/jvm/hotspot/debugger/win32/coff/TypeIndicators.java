/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package sun.jvm.hotspot.debugger.win32.coff;

/** Enumerates the types of COFF object file relocations for all
    currently-supported processors. (Some of the descriptions are
    taken directly from Microsoft's documentation and are copyrighted
    by Microsoft.) */

public interface TypeIndicators {
  //
  // I386 processors
  //

  /** This relocation is ignored. */
  public static final short IMAGE_REL_I386_ABSOLUTE = 0x0000;
  /** Not supported. */
  public static final short IMAGE_REL_I386_DIR16 = (short) 0x0001;
  /** Not supported. */
  public static final short IMAGE_REL_I386_REL16 = (short) 0x0002;
  /** The target?s 32-bit virtual address. */
  public static final short IMAGE_REL_I386_DIR32 = (short) 0x0006;
  /** The target?s 32-bit relative virtual address. */
  public static final short IMAGE_REL_I386_DIR32NB = (short) 0x0007;
  /** Not supported. */
  public static final short IMAGE_REL_I386_SEG12 = (short) 0x0009;
  /** The 16-bit-section index of the section containing the
      target. This is used to support debugging information. */
  public static final short IMAGE_REL_I386_SECTION = (short) 0x000A;
  /** The 32-bit offset of the target from the beginning of its
      section. This is used to support debugging information as well
      as static thread local storage. */
  public static final short IMAGE_REL_I386_SECREL = (short) 0x000B;
  /** The 32-bit relative displacement to the target. This supports
      the x86 relative branch and call instructions. */
  public static final short IMAGE_REL_I386_REL32 = (short) 0x0014;

  //
  // MIPS processors
  //

  /** This relocation is ignored. */
  public static final short IMAGE_REL_MIPS_ABSOLUTE = (short) 0x0000;
  /** The high 16 bits of the target's 32-bit virtual address. */
  public static final short IMAGE_REL_MIPS_REFHALF = (short) 0x0001;
  /** The target's 32-bit virtual address. */
  public static final short IMAGE_REL_MIPS_REFWORD = (short) 0x0002;
  /** The low 26 bits of the target's virtual address. This
      supports the MIPS J and JAL instructions. */
  public static final short IMAGE_REL_MIPS_JMPADDR = (short) 0x0003;
  /** The high 16 bits of the target's 32-bit virtual address. Used
      for the first instruction in a two-instruction sequence that
      loads a full address. This relocation must be immediately
      followed by a PAIR relocations whose SymbolTableIndex contains a
      signed 16-bit displacement which is added to the upper 16 bits
      taken from the location being relocated. */
  public static final short IMAGE_REL_MIPS_REFHI = (short) 0x0004;
  /** The low 16 bits of the target's virtual address. */
  public static final short IMAGE_REL_MIPS_REFLO = (short) 0x0005;
  /** 16-bit signed displacement of the target relative to the Global
      Pointer (GP) register. */
  public static final short IMAGE_REL_MIPS_GPREL = (short) 0x0006;
  /** Same as IMAGE_REL_MIPS_GPREL. */
  public static final short IMAGE_REL_MIPS_LITERAL = (short) 0x0007;
  /** The 16-bit section index of the section containing the target.
      This is used to support debugging information. */
  public static final short IMAGE_REL_MIPS_SECTION = (short) 0x000A;
  /** The 32-bit offset of the target from the beginning of its
      section. This is used to support debugging information as well
      as static thread local storage. */
  public static final short IMAGE_REL_MIPS_SECREL = (short) 0x000B;
  /** The low 16 bits of the 32-bit offset of the target from the
      beginning of its section. */
  public static final short IMAGE_REL_MIPS_SECRELLO = (short) 0x000C;
  /** The high 16 bits of the 32-bit offset of the target from the
      beginning of its section. A PAIR relocation must immediately
      follow this on. The SymbolTableIndex of the PAIR relocation
      contains a signed 16-bit displacement, which is added to the
      upper 16 bits taken from the location being relocated. */
  public static final short IMAGE_REL_MIPS_SECRELHI = (short) 0x000D;
  /** The low 26 bits of the target's virtual address. This supports
      the MIPS16 JAL instruction. */
  public static final short IMAGE_REL_MIPS_JMPADDR16 = (short) 0x0010;
  /** The target's 32-bit relative virtual address. */
  public static final short IMAGE_REL_MIPS_REFWORDNB = (short) 0x0022;
  /** This relocation is only valid when it immediately follows a
      REFHI or SECRELHI relocation. Its SymbolTableIndex contains a
      displacement and not an index into the symbol table. */
  public static final short IMAGE_REL_MIPS_PAIR = (short) 0x0025;

  //
  // Alpha processors
  //

  /** This relocation is ignored. */
  public static final short IMAGE_REL_ALPHA_ABSOLUTE = (short) 0x0000;
  /** The target's 32-bit virtual address. This fixup is illegal in a
      PE32+ image unless 