
/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package jdk.internal.org.objectweb.asm;

/**
 * A {@link ClassVisitor} that generates a corresponding ClassFile structure, as defined in the Java
 * Virtual Machine Specification (JVMS). It can be used alone, to generate a Java class "from
 * scratch", or with one or more {@link ClassReader} and adapter {@link ClassVisitor} to generate a
 * modified class from one or more existing Java classes.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html">JVMS 4</a>
 * @author Eric Bruneton
 */
public class ClassWriter extends ClassVisitor {

    /**
      * A flag to automatically compute the maximum stack size and the maximum number of local
      * variables of methods. If this flag is set, then the arguments of the {@link
      * MethodVisitor#visitMaxs} method of the {@link MethodVisitor} returned by the {@link
      * #visitMethod} method will be ignored, and computed automatically from the signature and the
      * bytecode of each method.
      *
      * <p><b>Note:</b> for classes whose version is {@link Opcodes#V1_7} of more, this option requires
      * valid stack map frames. The maximum stack size is then computed from these frames, and from the
      * bytecode instructions in between. If stack map frames are not present or must be recomputed,
      * used {@link #COMPUTE_FRAMES} instead.
      *
      * @see #ClassWriter(int)
      */
    public static final int COMPUTE_MAXS = 1;

    /**
      * A flag to automatically compute the stack map frames of methods from scratch. If this flag is
      * set, then the calls to the {@link MethodVisitor#visitFrame} method are ignored, and the stack
      * map frames are recomputed from the methods bytecode. The arguments of the {@link
      * MethodVisitor#visitMaxs} method are also ignored and recomputed from the bytecode. In other
      * words, {@link #COMPUTE_FRAMES} implies {@link #COMPUTE_MAXS}.
      *
      * @see #ClassWriter(int)
      */
    public static final int COMPUTE_FRAMES = 2;

    /**
      * The flags passed to the constructor. Must be zero or more of {@link #COMPUTE_MAXS} and {@link
      * #COMPUTE_FRAMES}.
      */
    private final int flags;

    // Note: fields are ordered as in the ClassFile structure, and those related to attributes are
    // ordered as in Section 4.7 of the JVMS.

    /**
      * The minor_version and major_version fields of the JVMS ClassFile structure. minor_version is
      * stored in the 16 most significant bits, and major_version in the 16 least significant bits.
      */
    private int version;

    /** The symbol table for this class (contains the constant_pool and the BootstrapMethods). */
    private final SymbolTable symbolTable;

    /**
      * The access_flags field of the JVMS ClassFile structure. This field can contain ASM specific
      * access flags, such as {@link Opcodes#ACC_DEPRECATED} or {@link Opcodes#ACC_RECORD}, which are
      * removed when generating the ClassFile structure.
      */
    private int accessFlags;

    /** The this_class field of the JVMS ClassFile structure. */
    private int thisClass;

    /** The super_class field of the JVMS ClassFile structure. */
    private int superClass;

    /** The interface_count field of the JVMS ClassFile structure. */
    private int interfaceCount;

    /** The 'interfaces' array of the JVMS ClassFile structure. */
    private int[] interfaces;

    /**
      * The fields of this class, stored in a linked list of {@link FieldWriter} linked via their
      * {@link FieldWriter#fv} field. This field stores the first element of this list.
      */
    private FieldWriter firstField;

    /**
      * The fields of this class, stored in a linked list of {@link FieldWriter} linked via their
      * {@link FieldWriter#fv} field. This field stores the last element of this list.
      */
    private FieldWriter lastField;

    /**
      * The methods of this class, stored in a linked list of {@link MethodWriter} linked via their
      * {@link MethodWriter#mv} field. This field stores the first element of this list.
      */
    private MethodWriter firstMethod;

    /**
      * The methods of this class, stored in a linked list of {@link MethodWriter} linked via their
      * {@link MethodWriter#mv} field. This field stores the last element of this list.
      */
    private MethodWriter lastMethod;

    /** The number_of_classes field of the InnerClasses attribute, or 0. */
    private int numberOfInnerClasses;

    /** The 'classes' array of the InnerClasses attribute, or {@literal null}. */
    private ByteVector innerClasses;

    /** The class_index field of the EnclosingMethod attribute, or 0. */
    private int enclosingClassIndex;

    /** The method_index field of the EnclosingMethod attribute. */
    private int enclosingMethodIndex;

    /** The signature_index field of the Signature attribute, or 0. */
    private int signatureIndex;

    /** The source_file_index field of the SourceFile attribute, or 0. */
    private int sourceFileIndex;

    /** The debug_extension field of the SourceDebugExtension attribute, or {@literal null}. */
    private ByteVector debugExtension;

    /**
      * The last runtime visible annotation of this class. The previous ones can be accessed with the
      * {@link AnnotationWriter#previousAnnotation} field. May be {@literal null}.
      */
    private AnnotationWriter lastRuntimeVisibleAnnotation;

    /**
      * The last runtime invisible annotation of this class. The previous ones can be accessed with the
      * {@link AnnotationWriter#previousAnnotation} field. May be {@literal null}.
      */
    private AnnotationWriter lastRuntimeInvisibleAnnotation;

    /**
      * The last runtime visible type annotation of this class. The previous ones can be accessed with
      * the {@link AnnotationWriter#previousAnnotation} field. May be {@literal null}.
      */
    private AnnotationWriter lastRuntimeVisibleTypeAnnotation;

    /**
      * The last runtime invisible type annotation of this class. The previous ones can be accessed
      * with the {@link AnnotationWriter#previousAnnotation} field. May be {@literal null}.
      */
    private AnnotationWriter lastRuntimeInvisibleTypeAnnotation;

    /** The Module attribute of this class, or {@literal null}. */
    private ModuleWriter moduleWriter;

    /** The host_class_index field of the NestHost attribute, or 0. */
    private int nestHostClassIndex;

    /** The number_of_classes field of the NestMembers attribute, or 0. */
    private int numberOfNestMemberClasses;

    /** The 'classes' array of the NestMembers attribute, or {@literal null}. */
    private ByteVector nestMemberClasses;

    /** The number_of_classes field of the PermittedSubclasses attribute, or 0. */
    private int numberOfPermittedSubclasses;

    /** The 'classes' array of the PermittedSubclasses attribute, or {@literal null}. */
    private ByteVector permittedSubclasses;

    /**
      * The record components of this class, stored in a linked list of {@link RecordComponentWriter}
      * linked via their {@link RecordComponentWriter#delegate} field. This field stores the first
      * element of this list.
      */
    private RecordComponentWriter firstRecordComponent;

    /**
      * The record components of this class, stored in a linked list of {@link RecordComponentWriter}
      * linked via their {@link RecordComponentWriter#delegate} field. This field stores the last
      * element of this list.
      */
    private RecordComponentWriter lastRecordComponent;

    /**
      * The first non standard attribute of this class. The next ones can be accessed with the {@link
      * Attribute#nextAttribute} field. May be {@literal null}.
      *
      * <p><b>WARNING</b>: this list stores the attributes in the <i>reverse</i> order of their visit.
      * firstAttribute is actually the last attribute visited in {@link #visitAttribute}. The {@link
      * #toByteArray} method writes the attributes in the order defined by this list, i.e. in the
      * reverse order specified by the user.
      */
    private Attribute firstAttribute;

    /**
      * Indicates what must be automatically computed in {@link MethodWriter}. Must be one of {@link
      * MethodWriter#COMPUTE_NOTHING}, {@link MethodWriter#COMPUTE_MAX_STACK_AND_LOCAL}, {@link
      * MethodWriter#COMPUTE_INSERTED_FRAMES}, or {@link MethodWriter#COMPUTE_ALL_FRAMES}.
      */
    private int compute;

    // -----------------------------------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------------------------------

    /**
      * Constructs a new {@link ClassWriter} object.
      *
      * @param flags option flags that can be used to modify the default behavior of this class. Must
      *     be zero or more of {@link #COMPUTE_MAXS} and {@link #COMPUTE_FRAMES}.
      */
    public ClassWriter(final int flags) {
        this(null, flags);
    }

    /**
      * Constructs a new {@link ClassWriter} object and enables optimizations for "mostly add" bytecode
      * transformations. These optimizations are the following:
      *
      * <ul>
      *   <li>The constant pool and bootstrap methods from the original class are copied as is in the
      *       new class, which saves time. New constant pool entries and new bootstrap methods will be
      *       added at the end if necessary, but unused constant pool entries or bootstrap methods
      *       <i>won't be removed</i>.
      *   <li>Methods that are not transformed are copied as is in the new class, directly from the
      *       original class bytecode (i.e. without emitting visit events for all the method
      *       instructions), which saves a <i>lot</i> of time. Untransformed methods are detected by
      *       the fact that the {@link ClassReader} receives {@link MethodVisitor} objects that come
      *       from a {@link ClassWriter} (and not from any other {@link ClassVisitor} instance).
      * </ul>
      *
      * @param classReader the {@link ClassReader} used to read the original class. It will be used to
      *     copy the entire constant pool and bootstrap methods from the original class and also to
      *     copy other fragments of original bytecode where applicable.
      * @param flags option flags that can be used to modify the default behavior of this class. Must
      *     be zero or more of {@link #COMPUTE_MAXS} and {@link #COMPUTE_FRAMES}. <i>These option flags
      *     do not affect methods that are copied as is in the new class. This means that neither the
      *     maximum stack size nor the stack frames will be computed for these methods</i>.
      */
    public ClassWriter(final ClassReader classReader, final int flags) {
        super(/* latest api = */ Opcodes.ASM9);
        this.flags = flags;
        symbolTable = classReader == null ? new SymbolTable(this) : new SymbolTable(this, classReader);
        if ((flags & COMPUTE_FRAMES) != 0) {
            compute = MethodWriter.COMPUTE_ALL_FRAMES;
        } else if ((flags & COMPUTE_MAXS) != 0) {
            compute = MethodWriter.COMPUTE_MAX_STACK_AND_LOCAL;
        } else {
            compute = MethodWriter.COMPUTE_NOTHING;
        }
    }

    // -----------------------------------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------------------------------

    /**
      * Returns true if all the given flags were passed to the constructor.
      *
      * @param flags some option flags. Must be zero or more of {@link #COMPUTE_MAXS} and {@link
      *     #COMPUTE_FRAMES}.
      * @return true if all the given flags, or more, were passed to the constructor.
      */
    public boolean hasFlags(final int flags) {
        return (this.flags & flags) == flags;
    }

    // -----------------------------------------------------------------------------------------------
    // Implementation of the ClassVisitor abstract class
    // -----------------------------------------------------------------------------------------------

    @Override
    public final void visit(
            final int version,
            final int access,
            final String name,
            final String signature,
            final String superName,
            final String[] interfaces) {
        this.version = version;
        this.accessFlags = access;
        this.thisClass = symbolTable.setMajorVersionAndClassName(version & 0xFFFF, name);
        if (signature != null) {
            this.signatureIndex = symbolTable.addConstantUtf8(signature);
        }
        this.superClass = superName == null ? 0 : symbolTable.addConstantClass(superName).index;
        if (interfaces != null && interfaces.length > 0) {
            interfaceCount = interfaces.length;
            this.interfaces = new int[interfaceCount];
            for (int i = 0; i < interfaceCount; ++i) {
                this.interfaces[i] = symbolTable.addConstantClass(interfaces[i]).index;
            }
        }
        if (compute == MethodWriter.COMPUTE_MAX_STACK_AND_LOCAL && (version & 0xFFFF) >= Opcodes.V1_7) {
            compute = MethodWriter.COMPUTE_MAX_STACK_AND_LOCAL_FROM_FRAMES;
        }
    }

    @Override
    public final void visitSource(final String file, final String debug) {
        if (file != null) {
            sourceFileIndex = symbolTable.addConstantUtf8(file);
        }
        if (debug != null) {
            debugExtension = new ByteVector().encodeUtf8(debug, 0, Integer.MAX_VALUE);
        }
    }

    @Override
    public final ModuleVisitor visitModule(
            final String name, final int access, final String version) {
        return moduleWriter =
                new ModuleWriter(
                        symbolTable,
                        symbolTable.addConstantModule(name).index,
                        access,
                        version == null ? 0 : symbolTable.addConstantUtf8(version));
    }

    @Override
    public final void visitNestHost(final String nestHost) {
        nestHostClassIndex = symbolTable.addConstantClass(nestHost).index;
    }

    @Override
    public final void visitOuterClass(
            final String owner, final String name, final String descriptor) {
        enclosingClassIndex = symbolTable.addConstantClass(owner).index;
        if (name != null && descriptor != null) {
            enclosingMethodIndex = symbolTable.addConstantNameAndType(name, descriptor);
        }
    }

    @Override
    public final AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
        if (visible) {
            return lastRuntimeVisibleAnnotation =
                    AnnotationWriter.create(symbolTable, descriptor, lastRuntimeVisibleAnnotation);
        } else {
            return lastRuntimeInvisibleAnnotation =
                    AnnotationWriter.create(symbolTable, descriptor, lastRuntimeInvisibleAnnotation);
        }
    }

    @Override
    public final AnnotationVisitor visitTypeAnnotation(
            final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
        if (visible) {
            return lastRuntimeVisibleTypeAnnotation =
                    AnnotationWriter.create(
                            symbolTable, typeRef, typePath, descriptor, lastRuntimeVisibleTypeAnnotation);
        } else {
            return lastRuntimeInvisibleTypeAnnotation =
                    AnnotationWriter.create(
                            symbolTable, typeRef, typePath, descriptor, lastRuntimeInvisibleTypeAnnotation);
        }
    }

    @Override
    public final void visitAttribute(final Attribute attribute) {
        // Store the attributes in the <i>reverse</i> order of their visit by this method.
        attribute.nextAttribute = firstAttribute;
        firstAttribute = attribute;
    }

    @Override
    public final void visitNestMember(final String nestMember) {
        if (nestMemberClasses == null) {
            nestMemberClasses = new ByteVector();
        }
        ++numberOfNestMemberClasses;
        nestMemberClasses.putShort(symbolTable.addConstantClass(nestMember).index);
    }

    @Override
    public final void visitPermittedSubclass(final String permittedSubclass) {
        if (permittedSubclasses == null) {
            permittedSubclasses = new ByteVector();
        }
        ++numberOfPermittedSubclasses;
        permittedSubclasses.putShort(symbolTable.addConstantClass(permittedSubclass).index);
    }

    @Override
    public final void visitInnerClass(
            final String name, final String outerName, final String innerName, final int access) {
        if (innerClasses == null) {
            innerClasses = new ByteVector();
        }
        // Section 4.7.6 of the JVMS states "Every CONSTANT_Class_info entry in the constant_pool table
        // which represents a class or interface C that is not a package member must have exactly one
        // corresponding entry in the classes array". To avoid duplicates we keep track in the info
        // field of the Symbol of each CONSTANT_Class_info entry C whether an inner class entry has
        // already been added for C. If so, we store the index of this inner class entry (plus one) in
        // the info field. This trick allows duplicate detection in O(1) time.
        Symbol nameSymbol = symbolTable.addConstantClass(name);
        if (nameSymbol.info == 0) {
            ++numberOfInnerClasses;
            innerClasses.putShort(nameSymbol.index);
            innerClasses.putShort(outerName == null ? 0 : symbolTable.addConstantClass(outerName).index);
            innerClasses.putShort(innerName == null ? 0 : symbolTable.addConstantUtf8(innerName));
            innerClasses.putShort(access);
            nameSymbol.info = numberOfInnerClasses;
        }
        // Else, compare the inner classes entry nameSymbol.info - 1 with the arguments of this method
        // and throw an exception if there is a difference?
    }

    @Override
    public final RecordComponentVisitor visitRecordComponent(
            final String name, final String descriptor, final String signature) {
        RecordComponentWriter recordComponentWriter =
                new RecordComponentWriter(symbolTable, name, descriptor, signature);
        if (firstRecordComponent == null) {
            firstRecordComponent = recordComponentWriter;
        } else {
            lastRecordComponent.delegate = recordComponentWriter;
        }
        return lastRecordComponent = recordComponentWriter;
    }

    @Override
    public final FieldVisitor visitField(
            final int access,
            final String name,
            final String descriptor,
            final String signature,
            final Object value) {
        FieldWriter fieldWriter =
                new FieldWriter(symbolTable, access, name, descriptor, signature, value);
        if (firstField == null) {
            firstField = fieldWriter;
        } else {
            lastField.fv = fieldWriter;
        }
        return lastField = fieldWriter;
    }

    @Override
    public final MethodVisitor visitMethod(