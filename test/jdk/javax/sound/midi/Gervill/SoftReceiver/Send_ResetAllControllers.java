/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @summary Test SoftReceiver send method
   @modules java.desktop/com.sun.media.sound
*/

import javax.sound.midi.*;
import javax.sound.sampled.*;

import com.sun.media.sound.*;

public class Send_ResetAllControllers {

    public static boolean[] dontResetControls = new boolean[128];
    static {
        for (int i = 0; i < dontResetControls.length; i++)
            dontResetControls[i] = false;

        dontResetControls[0] = true;   // Bank Select (MSB)
        dontResetControls[32] = true;  // Bank Select (LSB)
        dontResetControls[7] = true;   // Channel Volume (MSB)
        dontResetControls[8] = true;   // Balance (MSB)
        dontResetControls[10] = true;  // Pan (MSB)
        dontResetControls[11] = true;  // Expression (MSB)
        dontResetControls[91] = true;  // Effects 1 Depth (default: Reverb Send)
        dontResetControls[92] = true;  // Effects 2 Depth (default: Tremolo Depth)
        dontResetControls[93] = true;  // Effects 3 Depth (default: Chorus Send)
        dontResetControls[94] = true;  // Effects 4 Depth (default: Celeste [Detune] Depth)
        dontResetControls[95] = true;  // Effects 5 Depth (default: Phaser Depth)
        dontResetControls[70] = 