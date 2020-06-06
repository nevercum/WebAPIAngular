/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */


package com.sun.java.swing.ui;

import com.sun.java.swing.action.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;
import javax.swing.*;

// Referenced classes of package com.sun.java.swing.ui:
//            CommonUI

public class WizardDlg extends JDialog
{
    private class CancelListener
        implements ActionListener
    {

        public void actionPerformed(ActionEvent evt)
        {
            if(cancelListener != null)
                cancelListener.actionPerformed(evt);
            setVisible(false);
        }

        private CancelListener()
        {
        }

    }

    private class FinishListener
        implements ActionListener
    {

        public void actionPerformed(ActionEvent evt)
        {
            if(finishListener != null)
                finishListener.actionPerformed(evt);
            setVisible(false);
        }

        private FinishListener()
        {
        }

    }

    private class NextListener
        implements ActionListener
    {

        public void actionPerformed(ActionEvent evt)
        {
            cardShowing++;
            if(cardShowing > numCards)
                cardShowing = numCards;
            else
                panesLayout.next(panesPanel);
            if(nextListener != null)
                nextListener.actionPerformed(evt);
            enableBackNextButtons();
        }

        private NextListener()
        {
        }

    }

    private class BackListener
        implements ActionListener
    {

        public void actionPerformed(ActionEvent evt)
        {
            cardShowing--;
            if(cardShowing < 1)
                cardShowing = 1;
            else
                panesLayout.previous(panesPanel);
            if(backListener != null)
                backListener.actionPerformed(evt);
            enableBackNextButtons();
        }

        private BackListener()
        {
        }

    }


    public WizardDlg(JFrame frame, String title, Vector panels, Vector images)
    {
        super(frame, title, true);
        this.title = title;
        this.images = images;
        Container pane = getContentPane();
        pane.setLayout(new BorderLayout());
        panesLayout = new CardLayout();
        panesPanel = new JPanel(panesLayout);
        pane.add(panesPanel, "Center");
        pane.add(createButtonPanel(), "South");
        setPanels(panels);
        pack();
        CommonUI.centerComponent(this);
    }

    public WizardDlg(JFrame frame, String title, Vector panels)
    {
        this(frame, title, panels, null);
    }

    public WizardDlg(String title, Vector panels)
    {
        this(new JFrame(), title, panels, null);
    }

    public void setPanels(Vector panels)
    {
        numCards = panels.size();
        cardShowing = 1;
        this.panels = panels;
        panesPanel.removeAll();
        for(int i = 0; i < numCards; i++)
            panesPanel.add((JPanel)panels.elementAt(i), Integer.toString(i));

        validate();
        enableBackNextButtons();
    }

    public void reset()
    {
        cardShowing = 1;
        panesLayout.first(panesPanel);
        enableBackNextButtons();
    }

 