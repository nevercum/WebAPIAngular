/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.font;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharsetDecoder;

public class X11Johab extends Charset {
    public X11Johab () {
        super("X11Johab", null);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    public CharsetDecoder newDecoder() {
        throw new Error("Decoder is not supported by X11Johab Charset");
    }

    public boolean contains(Charset cs) {
        return cs instanceof X11GB18030_1;
    }

    private static class Encoder extends DoubleByteEncoder {
        public Encoder(Charset cs) {
            super(cs, index1, index2);
        }

        public boolean isLegalReplacement(byte[] repl) {
            return true;
        }

        private static final String innerIndex0=
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\uD9AE\u0000\u0000\uD9B4\u0000\u0000\uD967"+
            "\uD937\u0000\uDCA3\uD97C\u0000\u0000\u0000\u0000"+
            "\uD956\uD94E\uDD99\uDD9A\uD9A5\u0000\uD9D2\u0000"+
            "\uD9AC\uDD98\uDCAC\uD97D\uDCF9\uDCF6\uDCFA\uD9AF"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\uDCA1\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\uDCA2\u0000\u0000\u0000\u0000\u0000\u0000\uD94F"+
            "\uDCAA\u0000\u0000\u0000\u0000\u0000\uDCAD\uDD3C"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\uDD31\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\uDD33\u0000\u0000\u0000\u0000\u0000\u0000\uD950"+
            "\uDD3A\u0000\u0000\u0000\u0000\u0000\uDD3D\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\uDD32\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\uDCA4\uDD34"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\uDD35\uDCA6\uDD36\u0000\u0000\u0000\u0000"+
            "\uDD37\u0000\u0000\u0000\u0000\u0000\u0000\uDCA8"+
            "\uDD38\uDCA9\uDD39\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\uDD40\uDCAF\uDD3F\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\uDCAB\uDD3B\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\uDCAE\uDD3E"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
            "\u0000\u0000\u0000\u0000\u000