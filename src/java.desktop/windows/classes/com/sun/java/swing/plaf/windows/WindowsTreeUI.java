/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.swing.plaf.windows;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.io.Serializable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

import static com.sun.java.swing.plaf.windows.TMSchema.Part;
import static com.sun.java.swing.plaf.windows.TMSchema.State;
import static com.sun.java.swing.plaf.windows.XPStyle.Skin;

/**
 * A Windows tree.
 *
 * @author Scott Violet
 */
public class WindowsTreeUI extends BasicTreeUI {

    public static ComponentUI createUI( JComponent c )
      {
        return new WindowsTreeUI();
      }


    /**
      * Ensures that the rows identified by beginRow through endRow are
      * visible.
      */
    protected void ensureRowsAreVisible(int beginRow, int endRow) {
        if(tree != null && beginRow >= 0 && endRow < getRowCount(tree)) {
            Rectangle visRect = tree.getVisibleRect();
            if(beginRow == endRow) {
                Rectangle     scrollBounds = getPathBounds(tree, getPathForRow
                                                           (tree, beginRow));

                if(scrol