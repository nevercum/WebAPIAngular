
/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.share.gc.lock.jni;

import nsk.share.gc.lock.CriticalSectionObjectLocker;
import nsk.share.TestFailure;

public class ShortArrayCriticalLocker extends CriticalSectionObjectLocker<short[]> {
        private native short criticalNative(long enterTime, long sleepTime);

        static {
                System.loadLibrary("ShortArrayCriticalLocker");
        }

        public ShortArrayCriticalLocker(short[] obj) {
                super(obj);
        }

        protected void criticalSection(long enterTime, long sleepTime) {
                short javaHash = hashValue(obj);
                short nativeHash = criticalNative(enterTime, sleepTime);
                if (nativeHash != 0 && nativeHash != javaHash)
                        throw new TestFailure("Native hash: " + nativeHash + " != Java hash: " + javaHash);
        }

        private short hashValue(short[] obj) {
                short hash = 0;
                for (int i = 0; i < obj.length; ++i)
                        hash ^= obj[i];
                return hash;
        }
}