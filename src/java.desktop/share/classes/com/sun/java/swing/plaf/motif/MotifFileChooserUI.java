/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.swing.plaf.motif;

import javax.swing.*;
import javax.swing.filechooser.*;
import javax.swing.event.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import sun.awt.shell.ShellFolder;
import sun.swing.SwingUtilities2;

/**
 * Motif FileChooserUI.
 *
 * @author Jeff Dinkins
 */
public class MotifFileChooserUI extends BasicFileChooserUI {

    private FilterComboBoxModel filterComboBoxModel;

    protected JList<File> directoryList = null;
    protected JList<File> fileList = null;

    protected JTextField pathField = null;
    protected JComboBox<FileFilter> filterComboBox = null;
    protected JTextField filenameTextField = null;

    private static final Dimension hstrut10 = new Dimension(10, 1);
    private static final Dimension vstrut10 = new Dimension(1, 10);

    private static final Insets insets = new Insets(10, 10, 10, 10);

    private static Dimension prefListSize = new Dimension(75, 150);

    private static Dimension WITH_ACCELERATOR_PREF_SIZE = new Dimension(650, 450);
    private static Dimension PREF_SIZE = new Dimension(350, 450);
    private static final int MIN_WIDTH = 200;
    private static final int MIN_HEIGHT = 300;
    private static Dimension PREF_ACC_SIZE = new Dimension(10, 10);
    private static Dimension ZERO_ACC_SIZE = new Dimension(1, 1);

    private static Dimension MAX_SIZE = new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);

    private static final Insets buttonMargin = new Insets(3, 3, 3, 3);

    private JPanel bottomPanel;

    protected JButton approveButton;

    private String enterFolderNameLabelText = null;
    private int enterFolderNameLabelMnemonic = 0;
    private String enterFileNameLabelText = null;
    private int enterFileNameLabelMnemonic = 0;

    private String filesLabelText = null;
    private int filesLabelMnemonic = 0;

    private String foldersLabelText = null;
    private int foldersLabelMnemonic = 0;

    private String pathLabelText = null;
    private int pathLabelMnemonic = 0;

    private String filterLabelText = null;
    private int filterLabelMnemonic = 0;

    private JLabel fileNameLabel;

    private void populateFileNameLabel() {
        if (getFileChooser().getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY) {
            fileNameLabel.setText(enterFolderNameLabelText);
            fileNameLabel.setDisplayedMnemonic(enterFolderNameLabelMnemonic);
        } else {
            fileNameLabel.setText(enterFileNameLabelText);
            fileNameLabel.setDisplayedMnemonic(enterFileNameLabelMnemonic);
        }
    }

    private String fileNameString(File file) {
        if (file == null) {
            return null;
        } else {
            JFileChooser fc = getFileChooser();
            if (fc.isDirectorySelectionEnabled() && !fc.isFileSelectionEnabled()) {
                return file.getPath();
            } else {
                return file.getName();
            }
        }
    }

    private String fileNameString(File[] files) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; files != null && i < files.length; i++) {
            if (i > 0) {
                sb.append(" ");
            }
            if (files.length > 1) {
                sb.append("\"");
            }
            sb.append(fileNameString(files[i]));
            if (files.length > 1) {
                sb.append("\"");
            }
        }
        return sb.toString();
    }

    public MotifFileChooserUI(JFileChooser filechooser) {
        super(filechooser);
    }

    public String getFileName() {
        if(filenameTextField != null) {
            return filenameTextField.getText();
        } else {
            return null;
        }
    }

    public void setFileName(String filename) {
        if(filenameTextField != null) {
            filenameTextField.setText(filename);
        }
    }

    public String getDirectoryName() {
        return pathField.getText();
    }

    public void setDirectoryName(String dirname) {
        pathField.setText(dirname);
    }

    public void ensureFileIsVisible(JFileChooser fc, File f) {
        // PENDING(jeff)
    }

    public void rescanCurrentDirectory(JFileChooser fc) {
        getModel().validateFileCache();
    }

    public PropertyChangeListener createPropertyChangeListener(JFileChooser fc) {
        return new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                String prop = e.getPropertyName();
                if(prop.equals(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY)) {
                    File f = (File) e.getNewValue();
                    if(f != null) {
                        setFileName(getFileChooser().getName(f));
                    }
                } else if (prop.equals(JFileChooser.SELECTED_FILES_CHANGED_PROPERTY)) {
                    File[] files = (File[]) e.getNewValue();
                    JFileChooser fc = getFileChooser();
                    if (files != null && files.length > 0 && (files.length > 1 || fc.isDirectorySelectionEnabled()
                            || !files[0].isDirectory())) {
                        setFileName(fileNameString(files));
                    }
                } else if (prop.equals(JFileChooser.FILE_FILTER_CHANGED_PROPERTY)) {
                    fileList.clearSelection();
                } else if(prop.equals(JFileChooser.DIRECTORY_CHANGED_PROPERTY)) {
                    directoryList.clearSelection();
                    ListSelectionModel sm = directoryList.getSelectionModel();
                    if (sm instanceof DefaultListSelectionModel) {
                        ((DefaultListSelectionModel)sm).moveLeadSelectionIndex(0);
                        sm.setAnchorSelectionIndex(0);
                    }
                    fileList.clearSelection();
                    sm = fileList.getSelectionModel();
                    if (sm instanceof DefaultListSelectionModel) {
                        ((DefaultListSelectionModel)sm).moveLeadSelectionIndex(0);
                        sm.setAnchorSelectionIndex(0);
                    }
                    File currentDirectory = getFileChooser().getCurrentDirectory();
                    if(currentDirectory != null) {
                        try {
                            setDirectoryName(ShellFolder.getNormalizedFile((File)e.getNewValue()).getPath());
                        } catch (IOException ioe) {
                            setDirectoryName(((File)e.getNewValue()).getAbsolutePath());
                        }
                        if ((getFileChooser().getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY) && !getFileChooser().isMultiSelectionEnabled()) {
                            setFileName(getDirectoryName());
                        }
                    }
                } else if(prop.equals(JFileChooser.FILE_SELECTION_MODE_CHANGED_PROPERTY)) {
                    if (fileNameLabel != null) {
                        populateFileNameLabel();
                    }
                    directoryList.clearSelection();
                } else if (prop.equals(JFileChooser.MULTI_SELECTION_ENABLED_CHANGED_PROPERTY)) {
                    if(getFileChooser().isMultiSelectionEnabled()) {
                        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                    } else {
                        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                        fileList.clearSelection();
                        getFileChooser().setSelectedFiles(null);
                    }
                } else if (prop.equals(JFileChooser.ACCESSORY_CHANGED_PROPERTY)) {
                    if(getAccessoryPanel() != null) {
                        if(e.getOldValue() != null) {
                            getAccessoryPanel().remove((JComponent) e.getOldValue());
                        }
                        JComponent accessory = (JComponent) e.getNewValue();
                        if(accessory != null) {
                            getAccessoryPanel().add(accessory, BorderLayout.CENTER);
                            getAccessoryPanel().setPreferredSize(PREF_ACC_SIZE);
                            getAccessoryPanel().setMaximumSize(MAX_SIZE);
                        } else {
                            getAccessoryPanel().setPreferredSize(ZERO_ACC_SIZE);
                            getAccessoryPanel().setMaximumSize(ZERO_ACC_SIZE);
                        }
                    }
                } else if (prop.equals(JFileChooser.APPROVE_BUTTON_TEXT_CHANGED_PROPERTY) ||
                        prop.equals(JFileChooser.APPROVE_BUTTON_TOOL_TIP_TEXT_CHANGED_PROPERTY) ||
                        prop.equals(JFileChooser.DIALOG_TYPE_CHANGED_PROPERTY)) {
                    approveButton.setText(getApproveButtonText(getFileChooser()));
                    approveButton.setToolTipText(getApproveButtonToolTipText(getFileChooser()));
                } else if (prop.equals(JFileChooser.CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY)) {
                    doControlButtonsChanged(e);
                } else if (prop.equals("componentOrientation")) {
                    ComponentOrientation o = (ComponentOrientation)e.getNewValue();
                    JFileChooser cc = (JFileChooser)e.getSource();
                    if (o != (ComponentOrientation)e.getOldValue()) {
                        cc.applyComponentOrientation(o);
                    }
                }
            }
        };
    }

    //
    // ComponentUI Interface Implementation methods
    //
    public static ComponentUI createUI(JComponent c) {
        return new MotifFileChooserUI((JFileChooser)c);
    }

    public void installUI(JComponent c) {
        super.installUI(c);
    }

    public void uninstallUI(JComponent c) {
        c.removePropertyChangeListener(filterComboBoxModel);
        approveButton.removeActionListener(getApproveSelectionAction());
        filenameTextField.removeActionListener(getApproveSelectionAction());
        super.uninstallUI(c);
    }

    public void installComponents(JFileChooser fc) {
        fc.setLayout(new BorderLayout(10, 10));
        fc.setAlignmentX(JComponent.CENTER_ALIGNMENT);

        @SuppressWarnings("serial") // anonymous class
        JPanel interior = new JPanel() {
            public Insets getInsets() {
                return insets;
            }
        };
        interior.setInheritsPopupMenu(true);
        align(interior);
        interior.setLayout(new BoxLayout(interior, BoxLayout.PAGE_AXIS));

        fc.add(interior, BorderLayout.CENTER);

        // PENDING(jeff) - I18N
        JLabel l = new JLabel(pathLabelText);
        l.setDisplayedMnemonic(pathLabelMnemonic);
        align(l);
        interior.add(l);

        File currentDirectory = fc.getCurrentDirectory();
        String curDirName = null;
        if(currentDirectory != null) {
            curDirName = currentDirectory.getPath();
        }

        @SuppressWarnings("serial") // anonymous class
        JTextField tmp1 = new JTextField(curDirName, 35) {
            public Dimension getMaximumSize() {
                Dimension d = super.getMaximumSize();
                d.height = getPreferredSize().height;
                return d;
            }
        };
        pathField = tmp1;
        pathField.setInheritsPopupMenu(true);
        l.setLabelFor(pathField);
        align(pathField);

        // Change to folder on return
        pathField.addActionListener(getUpdateAction());
        interior.add(pathField);

        interior.ad