/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.shell;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.AbstractMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.MultiResolutionImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.swing.SwingConstants;

// NOTE: This class supersedes Win32ShellFolder, which was removed from
//       distribution after version 1.4.2.

/**
 * Win32 Shell Folders
 * <P>
 * <BR>
 * There are two fundamental types of shell folders : file system folders
 * and non-file system folders.  File system folders are relatively easy
 * to deal with.  Non-file system folders are items such as My Computer,
 * Network Neighborhood, and the desktop.  Some of these non-file system
 * folders have special values and properties.
 * <P>
 * <BR>
 * Win32 keeps two basic data structures for shell folders.  The first
 * of these is called an ITEMIDLIST.  Usually a pointer, called an
 * LPITEMIDLIST, or more frequently just "PIDL".  This structure holds
 * a series of identifiers and can be either relative to the desktop
 * (an absolute PIDL), or relative to the shell folder that contains them.
 * Some Win32 functions can take absolute or relative PIDL values, and
 * others can only accept relative values.
 * <BR>
 * The second data structure is an IShellFolder COM interface.  Using
 * this interface, one can enumerate the relative PIDLs in a shell
 * folder, get attributes, etc.
 * <BR>
 * All Win32ShellFolder2 objects which are folder types (even non-file
 * system folders) contain an IShellFolder object. Files are named in
 * directories via relative PIDLs.
 *
 * @author Michael Martak
 * @author Leif Samuelsson
 * @author Kenneth Russell
 * @since 1.4 */
@SuppressWarnings("serial") // JDK-implementation class
final class Win32ShellFolder2 extends ShellFolder {

    static final int SMALL_ICON_SIZE = 16;
    static final int LARGE_ICON_SIZE = 32;
    static final int MIN_QUALITY_ICON = 16;
    static final int MAX_QUALITY_ICON = 256;
    private static final int[] ICON_RESOLUTIONS
            = {16, 24, 32, 48, 64, 72, 96, 128, 256};

    static final int FILE_ICON_ID = 1;
    static final int FOLDER_ICON_ID = 4;

    private static native void initIDs();

    static {
        initIDs();
    }

    // Win32 Shell Folder Constants
    public static final int DESKTOP = 0x0000;
    public static final int INTERNET = 0x0001;
    public static final int PROGRAMS = 0x0002;
    public static final int CONTROLS = 0x0003;
    public static final int PRINTERS = 0x0004;
    public static final int PERSONAL = 0x0005;
    public static final int FAVORITES = 0x0006;
    public static final int STARTUP = 0x0007;
    public static final int RECENT = 0x0008;
    public static final int SENDTO = 0x0009;
    public static final int BITBUCKET = 0x000a;
    public static final int STARTMENU = 0x000b;
    public static final int DESKTOPDIRECTORY = 0x0010;
    public static final int DRIVES = 0x0011;
    public static final int NETWORK = 0x0012;
    public static final int NETHOOD = 0x0013;
    public static final int FONTS = 0x0014;
    public static final int TEMPLATES = 0x0015;
    public static final int COMMON_STARTMENU = 0x0016;
    public static final int COMMON_PROGRAMS = 0X0017;
    public static final int COMMON_STARTUP = 0x0018;
    public static final int COMMON_DESKTOPDIRECTORY = 0x0019;
    public static final int APPDATA = 0x001a;
    public static final int PRINTHOOD = 0x001b;
    public static final int ALTSTARTUP = 0x001d;
    public static final int COMMON_ALTSTARTUP = 0x001e;
    public static final int COMMON_FAVORITES = 0x001f;
    public static final int INTERNET_CACHE = 0x0020;
    public static final int COOKIES = 0x0021;
    public static final int HISTORY = 0x0022;

    // Win32 shell folder attributes
    public static final int ATTRIB_CANCOPY          = 0x00000001;
    public static final int ATTRIB_CANMOVE          = 0x00000002;
    public static final int ATTRIB_CANLINK          = 0x00000004;
    public static final int ATTRIB_CANRENAME        = 0x00000010;
    public static final int ATTRIB_CANDELETE        = 0x00000020;
    public static final int ATTRIB_HASPROPSHEET     = 0x00000040;
    public static final int ATTRIB_DROPTARGET       = 0x00000100;
    public static final int ATTRIB_LINK             = 0x00010000;
    public static final int ATTRIB_SHARE            = 0x00020000;
    public static final int ATTRIB_READONLY         = 0x00040000;
    public static final int ATTRIB_GHOSTED          = 0x00080000;
    public static final int ATTRIB_HIDDEN           = 0x00080000;
    public static final int ATTRIB_FILESYSANCESTOR  = 0x10000000;
    public static final int ATTRIB_FOLDER           = 0x20000000;
    public static final int ATTRIB_FILESYSTEM       = 0x40000000;
    public static final int ATTRIB_HASSUBFOLDER     = 0x80000000;
    public static final int ATTRIB_VALIDATE         = 0x01000000;
    public static final int ATTRIB_REMOVABLE        = 0x02000000;
    public static final int ATTRIB_COMPRESSED       = 0x04000000;
    public static final int ATTRIB_BROWSABLE        = 0x08000000;
    public static final int ATTRIB_NONENUMERATED    = 0x00100000;
    public static final int ATTRIB_NEWCONTENT       = 0x00200000;

    // IShellFolder::GetDisplayNameOf constants
    public static final int SHGDN_NORMAL            = 0;
    public static final int SHGDN_INFOLDER          = 1;
    public static final int SHGDN_INCLUDE_NONFILESYS= 0x2000;
    public static final int SHGDN_FORADDRESSBAR     = 0x4000;
    public static final int SHGDN_FORPARSING        = 0x8000;

    /** The referent to be registered with the Disposer. */
    private Object disposerReferent = new Object();

    // Values for system call LoadIcon()
    public enum SystemIcon {
        IDI_APPLICATION(32512),
        IDI_HAND(32513),
        IDI_ERROR(32513),
        IDI_QUESTION(32514),
        IDI_EXCLAMATION(32515),
        IDI_WARNING(32515),
        IDI_ASTERISK(32516),
        IDI_INFORMATION(32516),
        IDI_WINLOGO(32517);

        private final int iconID;

        SystemIcon(int iconID) {
            this.iconID = iconID;
        }

        public int getIconID() {
            return iconID;
        }
    }

    // Known Folder data
    static final class KnownFolderDefinition {
        String guid;
        int category;
        String name;
        String description;
        String parent;
        String relativePath;
        String parsingName;
        String tooltip;
        String localizedName;
        String icon;
        String security;
        long attributes;
        int defenitionFlags;
        String ftidType;
        String path;
        String saveLocation;
    }

    static final class KnownLibraries {
        static final List<KnownFolderDefinition> INSTANCE = getLibraries();
    }

    static class FolderDisposer implements sun.java2d.DisposerRecord {
        /*
         * This is cached as a concession to getFolderType(), which needs
         * an absolute PIDL.
         */
        long absolutePIDL;
        /*
         * We keep track of shell folders through the IShellFolder
         * interface of their parents plus their relative PIDL.
         */
        long pIShellFolder;
        long relativePIDL;

        boolean disposed;
        public void dispose() {
            if (disposed) return;
            invoke(new Callable<Void>() {
                public Void call() {
                    if (relativePIDL != 0) {
                        releasePIDL(relativePIDL);
                    }
                    if (absolutePIDL != 0) {
                        releasePIDL(absolutePIDL);
                    }
                    if (pIShellFolder != 0) {
                        releaseIShellFolder(pIShellFolder);
                    }
                    return null;
                }
            });
            disposed = true;
        }
    }
    FolderDisposer disposer = new FolderDisposer();
    private void setIShellFolder(long pIShellFolder) {
        disposer.pIShellFolder = pIShellFolder;
    }
    private void setRelativePIDL(long relativePIDL) {
        disposer.relativePIDL = relativePIDL;
    }
    /*
     * The following are for caching various shell folder properties.
     */
    private long pIShellIcon = -1L;
    private String folderType = null;
    private String displayName = null;
    private Image smallIcon = null;
    private Image largeIcon = null;
    private Boolean isDir = null;
    private final boolean isLib;
    private static final String FNAME = COLUMN_NAME;
    private static final String FSIZE = COLUMN_SIZE;
    private static final String FTYPE = "FileChooser.fileTypeHeaderText";
    private static final String FDATE = COLUMN_DATE;

    /*
     * The following is to identify the My Documents folder as being special
     */
    private boolean isPersonal;

    private static String composePathForCsidl(int csidl) throws IOException, InterruptedException {
        String path = getFileSystemPath(csidl);
        return path == null
                ? ("ShellFolder: 0x" + Integer.toHexString(csidl))
                : path;
    }

    /**
     * Create a system special shell folder, such as the
     * desktop or Network Neighborhood.
     */
    Win32ShellFolder2(final int csidl) throws IOException, InterruptedException {
        // Desktop is parent of DRIVES and NETWORK, not necessarily
        // other special shell folders.
        super(null, composePathForCsidl(csidl));
        isLib = false;

        invoke(new Callable<Void>() {
            public Void call() throws InterruptedException {
                if (csidl == DESKTOP) {
                    initDesktop();
                } else {
                    initSpecial(getDesktop().getIShellFolder(), csidl);
                    // At this point, the native method initSpecial() has set our relativePIDL
                    // relative to the Desktop, which may not be our immediate parent. We need
                    // to traverse this ID list and break it into a chain of shell folders from
                    // the top, with each one having an immediate parent and a relativePIDL
                    // relative to that parent.
                    long pIDL = disposer.relativePIDL;
                    parent = getDesktop();
                    while (pIDL != 0) {
                        // Get a child pidl relative to 'parent'
                        long childPIDL = copyFirstPIDLEntry(pIDL);
                        if (childPIDL != 0) {
                            // Get a handle to the rest of the ID list
                            // i,e, parent's grandchilren and down
                            pIDL = getNextPIDLEntry(pIDL);
                            if (pIDL != 0) {
                                // Now we know that parent isn't immediate to 'this' because it
                                // has a continued ID list. Create a shell folder for this child
                                // pidl and make it the new 'parent'.
                                parent = createShellFolder((Win32ShellFolder2) parent, childPIDL);
                            } else {
                                // No grandchildren means we have arrived at the parent of 'this',
                                // and childPIDL is directly relative to parent.
                                disposer.relativePIDL = childPIDL;
                            }
                        } else {
                            break;
                        }
                    }
                }
                return null;
            }
        }, InterruptedException.class);

        sun.java2d.Disposer.addObjectRecord(disposerReferent, disposer);
    }


    /**
     * Create a system shell folder
     */
    Win32ShellFolder2(Win32ShellFolder2 parent, long pIShellFolder, long relativePIDL, String path, boolean isLib) {
        super(parent, (path != null) ? path : "ShellFolder: ");
        this.isLib = isLib;
        this.disposer.pIShellFolder = pIShellFolder;
        this.disposer.relativePIDL = relativePIDL;
        sun.java2d.Disposer.addObjectRecord(disposerReferent, disposer);
    }


    /**
     * Creates a shell folder with a parent and relative PIDL
     */
    static Win32ShellFolder2 createShellFolder(Win32ShellFolder2 parent, long pIDL)
            throws InterruptedException {
        String path = invoke(new Callable<String>() {
            public String call() {
                return getFileSystemPath(parent.getIShellFolder(), pIDL);
            }
        }, RuntimeException.class);
        String libPath = resolveLibrary(path);
        if (libPath == null) {
            return new Win32ShellFolder2(parent, 0, pIDL, path, false);
        } else {
            return new Win32ShellFolder2(parent, 0, pIDL, libPath, true);
        }
    }

    // Initializes the desktop shell folder
    // NOTE: this method uses COM and must be called on the 'COM thread'. See ComInvoker for the details
    private native void initDesktop();

    // Initializes a special, non-file system shell folder
    // from one of the above constants
    // NOTE: this method uses COM and must be called on the 'COM thread'. See ComInvoker for the details
    private native void initSpecial(long desktopIShellFolder, int csidl);

    /** Marks this folder as being the My Documents (Personal) folder */
    public void setIsPersonal() {
        isPersonal = true;
    }

    /**
     * This method is implemented to make sure that no instances
     * of {@code ShellFolder} are ever serialized. If {@code isFileSystem()} returns
     * {@code true}, then the object is representable with an instance of
     * {@code java.io.File} instead. If not, then the object depends
     * on native PIDL state and should not be serialized.
     *
     * @return a {@code java.io.File} replacement object. If the folder
     * is a not a normal directory, then returns the first non-removable
     * drive (normally "C:\").
     */
    @Serial
    protected Object writeReplace() throws java.io.ObjectStreamException {
        return invoke(new Callable<File>() {
            public File call() {
                if (isFileSystem()) {
                    return new File(getPath());
                } else {
                    Win32ShellFolder2 drives = Win32ShellFolderManager2.getDrives();
                    if (drives != null) {
                        File[] driveRoots = drives.listFiles();
                        if (driveRoots != null) {
                            for (int i = 0; i < driveRoots.length; i++) {
                                if (driveRoots[i] instanceof Win32ShellFolder2) {
                                    Win32ShellFolder2 sf = (Win32ShellFolder2) driveRoots[i];
                                    if (sf.isFileSystem() && !sf.hasAttribute(ATTRIB_REMOVABLE)) {
                                        return new File(sf.getPath());
                                    }
                                }
                            }
                        }
                    }
                    // Ouch, we have no hard drives. Return something "valid" anyway.
                    return new File("C:\\");
                }
            }
        });
    }


    /**
     * Finalizer to clean up any COM objects or PIDLs used by this object.
     */
    protected void dispose() {
        disposer.dispose();
    }


    // Given a (possibly multi-level) relative PIDL (with respect to
    // the desktop, at least in all of the usage cases in this code),
    // return a pointer to the next entry. Does not mutate the PIDL in
    // any way. Returns 0 if the null terminator is reached.
    // Needs to be accessible to Win32ShellFolderManager2
    static native long getNextPIDLEntry(long pIDL);

    // Given a (possibly multi-level) relative PIDL (with respect to
    // the desktop, at