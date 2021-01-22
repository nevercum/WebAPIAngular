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

package sun.awt.im;

import java.awt.AWTEvent;
import java.awt.AWTKeyStroke;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.im.InputMethodRequests;
import java.awt.im.spi.InputMethod;
import java.lang.Character.Subset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import sun.util.logging.PlatformLogger;
import sun.awt.SunToolkit;

/**
 * This InputContext class contains parts of the implementation of
 * java.text.im.InputContext. These parts have been moved
 * here to avoid exposing protected members that are needed by the
 * subclass InputMethodContext.
 *
 * @see java.awt.im.InputContext
 * @author JavaSoft Asia/Pacific
 */

public class InputContext extends java.awt.im.InputContext
                          implements ComponentListener, WindowListener {
    private static final PlatformLogger log = PlatformLogger.getLogger("sun.awt.im.InputContext");
    // The current input method is represented by two objects:
    // a locator is used to keep information about the selected
    // input method and locale until we actually need a real input
    // method; only then the input method itself is created.
    // Once there is an input method, the input method's locale
    // takes precedence over locale information in the locator.
    private InputMethodLocator inputMethodLocator;
    private InputMethod inputMethod;
    private boolean inputMethodCreationFailed;

    // holding bin for previously used input method instances, but not the current one
    private HashMap<InputMethodLocator, InputMethod> usedInputMethods;

    // the current client component is kept until the user focuses on a different
    // client component served by the same input context. When that happens, we call
    // endComposition so that text doesn't jump from one component to another.
    private Component currentClientComponent;
    private Component awtFocussedComponent;
    private boolean   isInputMethodActive;
    private Subset[]  characterSubsets = null;

    // true if composition area has been set to invisible when focus was los