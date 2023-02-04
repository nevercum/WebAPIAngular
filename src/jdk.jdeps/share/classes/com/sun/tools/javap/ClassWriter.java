
/*
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javap;

import java.net.URI;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Attributes;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ConstantValue_attribute;
import com.sun.tools.classfile.Descriptor;
import com.sun.tools.classfile.Descriptor.InvalidDescriptor;
import com.sun.tools.classfile.Exceptions_attribute;
import com.sun.tools.classfile.Field;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.Module_attribute;
import com.sun.tools.classfile.Signature;
import com.sun.tools.classfile.Signature_attribute;
import com.sun.tools.classfile.SourceFile_attribute;
import com.sun.tools.classfile.Type;
import com.sun.tools.classfile.Type.ArrayType;
import com.sun.tools.classfile.Type.ClassSigType;
import com.sun.tools.classfile.Type.ClassType;
import com.sun.tools.classfile.Type.MethodType;
import com.sun.tools.classfile.Type.SimpleType;
import com.sun.tools.classfile.Type.TypeParamType;
import com.sun.tools.classfile.Type.WildcardType;

import static com.sun.tools.classfile.AccessFlags.*;
import static com.sun.tools.classfile.ConstantPool.CONSTANT_Module;
import static com.sun.tools.classfile.ConstantPool.CONSTANT_Package;

/*
 *  The main javap class to write the contents of a class file as text.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ClassWriter extends BasicWriter {
    static ClassWriter instance(Context context) {
        ClassWriter instance = context.get(ClassWriter.class);
        if (instance == null)
            instance = new ClassWriter(context);
        return instance;
    }

    protected ClassWriter(Context context) {
        super(context);
        context.put(ClassWriter.class, this);
        options = Options.instance(context);
        attrWriter = AttributeWriter.instance(context);
        codeWriter = CodeWriter.instance(context);
        constantWriter = ConstantWriter.instance(context);
    }

    void setDigest(String name, byte[] digest) {
        this.digestName = name;
        this.digest = digest;
    }

    void setFile(URI uri) {
        this.uri = uri;
    }

    void setFileSize(int size) {
        this.size = size;
    }

    void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    protected ClassFile getClassFile() {
        return classFile;
    }

    protected void setClassFile(ClassFile cf) {
        classFile = cf;
        constant_pool = classFile.constant_pool;
    }

    protected Method getMethod() {
        return method;
    }

    protected void setMethod(Method m) {
        method = m;
    }

    public void write(ClassFile cf) {
        setClassFile(cf);

        if (options.sysInfo || options.verbose) {
            if (uri != null) {
                if (uri.getScheme().equals("file"))
                    println("Classfile " + uri.getPath());
                else
                    println("Classfile " + uri);
            }
            indent(+1);
            if (lastModified != -1) {
                Date lm = new Date(lastModified);
                DateFormat df = DateFormat.getDateInstance();
                if (size > 0) {
                    println("Last modified " + df.format(lm) + "; size " + size + " bytes");
                } else {
                    println("Last modified " + df.format(lm));
                }
            } else if (size > 0) {
                println("Size " + size + " bytes");
            }
            if (digestName != null && digest != null) {
                StringBuilder sb = new StringBuilder();
                for (byte b: digest)
                    sb.append(String.format("%02x", b));
                println(digestName + " checksum " + sb);
            }
        }

        Attribute sfa = cf.getAttribute(Attribute.SourceFile);
        if (sfa instanceof SourceFile_attribute) {
            println("Compiled from \"" + getSourceFile((SourceFile_attribute) sfa) + "\"");
        }

        if (options.sysInfo || options.verbose) {
            indent(-1);
        }

        AccessFlags flags = cf.access_flags;
        writeModifiers(flags.getClassModifiers());

        if (classFile.access_flags.is(AccessFlags.ACC_MODULE)) {
            Attribute attr = classFile.attributes.get(Attribute.Module);
            if (attr instanceof Module_attribute) {
                Module_attribute modAttr = (Module_attribute) attr;
                String name;
                try {
                    // FIXME: compatibility code
                    if (constant_pool.get(modAttr.module_name).getTag() == CONSTANT_Module) {
                        name = getJavaName(constant_pool.getModuleInfo(modAttr.module_name).getName());
                    } else {
                        name = getJavaName(constant_pool.getUTF8Value(modAttr.module_name));
                    }
                } catch (ConstantPoolException e) {
                    name = report(e);
                }
                if ((modAttr.module_flags & Module_attribute.ACC_OPEN) != 0) {
                    print("open ");
                }
                print("module ");
                print(name);
                if (modAttr.module_version_index != 0) {
                    print("@");
                    print(getUTF8Value(modAttr.module_version_index));
                }
            } else {
                // fallback for malformed class files
                print("class ");
                print(getJavaName(classFile));
            }
        } else {
            if (classFile.isClass())
                print("class ");
            else if (classFile.isInterface())
                print("interface ");

            print(getJavaName(classFile));
        }

        Signature_attribute sigAttr = getSignature(cf.attributes);
        if (sigAttr == null) {
            // use info from class file header
            if (classFile.isClass() && classFile.super_class != 0 ) {
                String sn = getJavaSuperclassName(cf);
                if (!sn.equals("java.lang.Object")) {
                    print(" extends ");
                    print(sn);
                }
            }
            for (int i = 0; i < classFile.interfaces.length; i++) {
                print(i == 0 ? (classFile.isClass() ? " implements " : " extends ") : ",");
                print(getJavaInterfaceName(classFile, i));
            }
        } else {
            try {
                Type t = sigAttr.getParsedSignature().getType(constant_pool);
                JavaTypePrinter p = new JavaTypePrinter(classFile.isInterface());
                // The signature parser cannot disambiguate between a
                // FieldType and a ClassSignatureType that only contains a superclass type.
                if (t instanceof Type.ClassSigType) {
                    print(p.print(t));
                } else if (options.verbose || !t.isObject()) {
                    print(" extends ");
                    print(p.print(t));
                }
            } catch (ConstantPoolException e) {
                print(report(e));
            } catch (IllegalStateException e) {
                report("Invalid value for Signature attribute: " + e.getMessage());
            }
        }

        if (options.verbose) {
            println();
            indent(+1);
            println("minor version: " + cf.minor_version);
            println("major version: " + cf.major_version);
            writeList(String.format("flags: (0x%04x) ", flags.flags), flags.getClassFlags(), "\n");
            print("this_class: #" + cf.this_class);
            if (cf.this_class != 0) {
                tab();
                print("// " + constantWriter.stringValue(cf.this_class));
            }
            println();
            print("super_class: #" + cf.super_class);
            if (cf.super_class != 0) {
                tab();
                print("// " + constantWriter.stringValue(cf.super_class));
            }
            println();
            print("interfaces: " + cf.interfaces.length);
            print(", fields: " + cf.fields.length);
            print(", methods: " + cf.methods.length);
            println(", attributes: " + cf.attributes.attrs.length);
            indent(-1);
            constantWriter.writeConstantPool();
        } else {
            print(" ");
        }

        println("{");
        indent(+1);
        if (flags.is(AccessFlags.ACC_MODULE) && !options.verbose) {
            writeDirectives();
        }
        writeFields();
        writeMethods();
        indent(-1);
        println("}");

        if (options.verbose) {
            attrWriter.write(cf, cf.attributes, constant_pool);
        }
    }
    // where
        class JavaTypePrinter implements Type.Visitor<StringBuilder,StringBuilder> {
            boolean isInterface;

            JavaTypePrinter(boolean isInterface) {
                this.isInterface = isInterface;
            }

            String print(Type t) {
                return t.accept(this, new StringBuilder()).toString();
            }

            String printTypeArgs(List<? extends TypeParamType> typeParamTypes) {
                StringBuilder builder = new StringBuilder();
                appendIfNotEmpty(builder, "<", typeParamTypes, "> ");
                return builder.toString();
            }

            @Override
            public StringBuilder visitSimpleType(SimpleType type, StringBuilder sb) {
                sb.append(getJavaName(type.name));
                return sb;
            }

            @Override
            public StringBuilder visitArrayType(ArrayType type, StringBuilder sb) {
                append(sb, type.elemType);
                sb.append("[]");
                return sb;
            }

            @Override
            public StringBuilder visitMethodType(MethodType type, StringBuilder sb) {
                appendIfNotEmpty(sb, "<", type.typeParamTypes, "> ");
                append(sb, type.returnType);
                append(sb, " (", type.paramTypes, ")");
                appendIfNotEmpty(sb, " throws ", type.throwsTypes, "");
                return sb;
            }

            @Override
            public StringBuilder visitClassSigType(ClassSigType type, StringBuilder sb) {
                appendIfNotEmpty(sb, "<", type.typeParamTypes, ">");
                if (isInterface) {
                    appendIfNotEmpty(sb, " extends ", type.superinterfaceTypes, "");
                } else {
                    if (type.superclassType != null
                            && (options.verbose || !type.superclassType.isObject())) {
                        sb.append(" extends ");
                        append(sb, type.superclassType);
                    }
                    appendIfNotEmpty(sb, " implements ", type.superinterfaceTypes, "");
                }
                return sb;
            }

            @Override
            public StringBuilder visitClassType(ClassType type, StringBuilder sb) {
                if (type.outerType != null) {
                    append(sb, type.outerType);
                    sb.append(".");
                }
                sb.append(getJavaName(type.name));
                appendIfNotEmpty(sb, "<", type.typeArgs, ">");
                return sb;
            }

            @Override
            public StringBuilder visitTypeParamType(TypeParamType type, StringBuilder sb) {
                sb.append(type.name);
                String sep = " extends ";
                if (type.classBound != null
                        && (options.verbose || !type.classBound.isObject())) {
                    sb.append(sep);
                    append(sb, type.classBound);
                    sep = " & ";
                }
                if (type.interfaceBounds != null) {
                    for (Type bound: type.interfaceBounds) {
                        sb.append(sep);
                        append(sb, bound);
                        sep = " & ";
                    }
                }
                return sb;
            }

            @Override
            public StringBuilder visitWildcardType(WildcardType type, StringBuilder sb) {
                switch (type.kind) {
                    case UNBOUNDED:
                        sb.append("?");
                        break;
                    case EXTENDS: