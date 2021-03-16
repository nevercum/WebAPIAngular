/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import sun.util.locale.provider.CalendarDataUtility;
import sun.util.calendar.BaseCalendar;
import sun.util.calendar.CalendarDate;
import sun.util.calendar.CalendarSystem;
import sun.util.calendar.CalendarUtils;
import sun.util.calendar.Era;
import sun.util.calendar.Gregorian;
import sun.util.calendar.LocalGregorianCalendar;
import sun.util.calendar.ZoneInfo;

/**
 * {@code JapaneseImperialCalendar} implements a Japanese
 * calendar system in which the imperial era-based year numbering is
 * supported from the Meiji era. The following are the eras supported
 * by this calendar system.
 * <pre>{@code
 * ERA value   Era name    Since (in Gregorian)
 * ------------------------------------------------------
 *     0       N/A         N/A
 *     1       Meiji       1868-01-01T00:00:00 local time
 *     2       Taisho      1912-07-30T00:00:00 local time
 *     3       Showa       1926-12-25T00:00:00 local time
 *     4       Heisei      1989-01-08T00:00:00 local time
 *     5       Reiwa       2019-05-01T00:00:00 local time
 * ------------------------------------------------------
 * }</pre>
 *
 * <p>{@code ERA} value 0 specifies the years before Meiji and
 * the Gregorian year values are used. Unlike
 * {@link GregorianCalendar}, the Julian to Gregorian transition is not
 * supported because it doesn't make any sense to the Japanese
 * calendar systems used before Meiji. To represent the years before
 * Gregorian year 1, 0 and negative values are used. The Japanese
 * Imperial rescripts and government decrees don't specify how to deal
 * with time differences for applying the era transitions. This
 * calendar implementation assumes local time for all transitions.
 *
 * <p>A new era can be specified using property
 * jdk.calendar.japanese.supplemental.era. The new era is added to the
 * predefined eras. The syntax of the property is as follows.
 * <pre>
 *   {@code name=<name>,abbr=<abbr>,since=<time['u']>}
 * </pre>
 * where
 * <dl>
 * <dt>{@code <name>:}<dd>the full name of the new era (non-ASCII characters allowed,
 * either in platform's native encoding or in Unicode escape notation, {@code \\uXXXX})
 * <dt>{@code <abbr>:}<dd>the abbreviation of the new era (non-ASCII characters allowed,
 * either in platform's native encoding or in Unicode escape notation, {@code \\uXXXX})
 * <dt>{@code <time['u']>:}<dd>the start time of the new era represented by
 * milliseconds from 1970-01-01T00:00:00 local time or UTC if {@code 'u'} is
 * appended to the milliseconds value. (ASCII digits only)
 * </dl>
 *
 * <p>If the given era is invalid, such as the since value before the
 * beginning of the last predefined era, the given era will be
 * ignored.
 *
 * <p>The following is an example of the property usage.
 * <pre>
 *   java -Djdk.calendar.japanese.supplemental.era="name=NewEra,abbr=N,since=253374307200000"
 * </pre>
 * The property specifies an era change to NewEra at 9999-02-11T00:00:00 local time.
 *
 * @author Masayoshi Okutsu
 * @since 1.6
 */
class JapaneseImperialCalendar extends Calendar {
    /*
     * Implementation Notes
     *
     * This implementation uses
     * sun.util.calendar.LocalGregorianCalendar to perform most of the
     * calendar calculations.
     */

    /**
     * The ERA constant designating the era before Meiji.
     */
    public static final int BEFORE_MEIJI = 0;

    /**
     * The ERA constant designating the Meiji era.
     */
    public static final int MEIJI = 1;

    /**
     * The ERA constant designating the Taisho era.
     */
    public static final int TAISHO = 2;

    /**
     * The ERA constant designating the Showa era.
     */
    public static final int SHOWA = 3;

    /**
     * The ERA constant designating the Heisei era.
     */
    public static final int HEISEI = 4;

    /**
     * The ERA constant designating the Reiwa era.
     */
    private static final int REIWA = 5;

    private static final int EPOCH_OFFSET   = 719163; // Fixed date of January 1, 1970 (Gregorian)

    // Useful millisecond constants.  Although ONE_DAY and ONE_WEEK can fit
    // into ints, they must be longs in order to prevent arithmetic overflow
    // when performing (bug 4173516).
    private static final int  ONE_SECOND = 1000;
    private static final int  ONE_MINUTE = 60*ONE_SECOND;
    private static final int  ONE_HOUR   = 60*ONE_MINUTE;
    private static final long ONE_DAY    = 24*ONE_HOUR;

    // Reference to the sun.util.calendar.LocalGregorianCalendar instance (singleton).
    private static final LocalGregorianCalendar jcal
        = (LocalGregorianCalendar) CalendarSystem.forName("japanese");

    // Gregorian calendar instance. This is required because era
    // transition dates are given in Gregorian dates.
    private static final Gregorian gcal = CalendarSystem.getGregorianCalendar();

    // The Era instance representing "before Meiji".
    private static final Era BEFORE_MEIJI_ERA = new Era("BeforeMeiji", "BM", Long.MIN_VALUE, false);

    // Imperial eras. The sun.util.calendar.LocalGregorianCalendar
    // doesn't have an Era representing before Meiji, which is
    // inconvenient for a Calendar. So, era[0] is a reference to
    // BEFORE_MEIJI_ERA.
    private static final Era[] eras;

    // Fixed date of the first date of each era.
    private static final long[] sinceFixedDates;

    // The current era
    private static final int currentEra;

    /*
     * <pre>
     *                                 Greatest       Least
     * Field name             Minimum   Minimum     Maximum     Maximum
     * ----------             -------   -------     -------     -------
     * ERA                          0         0           1           1
     * YEAR                -292275055         1           ?           ?
     * MONTH                        0         0          11          11
     * WEEK_OF_YEAR                 1         1          52*         53
     * WEEK_OF_MONTH                0         0           4*          6
     * DAY_OF_MONTH                 1         1          28*         31
     * DAY_OF_YEAR                  1         1         365*        366
     * DAY_OF_WEEK                  1         1           7           7
     * DAY_OF_WEEK_IN_MONTH        -1        -1           4*          6
     * AM_PM                        0         0           1           1
     * HOUR                         0         0          11          11
     * HOUR_OF_DAY                  0         0          23          23
     * MINUTE                       0         0          59          59
     * SECOND                       0         0          59          59
     * MILLISECOND                  0         0         999         999
     * ZONE_OFFSET             -13:00    -13:00       14:00       14:00
     * DST_OFFSET                0:00      0:00        0:20        2:00
     * </pre>
     * *: depends on eras
     */
    static final int MIN_VALUES[] = {
        0,              // ERA
        -292275055,     // YEAR
        JANUARY,        // MONTH
        1,              // WEEK_OF_YEAR
        0,              // WEEK_OF_MONTH
        1,              // DAY_OF_MONTH
        1,              // DAY_OF_YEAR
        SUNDAY,         // DAY_OF_WEEK
        1,              // DAY_OF_WEEK_IN_MONTH
        AM,             // AM_PM
        0,              // HOUR
        0,              // HOUR_OF_DAY
        0,              // MINUTE
        0,              // SECOND
        0,              // MILLISECOND
        -13*ONE_HOUR,   // ZONE_OFFSET (UNIX compatibility)
        0               // DST_OFFSET
    };
    static final int LEAST_MAX_VALUES[] = {
        0,              // ERA (initialized later)
        0,              // YEAR (initialized later)
        JANUARY,        // MONTH (Showa 64 ended in January.)
        0,              // WEEK_OF_YEAR (Showa 1 has only 6 days which could be 0 weeks.)
        4,              // WEEK_OF_MONTH
        28,             // DAY_OF_MONTH
        0,              // DAY_OF_YEAR (initialized later)
        SATURDAY,       // DAY_OF_WEEK
        4,              // DAY_OF_WEEK_IN
        PM,             // AM_PM
        11,             // HOUR
        23,             // HOUR_OF_DAY
        59,             // MINUTE
        59,             // SECOND
        999,            // MILLISECOND
        14*ONE_HOUR,    // ZONE_OFFSET
        20*ONE_M