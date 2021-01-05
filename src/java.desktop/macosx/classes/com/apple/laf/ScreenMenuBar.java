/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.apple.laf;

import sun.lwawt.macosx.CMenuBar;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;

import static sun.awt.AWTAccessor.*;

@SuppressWarnings("serial") // JDK implementation class
public class ScreenMenuBar extends MenuBar
        implements ContainerListener, ScreenMenuPropertyHandler,
                   ComponentListener {

    static boolean sJMenuBarHasHelpMenus = false; //$ could check by calling getHelpMenu in a try block

    JMenuBar fSwingBar;
    Hashtable<JMenu, ScreenMenu> fSubmenus;

    ScreenMenuPropertyListener fPropertyListener;
    ScreenMenuPropertyListener fAccessibleListener;

    public ScreenMenuBar(final JMenuBar swingBar) {
        fSwingBar = swingBar;
        fSubmenus = new Hashtable<JMenu, ScreenMenu>(fSwingBar.getMenuCount());
    }

    public void addNotify() {
        super.addNotify();

        fSwingBar.addContainerListener(this);
        fPropertyListener = new ScreenMenuPropertyListener(this);
        fSwingBar.addPropertyChangeListener(fPropertyListener);
        fAccessibleListener = new ScreenMenuPropertyListener(this);
        fSwingBar.getAccessibleContext().addPropertyChangeListener(fAccessibleListener);

        // We disable component events when the menu bar is not parented.  So now we need to
        // sync back up with the current state of the JMenuBar.  We first add the menus we
        // don't have and then remove the items that are no longer on the JMenuBar.
        final int count = fSwingBar.getMenuCount();
        for(int i = 0; i < count ; i++) {
            final JMenu m = fSwingBar.getMenu(i);
            if (m != null) {
                addSubmenu(m);
            }
        }

        final Enumeration<JMenu> e = fSubmenus.keys();
        while (e.hasMoreElements()) {
            final JMenu m = e.nextElement();
            if (fSwingBar.getComponentIndex(m) == -1) {
                removeSubmenu(m);
            }
        }
    }

    public void removeNotify() {
        // KCH - 3974930 - We do null checks for fSwingBar and fSubmenus because some people are using
        // reflection to muck about with our ivars
        if (fSwingBar != null) {
            fSwingBar.removePropertyChangeListener(fPropertyListener);
            fSwingBar.getAccessibleContext().removePropertyChangeListener(fAccessibleListener);
            fSwingBar.removeContainerListener(this);
        }

        fPropertyListener = null;
        fAccessibleListener = null;

        if (fSubmenus != null) {
            // We don't listen to events when the menu bar is not parented.
            // Remove all the component listeners.
            final Enumeration<JMenu> e = fSubmenus.keys();
            whi