/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8007572 8008161 8157792 8212970 8224560
 * @summary Test whether the TimeZone generated from JSR310 tzdb is the same
 * as the one from the tz data from javazic
 * @modules java.base/sun.util.calendar:+open
 * @build BackEnd Checksum DayOfWeek Gen GenDoc Main Mappings Month
 *        Rule RuleDay RuleRec Simple TestZoneInfo310 Time Timezone
 *        TzIDOldMapping Zone ZoneInfoFile ZoneInfoOld ZoneRec Zoneinfo
 * @run main TestZoneInfo310
 */

import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.time.zone.*;
import java.time.ZoneId;

public class TestZoneInfo310 {

    public static void main(String[] args) throws Throwable {

        String TESTDIR = System.getProperty("test.dir", ".");
        Path tzdir = Paths.get(System.getProperty("test.root"),
      