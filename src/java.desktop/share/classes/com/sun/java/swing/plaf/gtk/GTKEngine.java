/*
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.swing.plaf.gtk;

import java.awt.*;
import java.awt.image.*;
import java.util.HashMap;
import javax.swing.*;
import javax.swing.plaf.synth.*;

import com.sun.java.swing.plaf.gtk.GTKConstants.ArrowType;
import com.sun.java.swing.plaf.gtk.GTKConstants.ExpanderStyle;
import com.sun.java.swing.plaf.gtk.GTKConstants.Orientation;
import com.sun.java.swing.plaf.gtk.GTKConstants.PositionType;
import com.sun.java.swing.plaf.gtk.GTKConstants.ShadowType;
import com.sun.java.swing.plaf.gtk.GTKConstants.TextDirection;

import sun.awt.image.SunWritableRaster;
import sun.swing.ImageCache;

/**
 * GTKEngine delegates all painting job to native GTK libraries.
 *
 * Painting with GTKEngine looks like this:
 * First, startPainting() is called. It prepares an offscreen buffer of the
 *   required size.
 * Then, any number of paintXXX() methods can be called. They effectively ignore
 *   the Graphics parameter and draw to the offscreen buffer.
 * Finally, finishPainting() should be called. It fills the data buffer passed
 *   in with the image data.
 *
 * @author Josh Outwater
 */
class GTKEngine {

    static final GTKEngine INSTANCE = new GTKEngine();

    /** Size of the image cache */
    private static final int CACHE_SIZE = 50;

    /** This enum mirrors that in gtk2_interface.h */
    static enum WidgetType {
        BUTTON, CHECK_BOX, CHECK_BOX_MENU_ITEM, COLOR_CHOOSER,
        COMBO_BOX, COMBO_BOX_ARROW_BUTTON, COMBO_BOX_TEXT_FIELD,
        DESKTOP_ICON, DESKTOP_PANE, EDITOR_PANE, FORMATTED_TEXT_FIELD,
        HANDLE_BOX, HPROGRESS_BAR,
        HSCROLL_BAR, HSCROLL_BAR_BUTTON_LEFT, HSCROLL_BAR_BUTTON_RIGHT,
        HSCROLL_BAR_TRACK, HSCROLL_BAR_THUMB,
        HSEPARATOR, HSLIDER, HSLIDER_TRACK, HSLIDER_THUMB, HSPLIT_PANE_DIVIDER,
        INTERNAL_FRAME, INTERNAL_FRAME_TITLE_PANE, IMAGE, LABEL, LIST, MENU,
        MENU_BAR, MENU_ITEM, MENU_ITEM_ACCELERATOR, OPTION_PANE, PANEL,
        PASSWORD_FIELD, POPUP_MENU, POPUP_MENU_SEPARATOR,
        RADIO_BUTTON, RADIO_BUTTON_MENU_ITEM, ROOT_PANE, SCROLL_PANE,
        SPINNER, SPINNER_ARROW_BUTTON, SPINNER_TEXT_FIELD,
        SPLIT_PANE, TABBED_PANE, TABBED_PANE_TAB_AREA, TABBED_PANE_CONTENT,
        TABBED_PANE_TAB, TABLE, TABLE_HEADER, TEXT_AREA, TEXT_FIELD, TEXT_PANE,
        TITLED_BORDER,
        TOGGLE_BUTTON, TOOL_BAR, TOOL_BAR_DRAG_WINDOW, TOOL_BAR_SEPARATOR,
        TOOL_TIP, TREE, TREE_CELL, VIEWPORT, VPROGRESS_BAR,
        VSCROLL_BAR, VSCROLL_BAR_BUTTON_UP, VSCROLL_BAR_BUTTON_DOWN,
        VSCROLL_BAR_TRACK, VSCROLL_BAR_THUMB,
        VSEPARATOR, VSLIDER, VSLIDER_TRACK, VSLIDER_THUMB,
        VSPLIT_PANE_DIVIDER
    }

    /**
     * Representation of GtkSettings properties.
     * When we need more settings we can add them here and
     * to all implementations of getGTKSetting().
     */
    static enum Settings {
        GTK_FONT_NAME,
        GTK_ICON_SIZES,
        GTK_CURSOR_BLINK,
        GTK_CURSOR_BLINK_TIME
    }

    /* Custom regions are needed for representing regions that don't exist
     * in the original Region class.
     */
    static class CustomRegion extends Region {
        /*
         * TITLED_BORDER Region is mapped to GtkFrame class which can draw
         * titled borders around components.
         */
        static Region TITLED_BORDER = new CustomRegion("TitledBorder");

        private CustomRegion(String name) {
            super(name, null, false);
        }
    }


    private static HashMap<Region, Object> regionToWidgetTypeMap;
    private ImageCache cache = new ImageCache(CACHE_SIZE);
    private int x0, y0, w0, h0;
    private Graphics graphics;
    private Object[] cacheArgs;

    private native void native_paint_arrow(
            int widgetType, int state, int shadowType, String detail,
            int x, int y, int width, int height, int arrowType);
    private native void native_paint_box(
            int widgetType, int state, int shadowType, String detail,
            int x, int y, int width, int height, int synthState, int dir);
    private native void native_paint_box_gap(
            int widgetType, int state, int shadowType, String detail,
            int x, int y, int width, int height,
            int gapSide, int gapX, int gapWidth);
    private native void native_paint_check(
            int widgetType, int synthState, String detail,
            int x, int y, int width, int height);
    private native void native_paint_expander(
            int widgetType, int state, String detail,
            int x, int y, int width, int height, int expanderStyle);
    private native void native_paint_extension(
            int widgetType, int state, int shadowType, String detail,
            int x, int y, int width, int height, int placement);
    private native void native_paint_flat_box(
            int widgetType, int state, int shadowType, String detail,
            int x, int y, int width, int height, boolean hasFocus);
    private native void native_paint_focus(
            int widgetType, int state, String detail,
            int x, int y, int width, int height);
    private native void native_paint_handle(
            int widgetType, int state, int shadowType, String detail,
            int x, int y, int width, int height, int orientation);
    private native void native_paint_hline(
            int widgetType, int state, String detail,
            int x, int y, int width, int height);
    private native void native_paint_option(
            int widgetType, int synthState, String detail,
            int x, int y, int width, int height);
    private native void native_paint_shadow(
            int widgetType, int state, int shadowType, String detail,
            int x, int y, int width, int height, int synthState, int dir);
    private native void native_paint_slider(
            int widgetType, int state, int shadowType, String detail, int x,
            int y, int width, int height, int orientation, boolean hasFocus);
    private native void native_paint_vline(
            int widgetType, int state, String detail,
            int x, int y, int width, int height);
    private native void native_paint_background(
            int widgetType, int state, int x, int y, int width, int height);
    private native Object native_get_gtk_setting(int property);
    private native void nativeSetRangeValue(int widgetType, double value,
                                            double min, double max,
                                            double visible);

    private native void nativeStartPainting(int w, int h);
    private native int nativeFinishPainting(int[] buffer, int width, int height);
    private native void native_switch_theme();

    static {
        // Make sure the awt toolkit is loaded so we have access to native
        // methods.
        Toolkit.getDefaultToolkit();

        // Initialize regionToWidgetTypeMap
        regionToWidgetTypeMap = new HashMap<Region, Object>(50);
        regionToWidgetTypeMap.put(Region.ARROW_BUTTON, new WidgetType[] {
            WidgetType.SPINNER_ARROW_BUTTON,
            WidgetType.COMBO_BOX_ARROW_BUTTON,
            WidgetType.HSCROLL_BAR_BUTTON_LEFT,
            WidgetType.HSCROLL_BAR_BUTTON_RIGHT,
            WidgetType.VSCROLL_BAR_BUTTON_UP,
            WidgetType.VSCROLL_BAR_BUTTON_DOWN});
        regionToWidgetTypeMap.put(Region.BUTTON, WidgetType.BUTTON);
        regionToWidgetTypeMap.put(Region.CHECK_BOX, WidgetType.CHECK_BOX);
        regionToWidgetTypeMap.put(Region.CHECK_BOX_MENU_ITEM,
                                  WidgetType.CHECK_BOX_MENU_ITEM);
        regionToWidgetTypeMap.put(Region.COLOR_CHOOSER, WidgetType.COLOR_CHOOSER);
        regionToWidgetTypeMap.put(Region.FILE_CHOOSER, WidgetType.OPTION_PANE);
        regionToWidgetTypeMap.put(Region.COMBO_BOX, WidgetType.COMBO_BOX);
        regionToWidgetTypeMap.put(Region.DESKTOP_ICON, WidgetType.DESKTOP_ICON);
        regionToWidgetTypeMap.put(Region.DESKTOP_PANE, WidgetType.DESKTOP_PANE);
        regionToWidgetTypeMap.put(Region.EDITOR_PANE, WidgetType.EDITOR_PANE);
        regionToWidgetTypeMap.put(Region.FORMATTED_TEXT_FIELD, new WidgetType[] {
            WidgetType.FORMATTED_TEXT_FIELD, WidgetType.SPINNER_TEXT_FIELD});
        regionToWidgetTypeMap.put(GTKRegion.HANDLE_BOX, WidgetType.HANDLE_BOX);
        regionToWidgetTypeMap.put(Region.INTERNAL_FRAME,
                                  WidgetType.INTERNAL_FRAME);
        regionToWidgetTypeMap.put(Region.INTERNAL_FRAME_TITLE_PANE,
                                  WidgetType.INTERNAL_FRAME_TITLE_PANE);
        regionToWidgetTypeMap.put(Region.LABEL, new WidgetType[] {
            WidgetType.LABEL, WidgetType.COMBO_BOX_TEXT_FIELD});
        regionToWidgetTypeMap.put(Region.LIST, WidgetType.LIST);
        regionToWidgetTypeMap.put(Region.MENU, WidgetType.MENU);
        regionToWidgetTypeMap.put(Region.MENU_BAR, WidgetType.MENU_BAR);
        regionToWidgetTypeMap.put(Region.MENU_ITEM, WidgetType.MENU_ITEM);
        regionToWidgetTypeMap.put(Region.MENU_ITEM_ACCELERATOR,
                                  WidgetType.MENU_ITEM_ACCELERATOR);
        regionToWidgetTypeMap.put(Region.OPTION_PANE, WidgetType.OPTION_PANE);
        regionToWidgetTypeMap.put(Region.PANEL, WidgetType.PANEL);
        regionToWidgetTypeMap.put(Region.PASSWORD_FIELD,
                                  WidgetType.PASSWORD_FIELD);
        regionToWidgetTypeMap.put(Region.POPUP_MENU, WidgetType.POPUP_MENU);
        regionToWidgetTypeMap.put(Region.POPUP_MENU_SEPARATOR,
                                  WidgetType.POPUP_MENU_SEPARATOR);
        regionToWidgetTypeMap.put(Region.PROGRESS_BAR, new WidgetType[] {
            WidgetType.HPROGRESS_BAR, WidgetType.VPROGRESS_BAR});
        regionToWidgetTypeMap.put(Region.RADIO_BUTTON, WidgetType.RADIO_BUTTON);
        regionToWidgetTypeMap.put(Region.RADIO_BUTTON_MENU_ITEM,
                                  WidgetType.RADIO_BUTTON_MENU_ITEM);
        regionToWidgetTypeMap.put(Region.ROOT_PANE, WidgetType.ROOT_PANE);
        regionToWidgetTypeMap.put(Region.SCROLL_BAR, new WidgetType[] {
            WidgetType.HSCROLL_BAR, WidgetType.VSCROLL_BAR});
        regionToWidgetTypeMap.put(Region.SCROLL_BAR_THUMB, new WidgetType[] {
            WidgetType.HSCROLL_