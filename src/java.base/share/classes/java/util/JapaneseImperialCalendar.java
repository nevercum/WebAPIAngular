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
        20*ONE_MINUTE   // DST_OFFSET (historical least maximum)
    };
    static final int MAX_VALUES[] = {
        0,              // ERA
        292278994,      // YEAR
        DECEMBER,       // MONTH
        53,             // WEEK_OF_YEAR
        6,              // WEEK_OF_MONTH
        31,             // DAY_OF_MONTH
        366,            // DAY_OF_YEAR
        SATURDAY,       // DAY_OF_WEEK
        6,              // DAY_OF_WEEK_IN
        PM,             // AM_PM
        11,             // HOUR
        23,             // HOUR_OF_DAY
        59,             // MINUTE
        59,             // SECOND
        999,            // MILLISECOND
        14*ONE_HOUR,    // ZONE_OFFSET
        2*ONE_HOUR      // DST_OFFSET (double summer time)
    };

    // Proclaim serialization compatibility with JDK 1.6
    @SuppressWarnings("FieldNameHidesFieldInSuperclass")
    @java.io.Serial
    private static final long serialVersionUID = -3364572813905467929L;

    static {
        Era[] es = jcal.getEras();
        int length = es.length + 1;
        eras = new Era[length];
        sinceFixedDates = new long[length];

        // eras[BEFORE_MEIJI] and sinceFixedDate[BEFORE_MEIJI] are the
        // same as Gregorian.
        int index = BEFORE_MEIJI;
        int current = index;
        sinceFixedDates[index] = gcal.getFixedDate(BEFORE_MEIJI_ERA.getSinceDate());
        eras[index++] = BEFORE_MEIJI_ERA;
        for (Era e : es) {
            if(e.getSince(TimeZone.NO_TIMEZONE) < System.currentTimeMillis()) {
                current = index;
            }
            CalendarDate d = e.getSinceDate();
            sinceFixedDates[index] = gcal.getFixedDate(d);
            eras[index++] = e;
        }
        currentEra = current;

        LEAST_MAX_VALUES[ERA] = MAX_VALUES[ERA] = eras.length - 1;

        // Calculate the least maximum year and least day of Year
        // values. The following code assumes that there's at most one
        // era transition in a Gregorian year.
        int year = Integer.MAX_VALUE;
        int dayOfYear = Integer.MAX_VALUE;
        CalendarDate date = gcal.newCalendarDate(TimeZone.NO_TIMEZONE);
        for (int i = 1; i < eras.length; i++) {
            long fd = sinceFixedDates[i];
            CalendarDate transitionDate = eras[i].getSinceDate();
            date.setDate(transitionDate.getYear(), BaseCalendar.JANUARY, 1);
            long fdd = gcal.getFixedDate(date);
            if (fd != fdd) {
                dayOfYear = Math.min((int)(fd - fdd) + 1, dayOfYear);
            }
            date.setDate(transitionDate.getYear(), BaseCalendar.DECEMBER, 31);
            fdd = gcal.getFixedDate(date);
            if (fd != fdd) {
                dayOfYear = Math.min((int)(fdd - fd) + 1, dayOfYear);
            }
            LocalGregorianCalendar.Date lgd = getCalendarDate(fd - 1);
            int y = lgd.getYear();
            // Unless the first year starts from January 1, the actual
            // max value could be one year short. For example, if it's
            // Showa 63 January 8, 63 is the actual max value since
            // Showa 64 January 8 doesn't exist.
            if (!(lgd.getMonth() == BaseCalendar.JANUARY && lgd.getDayOfMonth() == 1)) {
                y--;
            }
            year = Math.min(y, year);
        }
        LEAST_MAX_VALUES[YEAR] = year; // Max year could be smaller than this value.
        LEAST_MAX_VALUES[DAY_OF_YEAR] = dayOfYear;
    }

    /**
     * jdate always has a sun.util.calendar.LocalGregorianCalendar.Date instance to
     * avoid overhead of creating it for each calculation.
     */
    private transient LocalGregorianCalendar.Date jdate;

    /**
     * Temporary int[2] to get time zone offsets. zoneOffsets[0] gets
     * the GMT offset value and zoneOffsets[1] gets the daylight saving
     * value.
     */
    private transient int[] zoneOffsets;

    /**
     * Temporary storage for saving original fields[] values in
     * non-lenient mode.
     */
    private transient int[] originalFields;

    /**
     * Constructs a {@code JapaneseImperialCalendar} based on the current time
     * in the given time zone with the given locale.
     *
     * @param zone the given time zone.
     * @param aLocale the given locale.
     */
    JapaneseImperialCalendar(TimeZone zone, Locale aLocale) {
        super(zone, aLocale);
        jdate = jcal.newCalendarDate(zone);
        setTimeInMillis(System.currentTimeMillis());
    }

    /**
     * Constructs an "empty" {@code JapaneseImperialCalendar}.
     *
     * @param zone    the given time zone
     * @param aLocale the given locale
     * @param flag    the flag requesting an empty instance
     */
    JapaneseImperialCalendar(TimeZone zone, Locale aLocale, boolean flag) {
        super(zone, aLocale);
        jdate = jcal.newCalendarDate(zone);
    }

    /**
     * Returns {@code "japanese"} as the calendar type of this {@code
     * JapaneseImperialCalendar}.
     *
     * @return {@code "japanese"}
     */
    @Override
    public String getCalendarType() {
        return "japanese";
    }

    /**
     * Compares this {@code JapaneseImperialCalendar} to the specified
     * {@code Object}. The result is {@code true} if and
     * only if the argument is a {@code JapaneseImperialCalendar} object
     * that represents the same time value (millisecond offset from
     * the <a href="Calendar.html#Epoch">Epoch</a>) under the same
     * {@code Calendar} parameters.
     *
     * @param obj the object to compare with.
     * @return {@code true} if this object is equal to {@code obj};
     * {@code false} otherwise.
     * @see Calendar#compareTo(Calendar)
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof JapaneseImperialCalendar &&
            super.equals(obj);
    }

    /**
     * Generates the hash code for this
     * {@code JapaneseImperialCalendar} object.
     */
    @Override
    public int hashCode() {
        return super.hashCode() ^ jdate.hashCode();
    }

    /**
     * Adds the specified (signed) amount of time to the given calendar field,
     * based on the calendar's rules.
     *
     * <p><em>Add rule 1</em>. The value of {@code field}
     * after the call minus the value of {@code field} before the
     * call is {@code amount}, modulo any overflow that has occurred in
     * {@code field}. Overflow occurs when a field value exceeds its
     * range and, as a result, the next larger field is incremented or
     * decremented and the field value is adjusted back into its range.</p>
     *
     * <p><em>Add rule 2</em>. If a smaller field is expected to be
     * invariant, but it is impossible for it to be equal to its
     * prior value because of changes in its minimum or maximum after
     * {@code field} is changed, then its value is adjusted to be as close
     * as possible to its expected value. A smaller field represents a
     * smaller unit of time. {@code HOUR} is a smaller field than
     * {@code DAY_OF_MONTH}. No adjustment is made to smaller fields
     * that are not expected to be invariant. The calendar system
     * determines what fields are expected to be invariant.</p>
     *
     * @param field the calendar field.
     * @param amount the amount of date or time to be added to the field.
     * @throws    IllegalArgumentException if {@code field} is
     * {@code ZONE_OFFSET}, {@code DST_OFFSET}, or unknown,
     * or if any calendar fields have out-of-range values in
     * non-lenient mode.
     */
    @Override
    public void add(int field, int amount) {
        // If amount == 0, do nothing even the given field is out of
        // range. This is tested by JCK.
        if (amount == 0) {
            return;   // Do nothing!
        }

        if (field < 0 || field >= ZONE_OFFSET) {
            throw new IllegalArgumentException();
        }

        // Sync the time and calendar fields.
        complete();

        if (field == YEAR) {
            LocalGregorianCalendar.Date d = (LocalGregorianCalendar.Date) jdate.clone();
            d.addYear(amount);
            pinDayOfMonth(d);
            set(ERA, getEraIndex(d));
            set(YEAR, d.getYear());
            set(MONTH, d.getMonth() - 1);
            set(DAY_OF_MONTH, d.getDayOfMonth());
        } else if (field == MONTH) {
            LocalGregorianCalendar.Date d = (LocalGregorianCalendar.Date) jdate.clone();
            d.addMonth(amount);
            pinDayOfMonth(d);
            set(ERA, getEraIndex(d));
            set(YEAR, d.getYear());
            set(MONTH, d.getMonth() - 1);
            set(DAY_OF_MONTH, d.getDayOfMonth());
        } else if (field == ERA) {
            int era = internalGet(ERA) + amount;
            if (era < 0) {
                era = 0;
            } else if (era > eras.length - 1) {
                era = eras.length - 1;
            }
            set(ERA, era);
        } else {
            long delta = amount;
            long timeOfDay = 0;
            switch (field) {
            // Handle the time fields here. Convert the given
            // amount to milliseconds and call setTimeInMillis.
            case HOUR:
            case HOUR_OF_DAY:
                delta *= 60 * 60 * 1000;        // hours to milliseconds
                break;

            case MINUTE:
                delta *= 60 * 1000;             // minutes to milliseconds
                break;

            case SECOND:
                delta *= 1000;                  // seconds to milliseconds
                break;

            case MILLISECOND:
                break;

            // Handle week, day and AM_PM fields which involves
            // time zone offset change adjustment. Convert the
            // given amount to the number of days.
            case WEEK_OF_YEAR:
            case WEEK_OF_MONTH:
            case DAY_OF_WEEK_IN_MONTH:
                delta *= 7;
                break;

            case DAY_OF_MONTH: // synonym of DATE
            case DAY_OF_YEAR:
            case DAY_OF_WEEK:
                break;

            case AM_PM:
                // Convert the amount to the number of days (delta)
                // and +12 or -12 hours (timeOfDay).
                delta = amount / 2;
                timeOfDay = 12 * (amount % 2);
                break;
            }

            // The time fields don't require time zone offset change
            // adjustment.
            if (field >= HOUR) {
                setTimeInMillis(time + delta);
                return;
            }

            // The rest of the fields (week, day or AM_PM fields)
            // require time zone offset (both GMT and DST) change
            // adjustment.

            // Translate the current time to the fixed date and time
            // of the day.
            long fd = cachedFixedDate;
            timeOfDay += internalGet(HOUR_OF_DAY);
            timeOfDay *= 60;
            timeOfDay += internalGet(MINUTE);
            timeOfDay *= 60;
            timeOfDay += internalGet(SECOND);
            timeOfDay *= 1000;
            timeOfDay += internalGet(MILLISECOND);
            if (timeOfDay >= ONE_DAY) {
                fd++;
                timeOfDay -= ONE_DAY;
            } else if (timeOfDay < 0) {
                fd--;
                timeOfDay += ONE_DAY;
            }

            fd += delta; // fd is the expected fixed date after the calculation
            int zoneOffset = internalGet(ZONE_OFFSET) + internalGet(DST_OFFSET);
            setTimeInMillis((fd - EPOCH_OFFSET) * ONE_DAY + timeOfDay - zoneOffset);
            zoneOffset -= internalGet(ZONE_OFFSET) + internalGet(DST_OFFSET);
            // If the time zone offset has changed, then adjust the difference.
            if (zoneOffset != 0) {
                setTimeInMillis(time + zoneOffset);
                long fd2 = cachedFixedDate;
                // If the adjustment has changed the date, then take
                // the previous one.
                if (fd2 != fd) {
                    setTimeInMillis(time - zoneOffset);
                }
            }
        }
    }

    @Override
    public void roll(int field, boolean up) {
        roll(field, up ? +1 : -1);
    }

    /**
     * Adds a signed amount to the specified calendar field without changing larger fields.
     * A negative roll amount means to subtract from field without changing
     * larger fields. If the specified amount is 0, this method performs nothing.
     *
     * <p>This method calls {@link #complete()} before adding the
     * amount so that all the calendar fields are normalized. If there
     * is any calendar field having an out-of-range value in non-lenient mode, then an
     * {@code IllegalArgumentException} is thrown.
     *
     * @param field the calendar field.
     * @param amount the signed amount to add to {@code field}.
     * @throws    IllegalArgumentException if {@code field} is
     * {@code ZONE_OFFSET}, {@code DST_OFFSET}, or unknown,
     * or if any calendar fields have out-of-range values in
     * non-lenient mode.
     * @see #roll(int,boolean)
     * @see #add(int,int)
     * @see #set(int,int)
     */
    @Override
    public void roll(int field, int amount) {
        // If amount == 0, do nothing even the given field is out of
        // range. This is tested by JCK.
        if (amount == 0) {
            return;
        }

        if (field < 0 || field >= ZONE_OFFSET) {
            throw new IllegalArgumentException();
        }

        // Sync the time and calendar fields.
        complete();

        int min = getMinimum(field);
        int max = getMaximum(field);

        switch (field) {
        case ERA:
        case AM_PM:
        case MINUTE:
        case SECOND:
        case MILLISECOND:
            // These fields are handled simply, since they have fixed
            // minima and maxima. Other fields are complicated, since
            // the range within they must roll varies depending on the
            // date, a time zone and the era transitions.
            break;

        case HOUR:
        case HOUR_OF_DAY:
            {
                int unit = max + 1; // 12 or 24 hours
                int h = internalGet(field);
                int nh = (h + amount) % unit;
                if (nh < 0) {
                    nh += unit;
                }
                time += ONE_HOUR * (nh - h);

                // The day might have changed, which could happen if
                // the daylight saving time transition brings it to
                // the next day, although it's very unlikely. But we
                // have to make sure not to change the larger fields.
                CalendarDate d = jcal.getCalendarDate(time, getZone());
                if (internalGet(DAY_OF_MONTH) != d.getDayOfMonth()) {
                    d.setEra(jdate.getEra());
                    d.setDate(internalGet(YEAR),
                              internalGet(MONTH) + 1,
                              internalGet(DAY_OF_MONTH));
                    if (field == HOUR) {
                        assert (internalGet(AM_PM) == PM);
                        d.addHours(+12); // restore PM
                    }
                    time = jcal.getTime(d);
                }
                int hourOfDay = d.getHours();
                internalSet(field, hourOfDay % unit);
                if (field == HOUR) {
                    internalSet(HOUR_OF_DAY, hourOfDay);
                } else {
                    internalSet(AM_PM, hourOfDay / 12);
                    internalSet(HOUR, hourOfDay % 12);
                }

                // Time zone offset and/or daylight saving might have changed.
                int zoneOffset = d.getZoneOffset();
                int saving = d.getDaylightSaving();
                internalSet(ZONE_OFFSET, zoneOffset - saving);
                internalSet(DST_OFFSET, saving);
                return;
            }

        case YEAR:
            min = getActualMinimum(field);
            max = getActualMaximum(field);
            break;

        case MONTH:
            // Rolling the month involves both pinning the final value to [0, 11]
            // and adjusting the DAY_OF_MONTH if necessary.  We only adjust the
            // DAY_OF_MONTH if, after updating the MONTH field, it is illegal.
            // E.g., <jan31>.roll(MONTH, 1) -> <feb28> or <feb29>.
            {
                if (!isTransitionYear(jdate.getNormalizedYear())) {
                    int year = jdate.getYear();
                    if (year == getMaximum(YEAR)) {
                        CalendarDate jd = jcal.getCalendarDate(time, getZone());
                        CalendarDate d = jcal.getCalendarDate(Long.MAX_VALUE, getZone());
                        max = d.getMonth() - 1;
                        int n = getRolledValue(internalGet(field), amount, min, max);
                        if (n == max) {
                            // To avoid overflow, use an equivalent year.
                            jd.addYear(-400);
                            jd.setMonth(n + 1);
                            if (jd.getDayOfMonth() > d.getDayOfMonth()) {
                                jd.setDayOfMonth(d.getDayOfMonth());
                                jcal.normalize(jd);
                            }
                            if (jd.getDayOfMonth() == d.getDayOfMonth()
                                && jd.getTimeOfDay() > d.getTimeOfDay()) {
                                jd.setMonth(n + 1);
                                jd.setDayOfMonth(d.getDayOfMonth() - 1);
                                jcal.normalize(jd);
                                // Month may have changed by the normalization.
                                n = jd.getMonth() - 1;
                            }
                            set(DAY_OF_MONTH, jd.getDayOfMonth());
                        }
                        set(MONTH, n);
                    } else if (year == getMinimum(YEAR)) {
                        CalendarDate jd = jcal.getCalendarDate(time, getZone());
                        CalendarDate d = jcal.getCalendarDate(Long.MIN_VALUE, getZone());
                        min = d.getMonth() - 1;
                        int n = getRolledValue(internalGet(field), amount, min, max);
                        if (n == min) {
                            // To avoid underflow, use an equivalent year.
                            jd.addYear(+400);
                            jd.setMonth(n + 1);
                            if (jd.getDayOfMonth() < d.getDayOfMonth()) {
                                jd.setDayOfMonth(d.getDayOfMonth());
                                jcal.normalize(jd);
                            }
                            if (jd.getDayOfMonth() == d.getDayOfMonth()
                                && jd.getTimeOfDay() < d.getTimeOfDay()) {
                                jd.setMonth(n + 1);
                                jd.setDayOfMonth(d.getDayOfMonth() + 1);
                                jcal.normalize(jd);
                                // Month may have changed by the normalization.
                                n = jd.getMonth() - 1;
                            }
                            set(DAY_OF_MONTH, jd.getDayOfMonth());
                        }
                        set(MONTH, n);
                    } else {
                        int mon = (internalGet(MONTH) + amount) % 12;
                        if (mon < 0) {
                            mon += 12;
                        }
                        set(MONTH, mon);

                        // Keep the day of month in the range.  We
                        // don't want to spill over into the next
                        // month; e.g., we don't want jan31 + 1 mo ->
                        // feb31 -> mar3.
                        int monthLen = monthLength(mon);
                        if (internalGet(DAY_OF_MONTH) > monthLen) {
                            set(DAY_OF_MONTH, monthLen);
                        }
                    }
                } else {
                    int eraIndex = getEraIndex(jdate);
                    CalendarDate transition = null;
                    if (jdate.getYear() == 1) {
                        transition = eras[eraIndex].getSinceDate();
                        min = transition.getMonth() - 1;
                    } else {
                        if (eraIndex < eras.length - 1) {
                            transition = eras[eraIndex + 1].getSinceDate();
                            if (transition.getYear() == jdate.getNormalizedYear()) {
                                max = transition.getMonth() - 1;
                                if (transition.getDayOfMonth() == 1) {
                                    max--;
                                }
                            }
                        }
                    }

                    if (min == max) {
                        // The year has only one month. No need to
                        // process further. (Showa Gan-nen (year 1)
                        // and the last year have only one month.)
                        return;
                    }
                    int n = getRolledValue(internalGet(field), amount, min, max);
                    set(MONTH, n);
                    if (n == min) {
                        if (!(transition.getMonth() == BaseCalendar.JANUARY
                              && transition.getDayOfMonth() == 1)) {
                            if (jdate.getDayOfMonth() < transition.getDayOfMonth()) {
                                set(DAY_OF_MONTH, transition.getDayOfMonth());
                            }
                        }
                    } else if (n == max && (transition.getMonth() - 1 == n)) {
                        int dom = transition.getDayOfMonth();
                        if (jdate.getDayOfMonth() >= dom) {
                            set(DAY_OF_MONTH, dom - 1);
                        }
                    }
                }
                return;
            }

        case WEEK_OF_YEAR:
            {
                int y = jdate.getNormalizedYear();
                max = getActualMaximum(WEEK_OF_YEAR);
                set(DAY_OF_WEEK, internalGet(DAY_OF_WEEK)); // update stamp[field]
                int woy = internalGet(WEEK_OF_YEAR);
                int value = woy + amount;
                if (!isTransitionYear(jdate.getNormalizedYear())) {
                    int year = jdate.getYear();
                    if (year == getMaximum(YEAR)) {
                        max = getActualMaximum(WEEK_OF_YEAR);
                    } else if (year == getMinimum(YEAR)) {
                        min = getActualMinimum(WEEK_OF_YEAR);
                        max = getActualMaximum(WEEK_OF_YEAR);
                        if (value > min && value < max) {
                            set(WEEK_OF_YEAR, value);
                            return;
                        }

                    }
                    // If the new value is in between min and max
                    // (exclusive), then we can use the value.
                    if (value > min && value < max) {
                        set(WEEK_OF_YEAR, value);
                        return;
                    }
                    long fd = cachedFixedDate;
                    // Make sure that the min week has the current DAY_OF_WEEK
                    long day1 = fd - (7 * (woy - min));
                    if (year != getMinimum(YEAR)) {
                        if (gcal.getYearFromFixedDate(day1) != y) {
                            min++;
                        }
                    } else {
                        CalendarDate d = jcal.getCalendarDate(Long.MIN_VALUE, getZone());
                        if (day1 < jcal.getFixedDate(d)) {
                            min++;
                        }
                    }

                    // Make sure the same thing for the max week
                    fd += 7 * (max - internalGet(WEEK_OF_YEAR));
                    if (gcal.getYearFromFixedDate(fd) != y) {
                        max--;
                    }
                    break;
                }

                // Handle transition here.
                long fd = cachedFixedDate;
                long day1 = fd - (7 * (woy - min));
                // Make sure that the min week has the current DAY_OF_WEEK
                LocalGregorianCalendar.Date d = getCalendarDate(day1);
                if (!(d.getEra() == jdate.getEra() && d.getYear() == jdate.getYear())) {
                    min++;
                }

                // Make sure the same thing for the max week
                fd += 7 * (max - woy);
                jcal.getCalendarDateFromFixedDate(d, fd);
                if (!(d.getEra() == jdate.getEra() && d.getYear() == jdate.getYear())) {
                    max--;
                }
                // value: the new WEEK_OF_YEAR which must be converted
                // to month and day of month.
                value = getRolledValue(woy, amount, min, max) - 1;
                d = getCalendarDate(day1 + value * 7);
                set(MONTH, d.getMonth() - 1);
                set(DAY_OF_MONTH, d.getDayOfMonth());
                return;
            }

        case WEEK_OF_MONTH:
            {
                boolean isTransitionYear = isTransitionYear(jdate.getNormalizedYear());
                // dow: relative day of week from the first day of week
                int dow = internalGet(DAY_OF_WEEK) - getFirstDayOfWeek();
                if (dow < 0) {
                    dow += 7;
                }

                long fd = cachedFixedDate;
                long month1;     // fixed date of the first day (usually 1) of the month
                int monthLength; // actual month length
                if (isTransitionYear) {
                    month1 = getFixedDateMonth1(jdate, fd);
                    monthLength = actualMonthLength();
                } else {
                    month1 = fd - internalGet(DAY_OF_MONTH) + 1;
                    monthLength = jcal.getMonthLength(jdate);
                }

                // the first day of week of the month.
                long monthDay1st = LocalGregorianCalendar.getDayOfWeekDateOnOrBefore(month1 + 6,
                                                                                     getFirstDayOfWeek());
                // if the week has enough days to form a week, the
                // week starts from the previous month.
                if ((int)(monthDay1st - month1) >= getMinimalDaysInFirstWeek()) {
                    monthDay1st -= 7;
                }
                max = getActualMaximum(field);

                // value: the new WEEK_OF_MONTH value
                int value = getRolledValue(internalGet(field), amount, 1, max) - 1;

                // nfd: fixed date of the rolled date
                long nfd = monthDay1st + value * 7 + dow;

                // Unlike WEEK_OF_YEAR, we need to change day of week if the
                // nfd is out of the month.
                if (nfd < month1) {
                    nfd = month1;
                } else if (nfd >= (month1 + monthLength)) {
                    nfd = month1 + monthLength - 1;
                }
                set(DAY_OF_MONTH, getCalendarDate(nfd).getDayOfMonth());
                return;
            }

        case DAY_OF_MONTH:
            {
                if (!isTransitionYear(jdate.getNormalizedYear())) {
                    max = jcal.getMonthLength(jdate);
                    break;
                }

                // TODO: Need to change the spec to be usable DAY_OF_MONTH rolling...

                // Transition handling. We can't change year and era
                // values here due to the Calendar roll spec!
                long month1 = getFixedDateMonth1(jdate, cachedFixedDate);

                // It may not be a regular month. Convert the date and range to
                // the relative values, perform the roll, and
                // convert the result back to the rolled date.
                int value = getRolledValue((int)(cachedFixedDate - month1), amount,
                                           0, actualMonthLength() - 1);
                LocalGregorianCalendar.Date d = getCalendarDate(month1 + value);
                assert getEraIndex(d) == internalGetEra()
                    && d.getYear() == internalGet(YEAR) && d.getMonth()-1 == internalGet(MONTH);
                set(DAY_OF_MONTH, d.getDayOfMonth());
                return;
            }

        case DAY_OF_YEAR:
            {
                max = getActualMaximum(field);
                if (!isTransitionYear(jdate.getNormalizedYear())) {
                    break;
                }

                // Handle transition. We can't change year and era values
                // here due to the Calendar roll spec.
                int value = getRolledValue(internalGet(DAY_OF_YEAR), amount, min, max);
                long jan0 = cachedFixedDate - internalGet(DAY_OF_YEAR);
                LocalGregorianCalendar.Date d = getCalendarDate(jan0 + value);
                assert getEraIndex(d) == internalGetEra() && d.getYear() == internalGet(YEAR);
                set(MONTH, d.getMonth() - 1);
                set(DAY_OF_MONTH, d.getDayOfMonth());
                return;
            }

        case DAY_OF_WEEK:
            {
                int normalizedYear = jdate.getNormalizedYear();
                if (!isTransitionYear(normalizedYear) && !isTransitionYear(normalizedYear - 1)) {
                    // If the week of year is in the same year, we can
                    // just change DAY_OF_WEEK.
                    int weekOfYear = internalGet(WEEK_OF_YEAR);
                    if (weekOfYear > 1 && weekOfYear < 52) {
                        set(WEEK_OF_YEAR, internalGet(WEEK_OF_YEAR));
                        max = SATURDAY;
                        break;
                    }
                }

                // We need to handle it in a different way around year
                // boundaries and in the transition year. Note that
                // changing era and year values violates the roll
                // rule: not changing larger calendar fields...
                amount %= 7;
                if (amount == 0) {
                    return;
                }
                long fd = cachedFixedDate;
                long dowFirst = LocalGregorianCalendar.getDayOfWeekDateOnOrBefore(fd, getFirstDayOfWeek());
                fd += amount;
                if (fd < dowFirst) {
                    fd += 7;
                } else if (fd >= dowFirst + 7) {
                    fd -= 7;
                }
                LocalGregorianCalendar.Date d = getCalendarDate(fd);
                set(ERA, getEraIndex(d));
                set(d.getYear(), d.getMonth() - 1, d.getDayOfMonth());
                return;
            }

        case DAY_OF_WEEK_IN_MONTH:
            {
                min = 1; // after having normalized, min should be 1.
                if (!isTransitionYear(jdate.getNormalizedYear())) {
                    int dom = internalGet(DAY_OF_MONTH);
                    int monthLength = jcal.getMonthLength(jdate);
                    int lastDays = monthLength % 7;
                    max = monthLength / 7;
                    int x = (dom - 1) % 7;
                    if (x < lastDays) {
                        max++;
                    }
                    set(DAY_OF_WEEK, internalGet(DAY_OF_WEEK));
                    break;
                }

                // Transition year handling.
                long fd = cachedFixedDate;
                long month1 = getFixedDateMonth1(jdate, fd);
                int monthLength = actualMonthLength();
                int lastDays = monthLength % 7;
                max = monthLength / 7;
                int x = (int)(fd - month1) % 7;
                if (x < lastDays) {
                    max++;
                }
                int value = getRolledValue(internalGet(field), amount, min, max) - 1;
                fd = month1 + value * 7 + x;
                LocalGregorianCalendar.Date d = getCalendarDate(fd);
                set(DAY_OF_MONTH, d.getDayOfMonth());
                return;
            }
        }

        set(field, getRolledValue(internalGet(field), amount, min, max));
    }

    @Override
    public String getDisplayName(int field, int style, Locale locale) {
        if (!checkDisplayNameParams(field, style, SHORT, NARROW_FORMAT, locale,
                                    ERA_MASK|YEAR_MASK|MONTH_MASK|DAY_OF_WEEK_MASK|AM_PM_MASK)) {
            return null;
        }

        int fieldValue = get(field);

        // "GanNen" is supported only in the LONG style.
        if (field == YEAR
            && (getBaseStyle(style) != LONG || fieldValue != 1 || get(ERA) == 0)) {
            return null;
        }

        String name = CalendarDataUtility.retrieveFieldValueName(getCalendarType(), field,
                                                                 fieldValue, style, locale);
        // If the ERA value is null or empty, then
        // try to get its name or abbreviation from the Era instance.
        if ((name == null || name.isEmpty()) &&
                field == ERA &&
                fieldValue < eras.length) {
            Era era = eras[fieldValue];
            name = (style == SHORT) ? era.getAbbreviation() : era.getName();
        }
        return name;
    }

    @Override
    public Map<String,Integer> getDisplayNames(int field, int style, Locale locale) {
        if (!checkDisplayNameParams(field, style, ALL_STYLES, NARROW_FORMAT, locale,
                                    ERA_MASK|YEAR_MASK|MONTH_MASK|DAY_OF_WEEK_MASK|AM_PM_MASK)) {
            return null;
        }
        Map<String, Integer> names;
        names = CalendarDataUtility.retrieveFieldValueNames(getCalendarType(), field, style, locale);
        // If strings[] has fewer than eras[], get more names from eras[].
        if (names != null) {
            if (field == ERA) {
                int size = names.size();
                if (style == ALL_STYLES) {
                    Set<Integer> values = new HashSet<>();
                    // count unique era values
                    for (String key : names.keySet()) {
                        values.add(names.get(key));
                    }
                    size = values.size();
                }
                if (size < eras.length) {
                    int baseStyle = getBaseStyle(style);
                    for (int i = 0; i < eras.length; i++) {
                        if (!names.values().contains(i)) {
                            Era era = eras[i];
                            if (baseStyle == ALL_STYLES || baseStyle == SHORT
                                    || baseStyle == NARROW_FORMAT) {
                                names.put(era.getAbbreviation(), i);
                            }
                            if (baseStyle == ALL_STYLES || baseStyle == LONG) {
                                names.put(era.getName(), i);
                            }
                        }
                    }
                }
            }
        }
        return names;
    }

    /**
     * Returns the minimum value for the given calendar field of this
     * {@code Calendar} instance. The minimum value is
     * defined as the smallest value returned by the
     * {@link Calendar#get(int) get} method for any possible time value,
     * taking into consideration the current values of the
     * {@link Calendar#getFirstDayOfWeek() getFirstDayOfWeek},
     * {@link Calendar#getMinimalDaysInFirstWeek() getMinimalDaysInFirstWeek},
     * and {@link Calendar#getTimeZone() getTimeZone} methods.
     *
     * @param field the calendar field.
     * @return the minimum value for the given calendar field.
     * @see #getMaximum(int)
     * @see #getGreatestMinimum(int)
     * @see #getLeastMaximum(int)
     * @see #getActualMinimum(int)
     * @see #getActualMaximum(int)
     */
    public int getMinimum(int field) {
        return MIN_VALUES[field];
    }

    /**
     * Returns the maximum value for the given calendar field of this
     * {@code GregorianCalendar} instance. The maximum value is
     * defined as the largest value returned by the
     * {@link Calendar#get(int) get} method for any possible time value,
     * taking into consideration the current values of the
     * {@link Calendar#getFirstDayOfWeek() getFirstDayOfWeek},
     * {@link Calendar#getMinimalDaysInFirstWeek() getMinimalDaysInFirstWeek},
     * and {@link Calendar#getTimeZone() getTimeZone} methods.
     *
     * @param field the calendar field.
     * @return the maximum value for the given calendar field.
     * @see #getMinimum(int)
     * @see #getGreatestMinimum(int)
     * @see #getLeastMaximum(int)
     * @see #getActualMinimum(int)
     * @see #getActualMaximum(int)
     */
    public int getMaximum(int field) {
        return switch (field) {
            case YEAR -> {
                // The value should depend on the time zone of this calendar.
                LocalGregorianCalendar.Date d = jcal.getCalendarDate(Long.MAX_VALUE, getZone());
                yield Math.max(LEAST_MAX_VALUES[YEAR], d.getYear());
            }
            default -> MAX_VALUES[field];
        };
    }

    /**
     * Returns the highest minimum value for the given calendar field
     * of this {@code GregorianCalendar} instance. The highest
     * minimum value is defined as the largest value returned by
     * {@link #getActualMinimum(int)} for any possible time value,
     * taking into consideration the current values of the
     * {@link Calendar#getFirstDayOfWeek() getFirstDayOfWeek},
     * {@link Calendar#getMinimalDaysInFirstWeek() getMinimalDaysInFirstWeek},
     * and {@link Calendar#getTimeZone() getTimeZone} methods.
     *
     * @param field the calendar field.
     * @return the highest minimum value for the given calendar field.
     * @see #getMinimum(int)
     * @see #getMaximum(int)
     * @see #getLeastMaximum(int)
     * @see #getActualMinimum(int)
     * @see #getActualMaximum(int)
 