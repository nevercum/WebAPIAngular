/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.management.jfr;

import java.util.concurrent.Callable;

import javax.management.openmbean.CompositeData;

import jdk.jfr.EventType;
import jdk.jfr.SettingDescriptor;
import jdk.management.jfr.internal.FlightRecorderMXBeanProvider;

/**
 * Management class that describes a setting, for example name, description and
 * default value.
 *
 * @see EventType#getSettingDescriptors()
 *
 * @since 9
 */
public final class SettingDescriptorInfo {

    // Purpose of this static initializer is to allow
    // FlightRecorderMXBeanProvider
    // to be in an internal package and not visible, but at the same time allow
    // it to instantiate FlightRecorderMXBeanImpl.
    //
    // The reason the mechanism is in this class is because it is light weight
    // and can easily be triggered from FlightRecorderMXBeanProvider.
    static {
        FlightRecorderMXBeanProvider.setFlightRecorderMXBeanFactory(new Callable<FlightRecorderMXBean>() {
            @Override
            public FlightRecorderMXBean call() throws Exception {
                return new FlightRecorderMXBeanImpl();
            }
        });
    }

    private final String name;
    private final String label;
    private final String description;
    private final String typeName;
    private final String contentType;
    private final String defaultValue;

    // package private
    SettingDescriptorInfo(SettingDescriptor settingDescriptor) {
        this.name = settingDescriptor.getName();
        this.label = settingDescriptor.getLabel();
        this.description = settingDescriptor.getDescription();
        this.typeName = settingDescriptor.getTypeName();
        this.contentType = settingDescriptor.getContentType();
        this.defaultValue = settingDescriptor.getDefaultValue();
    }

    private SettingDescr