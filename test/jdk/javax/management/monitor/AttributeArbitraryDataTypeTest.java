/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6222961
 * @summary Test that the counter/gauge/string monitors
 *          support attributes of arbitrary data types.
 * @author Luis-Miguel Alventosa
 * @modules java.desktop
 *          java.management
 * @run clean AttributeArbitraryDataTypeTest
 * @run build AttributeArbitraryDataTypeTest
 * @run main AttributeArbitraryDataTypeTest
 */

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.monitor.CounterMonitor;
import javax.management.monitor.GaugeMonitor;
import javax.management.monitor.MonitorNotification;
import javax.management.monitor.StringMonitor;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

public class AttributeArbitraryDataTypeTest implements NotificationListener {

    // Flag to notify that a message has been received
    private volatile boolean counterMessageReceived = false;
    private volatile boolean gaugeMessageReceived = false;
    private volatile boolean stringMessageReceived = false;

    // Match enum
    public enum Match { do_not_match_0,
                        do_not_match_1,
                        do_not_match_2,
                        do_match_now };

    // MatchBeanInfo class
    public static class MatchBeanInfo extends SimpleBeanInfo {
        public PropertyDescriptor[] getPropertyDescriptors() {
            try {
                return new PropertyDescriptor[] {
                    new PropertyDescriptor("name", Match.class, "name", null) };
            } catch (IntrospectionException e ) {
                e.printStackTrace();
                return null;
            }
        }
    }

    // ComplexAttribute class
    public class ComplexAttribute {

        public Integer getIntegerAttribute() {
            return i;
        }

        public void setIntegerAttribute(Integer i) {
            this.i = i;
        }

        public Double getDoubleAttribute() {
            return d;
        }

        public void setDoubleAttribute(Double d) {
            this.d = d;
        }

        public String getStringAttribute() {
            return s;
        }

        public void setStringAttribute(String s) {
            this.s = s;
        }

        public Integer[] getArrayAttribute() {
            return a;
        }

        public void setArrayAttribute(Integer[] a) {
            this.a = a;
        }

        public Match getEnumAttribute() {
            return e;
        }

        public void setEnumAttribute(Match e) {
            this.e = e;
        }

        private Integer i;
        private Double d;
        private String s;
        private Integer[] a;
        private Match e;
    }

    // MBean class
    public class ObservedObject implements ObservedObjectMBean {

        // Simple type buried in complex getter
        //
        public ComplexAttribute getComplexAttribute() {
            return ca;
        }

        public void setComplexAttribute(ComplexAttribute ca) {
            this.ca = ca;
        }

        private ComplexAttribute ca = null;

        // Simple type buried in CompositeData
        //
        public CompositeData getCompositeDataAttribute()
            throws OpenDataException {
            CompositeType ct = new CompositeType("CompositeDataAttribute",
                                                 "Composite Data Attribute",
                                                 itemNames,
                                                 itemDescriptions,
                                                 itemTypes);
            Object itemValues[] = { ia, da, sa };
            return new CompositeDataSupport(ct, itemNames, itemValues);
        }

        public Integer ia;
        public Double da;
        public String sa;

        private String itemNames[] = { "IntegerAttribute",
                                       "DoubleAttribute",
                                       "StringAttribute" };
        private String itemDescriptions[] = { "Integer Attribute",
                                              "Double Attribute",
                                              "String Attribute" };
        private OpenType itemTypes[] = { SimpleType.INTEGER,
                                         SimpleType.DOUBLE,
                                         SimpleType.STRING };
    }

    // MBean interface
    public interface ObservedObjectMBean {
        public ComplexAttribute getComplexAttribute();
        public void setComplexAttribute(ComplexAttribute ca);
        public CompositeData getCompositeDataAttribute()
            throws OpenDataException;
    }

    // Notification handler
    public void handleNotification(Notification notification,
                                   Object handback) {
        MonitorNotification n = (MonitorNotification) notification;
        echo("\tInside handleNotification...");
        String type = n.getType();
        try {
            if (type.equals(MonitorNotification.
                            THRESHOLD_VALUE_EXCEEDED)) {
                echo("\t\t" + n.getObservedAttribute() +
                     " has reached or exceeded the threshold");
                echo("\t\tDerived Gauge = " + n.getDerivedGauge());
                echo("\t\tTrigger = " + n.getTrigger());

                synchronized (this) {
                    counterMessageReceived = true;
                    notifyAll();
                }
            } else if (type.equals(MonitorNotification.
                                   THRESHOLD_HIGH_VALUE_EXCEEDED)) {
                echo("\t\t" + n.getObservedAttribute() +
                     " has reached or exceeded the high threshold");
                echo("\t\tDerived Gauge = " + n.getDerivedGauge());
                echo("\t\tTrigger = " + n.getTrigger());

                synchronized (this) {
                    gaugeMessageReceived = true;
                    notifyAll();
                }
            } else if (type.equals(MonitorNotification.
                                   STRING_TO_COMPARE_VALUE_MATCHED)) {
                echo("\t\t" + n.getObservedAttribute() +
                     " matches the string-to-compare value");
                echo("\t\tDerived Gauge = " + n.getDerivedGauge());
                echo("\t\tTrigger = " + n.getTrigger());

                synchronized (this) {
                    stringMessageReceived = true;
                    notifyAll();
                }
            } else {
                echo("\t\tSkipping notification of type: " + type);
            }
        } catch (Exception e) {
            echo("\tError in handleNotification!");
            e.printStackTrace(System.out);
        }
    }

    /**
     * Update the counter and check for notifications
     */
    public int counterMonitorNotification(int testCase)
        throws Exception {

        counterMessageReceived = false;
        CounterMonitor counterMonitor = null;
        try {
            MBeanServer server = MBeanServerFactory.newMBeanServer();

            String domain = server.getDefaultDomain();

            // Create a new CounterMonitor MBean and add it to the MBeanServer.
            //
            echo(">>> CREATE a new CounterMonitor MBean");
            ObjectName counterMonitorName = new ObjectName(
                            domain + ":type=" + CounterMonitor.class.getName());
            counterMonitor = new CounterMonitor();
            server.registerMBean(counterMonitor, counterMonitorName);

            echo(">>> ADD a listener to the CounterMonitor");
            counterMonitor.addNotificationListener(this, null, null);

            //
            // MANAGEMENT OF A STANDARD MBEAN
            //

            echo(">>> CREATE a new ObservedObject MBean");

            ObjectName obsObjName =
                ObjectName.getInstance(domain + ":type=ObservedObject");
            ObservedObject obsObj = new ObservedObject();
            ComplexAttribute ca = new ComplexAttribute();
            switch (testCase) {
                case 1:
                    obsObj.ia = 0;
                    break;
                case 2:
                    ca.setIntegerAttribute(0);
                    obsObj.setComplexAttribute(ca);
                    break;
                case 3:
                    ca.setArrayAttribute(new Integer[0]);
                    obsObj.setComplexAttribute(ca);
                    break;
            }
            server.registerMBean(obsObj, obsObjName);

            echo(">>> SET the attributes of the CounterMonitor:");

            counterMonitor.addObservedObject(obsObjName);
            echo("\tATTRIBUTE \"ObservedObject\"    = " + obsObjName);

            switch (testCase) {
                case 1:
                    counterMonitor.setObservedAttribute(
                         "CompositeDataAttribute.IntegerAttribute");
                    echo("\tATTRIBUTE \"ObservedAttribute\" = " +
                         "CompositeDataAttribute.IntegerAttribute");
                    break;
                case 2:
                    counterMonitor.setObservedAttribute(
                         "ComplexAttribute.integerAttribute");
                    echo("\tATTRIBUTE \"ObservedAttribute\" = " +
                         "ComplexAttribute.integerAttribute");
                    break;
                case 3:
                    counterMonitor.setObservedAttribute(
                         "ComplexAttribute.arrayAttribute.length");
                    echo("\tATTRIBUTE \"ObservedAttribute\" = " +
                         "ComplexAttribute.arrayAttribute.length");
                    break;
            }

            counterMonitor.setNotify(true);
            echo("\tATTRIBUTE \"NotifyFlag\"        = true");

            Integer threshold = 2;
            counterMonitor.setInitThreshold(threshold);
            echo("\tATTRIBUTE \"Threshold\"         = " + threshold);

            int granularityperiod = 500;
            counterMonitor.setGranularityPeriod(granularityperiod);
            echo("\tATTRIBUTE \"GranularityPeriod\" = " + granularityperiod);

            echo(">>> START the CounterMonitor");
            counterMonitor.start();

            // Wait for granularity period (multiplied by 2 for sure)
            //
            Thread.sleep(granularityperiod * 2);

            switch (testCase) {
                case 1:
                    obsObj.ia = 1;
                    break;
                case 2:
                    ca.setIntegerAttribute(1);
                    break;
                case 3:
                    ca.setArrayAttribute(new Integer[1]);
                    break;
            }

            // Wait for granularity period (multiplied by 2 for sure)
            //
            Thread.sleep(granularityperiod * 2);

            switch (testCase) {
                case 1:
                    obsObj.ia = 2;
                    break;
                case 2:
                    ca.setIntegerAttribute(2);
                    break;
                case 3:
                    ca.setArrayAttribute(new Integer[2]);
                    break;
            }

            // Wait for granularity period (multiplied by 2 for sure)
            //
            Thread.sleep(granularityperiod * 2);

            switch (testCase) {
                case 1:
                    obsObj.ia = 3;
                    break;
                case 2:
                    ca.setIntegerAttribute(3);
                    break;
                case 3:
                    ca.setArrayAttribute(new Integer[3]);
                    break;
            }

            // Check if notification was received
            //
            synchronized (this) {
                while (!counterMessageReceived) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        System.err.println("Got unexpected exception: " + e);
                        e.printStackTrace();
                        break;
                    }
                }
            }
            if (counterMessageReceived) {
                echo("\tOK: CounterMonitor notification received");
            } else {
                echo("\tKO: CounterMonitor notification missed or not emitted");
                return 1;
            }
        } finally {
            if (counterMonitor != null)
                counterMonitor.stop();
        }

        return 0;
    }

    /**
     * Update the gauge and check for notifications
     */
    public int gaugeMonitorNotification(int testCase)
        throws Exception {

        gaugeMessageReceived = false;
        GaugeMonitor gaugeMonitor = null;
        try {
            MBeanServer server = MBeanServerFactory.newMBeanServer();

            String domain = server.getDefaultDomain();

            // Create a new GaugeMonitor MBean and add it to the MBeanServer.
            //
            echo(">>> CREATE a new GaugeMonitor MBean");
            ObjectName gaugeMonitorName = new ObjectName(
                            domain + ":type=" + GaugeMonitor.class.getName());
            gaugeMonitor = new GaugeMonitor();
            server.registerMBean(gaugeMonitor, gaugeMonitorName);

            echo(">>> ADD a listener to the GaugeMonitor");
            gaugeMonitor.addNotificationListener(this, null, null);

            //
            // MANAGEMENT OF A STANDARD MBEAN
            //

            echo(">>> CREATE a new ObservedObject MBean");

            ObjectName obsObjName =
                ObjectName.getInstance(domain + ":type=ObservedObject");
            ObservedObject obsObj 