/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.vector;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.vector.VectorSupport;

import static jdk.internal.vm.vector.VectorSupport.*;

import static jdk.incubator.vector.VectorOperators.*;

// -- This file was mechanically generated: Do not edit! -- //

@SuppressWarnings("cast")  // warning: redundant cast
final class Byte512Vector extends ByteVector {
    static final ByteSpecies VSPECIES =
        (ByteSpecies) ByteVector.SPECIES_512;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<Byte512Vector> VCLASS = Byte512Vector.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Byte> ETYPE = byte.class; // used by the JVM

    Byte512Vector(byte[] v) {
        super(v);
    }

    // For compatibility as Byte512Vector::new,
    // stored into species.vectorFactory.
    Byte512Vector(Object v) {
        this((byte[]) v);
    }

    static final Byte512Vector ZERO = new Byte512Vector(new byte[VLENGTH]);
    static final Byte512Vector IOTA = new Byte512Vector(VSPECIES.iotaArray());

    static {
        // Warm up a few species caches.
        // If we do this too much we will
        // get NPEs from bootstrap circularity.
        VSPECIES.dummyVector();
        VSPECIES.withLanes(LaneType.BYTE);
    }

    // Specialized extractors

    @ForceInline
    final @Override
    public ByteSpecies vspecies() {
        // ISSUE:  This should probably be a @Stable
        // field inside AbstractVector, rather than
        // a megamorphic method.
        return VSPECIES;
    }

    @ForceInline
    @Override
    public final Class<Byte> elementType() { return byte.class; }

    @ForceInline
    @Override
    public final int elementSize() { return Byte.SIZE; }

    @ForceInline
    @Override
    public final VectorShape shape() { return VSHAPE; }

    @ForceInline
    @Override
    public final int length() { return VLENGTH; }

    @ForceInline
    @Override
    public final int bitSize() { return VSIZE; }

    @ForceInline
    @Override
    public final int byteSize() { return VSIZE / Byte.SIZE; }

    /*package-private*/
    @ForceInline
    final @Override
    byte[] vec() {
        return (byte[])getPayload();
    }

    // Virtualized constructors

    @Override
    @ForceInline
    public final Byte512Vector broadcast(byte e) {
        return (Byte512Vector) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final Byte512Vector broadcast(long e) {
        return (Byte512Vector) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    Byte512Mask maskFromArray(boolean[] bits) {
        return new Byte512Mask(bits);
    }

    @Override
    @ForceInline
    Byte512Shuffle iotaShuffle() { return Byte512Shuffle.IOTA; }

    @ForceInline
    Byte512Shuffle iotaShuffle(int start, int step, boolean wrap) {
      if (wrap) {
        return (Byte512Shuffle)VectorSupport.shuffleIota(ETYPE, Byte512Shuffle.class, VSPECIES, VLENGTH, start, step, 1,
                (l, lstart, lstep, s) -> s.shuffleFromOp(i -> (VectorIntrinsics.wrapToRange(i*lstep + lstart, l))));
      } else {
        return (Byte512Shuffle)VectorSupport.shuffleIota(ETYPE, Byte512Shuffle.class, VSPECIES, VLENGTH, start, step, 0,
                (l, lstart, lstep, s) -> s.shuffleFromOp(i -> (i*lstep + lstart)));
      }
    }

    @Override
    @ForceInline
    Byte512Shuffle shuffleFromBytes(byte[] reorder) { return new Byte512Shuffle(reorder); }

    @Override
    @ForceInline
    Byte512Shuffle shuffleFromArray(int[] indexes, int i) { return new Byte512Shuffle(indexes, i); }

    @Override
    @ForceInline
    Byte512Shuffle shuffleFromOp(IntUnaryOperator fn) { return new Byte512Shuffle(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    Byte512Vector vectorFactory(byte[] vec) {
        return new Byte512Vector(vec);
    }

    @ForceInline
    final @Override
    Byte512Vector asByteVectorRaw() {
        return (Byte512Vector) super.asByteVectorRawTemplate();  // specialize
    }

    @ForceInline
    final @Override
    AbstractVector<?> asVectorRaw(LaneType laneType) {
        return super.asVectorRawTemplate(laneType);  // specialize
    }

    // Unary operator

    @ForceInline
    final @Override
    Byte512Vector uOp(FUnOp f) {
        return (Byte512Vector) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    Byte512Vector uOp(VectorMask<Byte> m, FUnOp f) {
        return (Byte512Vector)
            super.uOpTemplate((Byte512Mask)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    Byte512Vector bOp(Vector<Byte> v, FBinOp f) {
        return (Byte512Vector) super.bOpTemplate((Byte512Vector)v, f);  // specialize
    }

    @ForceInline
    final @Override
    Byte512Vector bOp(Vector<Byte> v,
                     VectorMask<Byte> m, FBinOp f) {
        return (Byte512Vector)
            super.bOpTemplate((Byte512Vector)v, (Byte512Mask)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    Byte512Vector tOp(Vector<Byte> v1, Vector<Byte> v2, FTriOp f) {
        return (Byte512Vector)
            super.tOpTemplate((Byte512Vector)v1, (Byte512Vector)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    Byte512Vector tOp(Vector<Byte> v1, Vector<Byte> v2,
                     VectorMask<Byte> m, FTriOp f) {
        return (Byte512Vector)
            super.tOpTemplate((Byte512Vector)v1, (Byte512Vector)v2,
                              (Byte512Mask)m, f);  // specialize
    }

    @ForceInline
    final @Override
    byte rOp(byte v, VectorMask<Byte> m, FBinOp f) {
        return super.rOpTemplate(v, m, f);  // specialize
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> convertShape(VectorOperators.Conversion<Byte,F> conv,
                           VectorSpecies<F> rsp, int part) {
        return super.convertShapeTemplate(conv, rsp, part);  // specialize
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> reinterpretShape(VectorSpecies<F> toSpecies, int part) {
        return super.reinterpretShapeTemplate(toSpecies, part);  // special