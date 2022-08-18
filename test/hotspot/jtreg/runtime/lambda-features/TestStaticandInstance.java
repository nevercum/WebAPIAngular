/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * @test
 * @bug 8087342
 * @summary Test linkresolver search static, instance and overpass duplicates
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote -XX:-BytecodeVerificationLocal TestStaticandInstance
 */


import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

public class TestStaticandInstance {
  static final String stringC = "C";
  static final String stringD = "D";
  static final String stringI = "I";

  public static void main(String args[]) throws Throwable {
    ClassLoader cl = new ClassLoader() {
      public Class<?> loadClass(String name) throws ClassNotFoundException {
        Class retClass;
        if ((retClass = findLoadedClass(name)) != null) {
           return retClass;
        }
        if (stringC.equals(name)) {
            byte[] classFile=dumpC();
            return defineClass(stringC, classFile, 0, classFile.length);
        }
        if (stringD.equals(name)) {
            byte[] classFile=dumpD();
            return defineClass(stringD, classFile, 0, classFile.length);
        }
        if (stringI.equals(name)) {
            byte[] classFile=dumpI();
            return defineClass(stringI, classFile, 0, classFile.length);
        }
        return super.loadClass(name);
      }
    };

    Class classC = cl.loadClass(stringC);
    Class classI = cl.loadClass(stringI);

    try {
      int staticret = (Integer)cl.loadClass(stringD).getDeclaredMethod("CallStatic").invoke(null);
      if (staticret != 1) {
        throw new RuntimeException("invokestatic failed to call correct method");
      }
      System.out.println("staticret: " + staticret); // should be 1

      int invokeinterfaceret = (Integer)cl.loadClass(stringD).getDeclaredMethod("CallInterface").invoke(null);
      if (invokeinterfaceret != 0) {
        throw new RuntimeException(String.format("Expected java.lang.AbstractMethodError, got %d", invokeinterfaceret));
      }
      System.out.println("invokeinterfaceret: AbstractMethodError");

      int invokevirtualret = (Integer)cl.loadClass(stringD).getDeclaredMethod("CallVirtual").invoke(null);
      if (invokevirtualret != 0) {
        throw new RuntimeException(String.format("Expected java.lang.IncompatibleClassChangeError, got %d", invokevirtualret));
      }
      System.out.println("invokevirtualret: IncompatibleClassChangeError");
    } catch (java.lang.Throwable e) {
      throw new RuntimeException("Unexpected exception: " + e.getMessage());
    }
  }

/*
interface I {
  public int m(); // abstract
  default int q() { return 3; } // trigger defmeth processing: C gets AME overpass
}

// C gets static, private and AME overpass m()I with the verifier turned off
class C implements I {
  static int m() { return 1;}  // javac with "n()" and patch to "m()"
  private int m() { return 2;} // javac with public and patch to private
}

public class D {
  public static int CallStatic() {
    int staticret = C.m();    // javac with "C.n" and patch to "C.m"
    return staticret;
  }
  public static int CallInterface() throws AbstractMethodError{
    try {
      I myI = new C();
      return myI.m();
    } catch (java.lang.AbstractMethodError e) {
      return 0; // for success
    }
  }
  public static int CallVirtual() {
    try {
      C myC = new C();
      return myC.m();
    } catch (java.lang.IncompatibleClassChangeError e) {
      return 0; // for success
    }
  }
}
*/

  public static byte[] dumpC() {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(52, ACC_SUPER, "C", null, "java/lang/Object", new String[] { "I" });

    {
      mv = cw.visitMethod(0, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVar