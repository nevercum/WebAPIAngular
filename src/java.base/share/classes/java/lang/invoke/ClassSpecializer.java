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
            mh = mh.asType(mt);
            return transformHelpers[whichtm] = mh;
        }

        private final MethodType transformHelperType(int whichtm) {
            MemberName tm = transformMethods().get(whichtm);
            MethodType tmt = tm.getMethodType();
            ArrayList<Class<?>> args = new ArrayList<>();
            ArrayList<Class<?>> fields = new ArrayList<>();
            Collections.addAll(args, tmt.ptypes());
            fields.addAll(fieldTypes());
            List<Class<?>> helperArgs = deriveTransformHelperArguments(tm, whichtm, args, fields);
            return MethodType.methodType(tmt.returnType(), helperArgs);
        }

        // Hooks for subclasses:

        /**
         * Given a key, derive the list of field types, which all instances of this
         * species must store.
         */
        protected abstract List<Class<?>> deriveFieldTypes(K key);

        /**
         * Given the index of a method in the transforms list, supply a factory
         * method that takes the arguments of the transform, plus the local fields,
         * and produce a value of the required type.
         * You can override this to return null or throw if there are no transforms.
         * This method exists so that the transforms can be "grown" lazily.
         * This is necessary if the transform *adds* a field to an instance,
         * which sometimes requires the creation, on the fly, of an extended species.
         * This method is only called once for any particular parameter.
         * The species caches the result in a private array.
         *
         * @param transform the transform being implemented
         * @param whichtm the index of that transform in the original list of transforms
         * @return the method handle which creates a new result from a mix of transform
         * arguments and field values
         */
        protected abstract MethodHandle deriveTransformHelper(MemberName transform, int whichtm);

        /**
         * During code generation, this method is called once per transform to determine
         * what is the mix of arguments to hand to the transform-helper.  The bytecode
         * which marshals these arguments is open-coded in the species-specific transform.
         * The two lists are of opaque objects, which you shouldn't do anything with besides
         * reordering them into the output list.  (They are both mutable, to make editing
         * easier.)  The imputed types of the args correspond to the transform's parameter
         * list, while the imputed types of the fields correspond to the species field types.
         * After code generation, this method may be called occasionally by error-checking code.
         *
         * @param transform the transform being implemented
         * @param whichtm the index of that transform in the original list of transforms
         * @param args a list of opaque objects representing the incoming transform arguments
         * @param fields a list of opaque objects representing the field values of the receiver
         * @param <X> the common element type of the various lists
         * @return a new list
         */
        protected abstract <X> List<X> deriveTransformHelperArguments(MemberName transform, int whichtm,
                                                                      List<X> args, List<X> fields);

        /** Given a key, generate the name of the class which implements the species for that key.
         * This algorithm must be stable.
         *
         * @return class name, which by default is {@code outer().topClass().getName() + "$Species_" + deriveTypeString(key)}
         */
        protected String deriveClassName() {
            return outer().topClass().getName() + "$Species_" + deriveTypeString();
        }

        /**
         * Default implementation collects basic type characters,
         * plus possibly type names, if some types don't correspond
         * to basic types.
         *
         * @return a string suitable for use in a class name
         */
        protected String deriveTypeString() {
            List<Class<?>> types = fieldTypes();
            StringBuilder buf = new StringBuilder();
            StringBuilder end = new StringBuilder();
            for (Class<?> type : types) {
                BasicType basicType = BasicType.basicType(type);
                if (basicType.basicTypeClass() == type) {
                    buf.append(basicType.basicTypeChar());
                } else {
                    buf.append('V');
                    end.append(classSig(type));
                }
            }
            String typeString;
            if (end.length() > 0) {
                typeString = BytecodeName.toBytecodeName(buf.append("_").append(end).toString());
            } else {
                typeString = buf.toString();
            }
            return LambdaForm.shortenSignature(typeString);
        }

        /**
         * Report what immediate super-class to use for the concrete class of this species.
         * Normally this is {@code topClass}, but if that is an interface, the factory must override.
         * The super-class must provide a constructor which takes the {@code baseConstructorType} arguments, if any.
         * This hook also allows the code generator to use more than one canned supertype for species.
         *
         * @return the super-class of the class to be generated
         */
        protected Class<? extends T> deriveSuperClass() {
            final Class<T> topc = topClass();
            if (!topClassIsSuper) {
                try {
                    final Constructor<T> con = reflectConstructor(topc, baseConstructorType().ptypes());
                    if (!topc.isInterface() && !Modifier.isPrivate(con.getModifiers())) {
                        topClassIsSuper = true;
                    }
                } catch (Exception|InternalError ex) {
                    // fall through...
                }
                if (!topClassIsSuper) {
                    throw newInternalError("must override if the top class cannot serve as a super class");
                }
            }
            return topc;
        }
    }

    protected abstract S newSpeciesData(K key);

    protected K topSpeciesKey() {
        return null;  // null means don't report a top species
    }

    /**
     * Code generation support for instances.
     * Subclasses can modify the behavior.
     */
    public class Factory {
        /**
         * Constructs a factory.
         */
        Factory() {}

        /**
         * Get a concrete subclass of the top class for a given combination of bound types.
         *
         * @param speciesData the species requiring the class, not yet linked
         * @return a linked version of the same species
         */
        S loadSpecies(S speciesData) {
            String className = speciesData.deriveClassName();
            assert(className.indexOf('/') < 0) : className;
            Class<?> salvage = null;
            try {
                salvage = BootLoader.loadClassOrNull(className);
            } catch (Error ex) {
                // ignore
            } finally {
                traceSpeciesType(className, salvage);
            }
            final Class<? extends T> speciesCode;
            if (salvage != null) {
                speciesCode = salvage.asSubclass(topClass());
                linkSpeciesDataToCode(speciesData, speciesCode);
                linkCodeToSpeciesData(speciesCode, speciesData, true);
            } else {
                // Not pregenerated, generate the class
                try {
                    speciesCode = generateConcreteSpeciesCode(className, speciesData);
                    // This operation causes a lot of churn:
                    linkSpeciesDataToCode(speciesData, speciesCode);
                    // This operation commits the relation, but causes little churn:
                    linkCodeToSpeciesData(speciesCode, speciesData, false);
                } catch (Error ex) {
                    // We can get here if there is a race condition loading a class.
                    // Or maybe we are out of resources.  Back out of the CHM.get and retry.
                    throw ex;
                }
            }

            if (!speciesData.isResolved()) {
                throw newInternalError("bad species class linkage for " + className + ": " + speciesData);
            }
            assert(speciesData == loadSpeciesDataFromCode(speciesCode));
            return speciesData;
        }

        /**
         * Generate a concrete subclass of the top class for a given combination of bound types.
         *
         * A concrete species subclass roughly matches the following schema:
         *
         * <pre>
         * class Species_[[types]] extends [[T]] {
         *     final [[S]] speciesData() { return ... }
         *     static [[T]] make([[fields]]) { return ... }
         *     [[fields]]
         *     final [[T]] transform([[args]]) { return ... }
         * }
         * </pre>
         *
         * The {@code [[types]]} signature is precisely the key for the species.
         *
         * The {@code [[fields]]} section consists of one field definition per character in
         * the type signature, adhering to the naming schema described in the definition of
         * {@link #chooseFieldName}.
         *
         * For example, a concrete species for two references and one integral bound value
         * has a shape like the following:
         *
         * <pre>
         * class TopClass {
         *     ...
         *     private static final class Species_LLI extends TopClass {
         *         final Object argL0;
         *         final Object argL1;
         *         final int argI2;
         *         private Species_LLI(CT ctarg, ..., Object argL0, Object argL1, int argI2) {
         *             super(ctarg, ...);
         *             this.argL0 = argL0;
         *             this.argL1 = argL1;
         *             this.argI2 = argI2;
         *         }
         *         final SpeciesData speciesData() { return BMH_SPECIES; }
         *         &#64;Stable static SpeciesData BMH_SPECIES; // injected afterwards
         *         static TopClass make(CT ctarg, ..., Object argL0, Object argL1, int argI2) {
         *             return new Species_LLI(ctarg, ..., argL0, argL1, argI2);
         *         }
         *         final TopClass copyWith(CT ctarg, ...) {
         *             return new Species_LLI(ctarg, ..., argL0, argL1, argI2);
         *         }
         *         // two transforms, for the sake of illustration:
         *         final TopClass copyWithExtendL(CT ctarg, ..., Object narg) {
         *             return BMH_SPECIES.transform(L_TYPE).invokeBasic(ctarg, ..., argL0, argL1, argI2, narg);
         *         }
         *         final TopClass copyWithExtendI(CT ctarg, ..., int narg) {
         *             return BMH_SPECIES.transform(I_TYPE).invokeBasic(ctarg, ..., argL0, argL1, argI2, narg);
         *         }
         *     }
         * }
         * </pre>
         *
         * @param className of the species
         * @param speciesData what species we are generating
         * @return the generated concrete TopClass class
         */
        @SuppressWarnings("removal")
        Class<? extends T> generateConcreteSpeciesCode(String className, ClassSpecializer<T,K,S>.SpeciesData speciesData) {
            byte[] classFile = generateConcreteSpeciesCodeFile(className, speciesData);

            // load class
            InvokerBytecodeGenerator.maybeDump(classBCName(className), classFile);
            ClassLoader cl = topClass.getClassLoader();
            ProtectionDomain pd = null;
            if (cl != null) {
                pd = AccessController.doPrivileged(
                        new PrivilegedAction<>() {
                            @Override
                            public ProtectionDomain run() {
                                return topClass().getProtectionDomain();
                            }
                        });
            }
            Class<?> speciesCode = SharedSecrets.getJavaLangAccess()
                    .defineClass(cl, className, classFile, pd, "_ClassSpecializer_generateConcreteSpeciesCode");
            return speciesCode.asSubclass(topClass());
        }

        // These are named like constants because there is only one per specialization scheme:
        private final String SPECIES_DATA = classBCName(metaType);
        private final String SPECIES_DATA_SIG = classSig(SPECIES_DATA);
        private final String SPECIES_DATA_NAME = sdAccessor.getName();
        private final int SPECIES_DATA_MODS = sdAccessor.getModifiers();
        private final List<String> TRANSFORM_NAMES;  // derived from transformMethods
        private final List<MethodType> TRANSFORM_TYPES;
        private final List<Integer> TRANSFORM_MODS;
        {
            // Tear apart transformMethods to get the names, types, and modifiers.
            List<String> tns = new ArrayList<>();
            List<MethodType> tts = new ArrayList<>();
            List<Integer> tms = new ArrayList<>();
            for (int i = 0; i < transformMethods.size(); i++) {
                MemberName tm = transformMethods.get(i);
                tns.add(tm.getName());
                final MethodType tt = tm.getMethodType();
                tts.add(tt);
                tms.add(tm.getModifiers());
            }
            TRANSFORM_NAMES = List.of(tns.toArray(new String[0]));
            TRANSFORM_TYPES = List.of(tts.toArray(new MethodType[0]));
            TRANSFORM_MODS = List.of(tms.toArray(new Integer[0]));
        }
        private static final int ACC_PPP = ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED;

        /*non-public*/
        byte[] generateConcreteSpeciesCodeFile(String className0, ClassSpecializer<T,K,S>.SpeciesData speciesData) {
            final String className = classBCName(className0);
            final String superClassName = classBCName(speciesData.deriveSuperClass());

            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
            final int NOT_ACC_PUBLIC = 0;  // not ACC_PUBLIC
            cw.visit(CLASSFILE_VERSION, NOT_ACC_PUBLIC + ACC_FINAL + ACC_SUPER, className, null, superClassName, null);

            final String sourceFile = className.substring(className.lastIndexOf('.')+1);
            cw.visitSource(sourceFile, null);

            // emit static types and BMH_SPECIES fields
            FieldVisitor fw = cw.visitField(NOT_ACC_PUBLIC + ACC_STATIC, sdFieldName, SPECIES_DATA_SIG, null, null);
            fw.visitAnnotation(STABLE_SIG, true);
            fw.visitEnd();

            // handy holder for dealing with groups of typed values (ctor arguments and fields)
            class Var {
                final int index;
                final String name;
                final Class<?> type;
                final String desc;
                final BasicType basicType;
                final int slotIndex;
                Var(int index, int slotIndex) {
                    this.index = index;
                    this.slotIndex = slotIndex;
                    name = null; type = null; desc = nu