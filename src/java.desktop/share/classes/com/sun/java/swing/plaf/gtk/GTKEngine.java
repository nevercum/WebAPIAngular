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
        VSEPARATOR, VSLIDE