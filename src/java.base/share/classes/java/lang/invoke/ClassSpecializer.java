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
        } catch (NoSuchFieldException ex) {
            throw newIAE(defc.getName()+"."+name, ex);
        }
    }

    private static RuntimeException newIAE(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }

    private static final Function<Object, Object> CREATE_RESERVATION = new Function<>() {
        @Override
        public Object apply(Object key) {
            return new Object();
        }
    };

    public final S findSpecies(K key) {
        // Note:  Species instantiation may throw VirtualMachineError because of
        // code cache overflow.  If this happens the species bytecode may be
        // loaded but not linked to its species metadata (with MH's etc).
        // That will cause a throw out of Factory.loadSpecies.
        //
        // In a later attempt to get the same species, the already-loaded
        // class will be present in the system dictionary, causing an
        // error when the species generator tries to reload it.
        // We try to detect this case and link the pre-existing code.
        //
        // Although it would be better to start fresh by loading a new
        // copy, we have to salvage the previously loaded but broken code.
        // (As an alternative, we might spin a new class with a new name,
        // or use the anonymous class mechanism.)
        //
        // In the end, as long as everybody goes through this findSpecies method,
        // it will ensure only one SpeciesData will be set successfully on a
        // concrete class if ever.
        // The concrete class is published via SpeciesData instance
        // returned here only after the class and species data are linked together.
        Object speciesDataOrReservation = cache.computeIfAbsent(key, CREATE_RESERVATION);
        // Separating the creation of a placeholder SpeciesData instance above
        // from the loading and linking a real one below ensures we can never
        // accidentally call computeIfAbsent recursively.
        S speciesData;
        if (speciesDataOrReservation.getClass() == Object.class) {
            synchronized (speciesDataOrReservation) {
                Object existingSpeciesData = cache.get(key);
                if (existingSpeciesData == speciesDataOrReservation) { // won the race
                    // create a new SpeciesData...
                    speciesData = newSpeciesData(key);
                    // load and link it...
                    speciesData = factory.loadSpecies(speciesData);
                    if (!cache.replace(key, existingSpeciesData, speciesData)) {
                        throw newInternalError("Concurrent loadSpecies");
                    }
                } else { // lost the race; the retrieved existingSpeciesData is the final
                    speciesData = metaType.cast(existingSpeciesData);
                }
            }
        } else {
            speciesData = metaType.cast(speciesDataOrReservation);
        }
        assert(speciesData != null && speciesData.isResolved());
        return speciesData;
    }

    /**
     * Meta-data wrapper for concrete subtypes of the top class.
     * Each concrete subtype corresponds to a given sequence of basic field types (LIJFD).
     * The fields are immutable; their values are fully specified at object construction.
     * Each species supplies an array of getter functions which may be used in lambda forms.
     * A concrete value is always constructed from the full tuple of its field values,
     * accompanied by the required constructor parameters.
     * There *may* also be transforms which cloning a species instance and
     * either replace a constructor parameter or add one or more new field values.
     * The shortest possible species has zero fields.
     * Subtypes are not interrelated among themselves by subtyping, even though
     * it would appear that a shorter species could serve as a supertype of a
     * longer one which extends it.
     */
    public abstract class SpeciesData {
        // Bootstrapping requires circular relations Class -> SpeciesData -> Class
        // Therefore, we need non-final links in the chain.  Use @Stable fields.
        private final K key;
        private final List<Class<?>> fieldTypes;
        @Stable private Class<? extends T> speciesCode;
        @Stable private List<MethodHandle> factories;
        @Stable private List<MethodHandle> getters;
        @Stable private List<LambdaForm.NamedFunction> nominalGetters;
        @Stable private final MethodHandle[] transformHelpers = new MethodHandle[transformMethods.size()];

        protected SpeciesData(K key) {
            this.key = keyType.cast(Objects.requireNonNull(key));
            List<Class<?>> types = deriveFieldTypes(key);
            this.fieldTypes = List.copyOf(types);
        }

        public final K key() {
            return key;
        }

        protected final List<Class<?>> fieldTypes() {
            return fieldTypes;
        }

        protected final int fieldCount() {
            return fieldTypes.size();
        }

        protected ClassSpecializer<T,K,S> outer() {
            return ClassSpecializer.this;
        }

        protected final boolean isResolved() {
            return speciesCode != null && factories != null && !factories.isEmpty();
        }

        @Override public String toString() {
            return metaType.getSimpleName() + "[" + key.toString() + " => " + (isResolved() ? speciesCode.getSimpleName() : "UNRESOLVED") + "]";
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ClassSpecializer.SpeciesData)) {
                return false;
            }
            @SuppressWarnings("rawtypes")
            ClassSpecializer.SpeciesData that = (ClassSpecializer.SpeciesData) obj;
            return this.outer() == that.outer() && this.key.equals(that.key);
        }

        /** Throws NPE if this species is not yet resolved. */
        protected final Class<? extends T> speciesCode() {
            return Objects.requireNonNull(speciesCode);
        }

        /**
         * Return a {@link MethodHandle} which can get the indexed field of this species.
         * The return type is the type of the species field it accesses.
         * The argument type is the {@code fieldHolder} class of this species.
         */
        protected MethodHandle getter(int i) {
            return getters.get(i);
        }

        /**
         * Return a {@link LambdaForm.Name} containing a {@link LambdaForm.NamedFunction} that
         * represents a MH bound to a generic invoker, which in turn forwards to the corresponding
         * getter.
         */
        protected LambdaForm.NamedFunction getterFunction(int i) {
            LambdaForm.NamedFunction nf = nominalGetters.get(i);
            assert(nf.memberDeclaringClassOrNull() == speciesCode());
            assert(nf.returnType() == BasicType.basicType(fieldTypes.get(i)));
            return nf;
        }

        protected List<LambdaForm.NamedFunction> getterFunctions() {
            return nominalGetters;
        }

        protected List<MethodHandle> getters() {
            return getters;
        }

        protected MethodHandle factory() {
            return factories.get(0);
        }

        protected MethodHandle transformHelper(int whichtm) {
            MethodHandle mh = transformHelpers[whichtm];
            if (mh != null)  return mh;
            mh = deriveTransformHelper(transformMethods().get(whichtm), whichtm);
            // Do a little type checking before we start using the MH.
            // (It will be called with invokeBasic, so this is our only chance.)
            final MethodType mt = transformHelperType(whichtm);
            