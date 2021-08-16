/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import jdk.internal.access.SharedSecrets;
import jdk.internal.loader.BootLoader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.vm.annotation.Stable;
import sun.invoke.util.BytecodeName;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.lang.invoke.LambdaForm.*;
import static java.lang.invoke.MethodHandleNatives.Constants.REF_getStatic;
import static java.lang.invoke.MethodHandleNatives.Constants.REF_putStatic;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 * Class specialization code.
 * @param <T> top class under which species classes are created.
 * @param <K> key which identifies individual specializations.
 * @param <S> species data type.
 */
/*non-public*/
abstract class ClassSpecializer<T,K,S extends ClassSpecializer<T,K,S>.SpeciesData> {
    private final Class<T> topClass;
    private final Class<K> keyType;
    private final Class<S> metaType;
    private final MemberName sdAccessor;
    private final String sdFieldName;
    private final List<MemberName> transformMethods;
    private final MethodType baseConstructorType;
    private final S topSpecies;
    private final ConcurrentHashMap<K, Object> cache = new ConcurrentHashMap<>();
    private final Factory factory;
    private @Stable boolean topClassIsSuper;

    /** Return the top type mirror, for type {@code T} */
    public final Class<T> topClass() { return topClass; }

    /** Return the key type mirror, for type {@code K} */
    public final Class<K> keyType() { return keyType; }

    /** Return the species metadata type mirror, for type {@code S} */
    public final Class<S> metaType() { return metaType; }

    /** Report the leading arguments (if any) required by every species factory.
     * Every species factory adds its own field types as additional arguments,
     * but these arguments always come first, in every factory method.
     */
    protected MethodType baseConstructorType() { return baseConstructorType; }

    /** Return the trivial species for the null sequence of arguments. */
    protected final S topSpecies() { return topSpecies; }

    /** Return the list of transform methods originally given at creation of this specializer. */
    protected final List<MemberName> transformMethods() { return transformMethods; }

    /** Return the factory object used to build and load concrete species code. */
    protected final Factory factory() { return factory; }

    /**
     * Constructor for this class specializer.
     * @param topClass type mirror for T
     * @param keyType type mirror for K
     * @param metaType type mirror for S
     * @param baseConstructorType principal constructor type
     * @param sdAccessor the method used to get the speciesData
     * @param sdFieldName the name of the species data field, inject the speciesData object
     * @param transformMethods optional list of transformMethods
     */
    protected ClassSpecializer(Class<T> topClass,
                               Class<K> keyType,
                               Class<S> metaType,
                               MethodType baseConstructorType,
                               MemberName sdAccessor,
                               String sdFieldName,
                               List<MemberName> transformMethods) {
        this.topClass = topClass;
        this.keyType = keyType;
        this.metaType = metaType;
        this.sdAccessor = sdAccessor;
        this.transformMethods = List.copyOf(transformMethods);
        this.sdFieldName = sdFieldName;
        this.baseConstructorType = baseConstructorType.changeReturnType(void.class);
        this.factory = makeFactory();
        K tsk = topSpeciesKey();
        S topSpecies = null;
        if (tsk != null && topSpecies == null) {
            // if there is a key, build the top species if needed:
            topSpecies = findSpecies(tsk);
        }
        this.topSpecies = topSpecies;
    }

    // Utilities for subclass constructors:
    protected static <T> Constructor<T> reflectConstructor(Class<T> defc, Class<?>... ptypes) {
        try {
            return defc.getDeclaredConstructor(ptypes);
        } catch (NoSuchMethodException ex) {
            throw newIAE(defc.getName()+"("+MethodType.methodType(void.class, ptypes)+")", ex);
        }
    }

    protected static Field reflectField(Class<?> defc, String name) {
        try {
            return defc.getDeclaredField(name);
        } catch (NoSuchFieldException ex)