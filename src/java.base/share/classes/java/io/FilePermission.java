/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.nio.file.*;
import java.security.*;
import java.util.Enumeration;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.access.JavaIOFilePermissionAccess;
import jdk.internal.access.SharedSecrets;
import sun.nio.fs.DefaultFileSystemProvider;
import sun.security.action.GetPropertyAction;
import sun.security.util.FilePermCompat;
import sun.security.util.SecurityConstants;

/**
 * This class represents access to a file or directory.  A FilePermission consists
 * of a pathname and a set of actions valid for that pathname.
 * <P>
 * Pathname is the pathname of the file or directory granted the specified
 * actions. A pathname that ends in "/*" (where "/" is
 * the file separator character, {@code File.separatorChar}) indicates
 * all the files and directories contained in that directory. A pathname
 * that ends with "/-" indicates (recursively) all files
 * and subdirectories contained in that directory. Such a pathname is called
 * a wildcard pathname. Otherwise, it's a simple pathname.
 * <P>
 * A pathname consisting of the special token {@literal "<<ALL FILES>>"}
 * matches <b>any</b> file.
 * <P>
 * Note: A pathname consisting of a single "*" indicates all the files
 * in the current directory, while a pathname consisting of a single "-"
 * indicates all the files in the current directory and
 * (recursively) all files and subdirectories contained in the current
 * directory.
 * <P>
 * The actions to be granted are passed to the constructor in a string containing
 * a list of one or more comma-separated keywords. The possible keywords are
 * "read", "write", "execute", "delete", and "readlink". Their meaning is
 * defined as follows:
 *
 * <DL>
 *    <DT> read <DD> read permission
 *    <DT> write <DD> write permission
 *    <DT> execute
 *    <DD> execute permission. Allows {@code Runtime.exec} to
 *         be called. Corresponds to {@code SecurityManager.checkExec}.
 *    <DT> delete
 *    <DD> delete permission. Allows {@code File.delete} to
 *         be called. Corresponds to {@code SecurityManager.checkDelete}.
 *    <DT> readlink
 *    <DD> read link permission. Allows the target of a
 *         <a href="../nio/file/package-summary.html#links">symbolic link</a>
 *         to be read by invoking the {@link java.nio.file.Files#readSymbolicLink
 *         readSymbolicLink } method.
 * </DL>
 * <P>
 * The actions string is converted to lowercase before processing.
 * <P>
 * Be careful when granting FilePermissions. Think about the implications
 * of granting read and especially write access to various files and
 * directories. The {@literal "<<ALL FILES>>"} permission with write action is
 * especially dangerous. This grants permission to write to the entire
 * file system. One thing this effectively allows is replacement of the
 * system binary, including the JVM runtime environment.
 * <P>
 * Please note: Code can always read a file from the same
 * directory it's in (or a subdirectory of that directory); it does not
 * need explicit permission to do so.
 *
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 *
 *
 * @author Marianne Mueller
 * @author Roland Schemers
 * @since 1.2
 *
 * @serial exclude
 */

public final class FilePermission extends Permission implements Serializable {

    /**
     * Execute action.
     */
    private static final int EXECUTE = 0x1;
    /**
     * Write action.
     */
    private static final int WRITE   = 0x2;
    /**
     * Read action.
     */
    private static final int READ    = 0x4;
    /**
     * Delete action.
     */
    private static final int DELETE  = 0x8;
    /**
     * Read link action.
     */
    private static final int READLINK    = 0x10;

    /**
     * All actions (read,write,execute,delete,readlink)
     */
    private static final int ALL     = READ|WRITE|EXECUTE|DELETE|READLINK;
    /**
     * No actions.
     */
    private static final int NONE    = 0x0;

    // the actions mask
    private transient int mask;

    // does path indicate a directory? (wildcard or recursive)
    private transient boolean directory;

    // is it a recursive directory specification?
    private transient boolean recursive;

    /**
     * the actions string.
     *
     * @serial
     */
    private String actions; // Left null as long as possible, then
                            // created and re-used in the getAction function.

    // canonicalized dir path. used by the "old" behavior (nb == false).
    // In the case of directories, it is the name "/blah/*" or "/blah/-"
    // without the last character (the "*" or "-").

    private transient String cpath;

    // Following fields used by the "new" behavior (nb == true), in which
    // input path is not canonicalized. For compatibility (so that granting
    // FilePermission on "x" allows reading "`pwd`/x", an alternative path
    // can be added so that both can be used in an implies() check. Please note
    // the alternative path only deals with absolute/relative path, and does
    // not deal with symlink/target.

    private transient Path npath;       // normalized dir path.
    private transient Path npath2;      // alternative normalized dir path.
    private transient boolean allFiles; // whether this is <<ALL FILES>>
    private transient boolean invalid;  // whether input path is invalid

    // static Strings used by init(int mask)
    private static final char RECURSIVE_CHAR = '-';
    private static final char WILD_CHAR = '*';

//    public String toString() {
//        StringBuilder sb = new StringBuilder();
//        sb.append("*** FilePermission on " + getName() + " ***");
//        for (Field f : FilePermission.class.getDeclaredFields()) {
//            if (!Modifier.isStatic(f.getModifiers())) {
//                try {
//                    sb.append(f.getName() + " = " + f.get(this));
//                } catch (Exception e) {
//                    sb.append(f.getName() + " = " + e.toString());
//                }
//                sb.append('\n');
//            }
//        }
//        sb.append("***\n");
//        return sb.toString();
//    }

    @java.io.Serial
    private static final long serialVersionUID = 7930732926638008763L;

    /**
     * Use the platform's default file system to avoid recursive initialization
     * issues when the VM is configured to use a custom file system provider.
     */
    private static final java.nio.file.FileSystem builtInFS =
        DefaultFileSystemProvider.theFileSystem();

    private static final Path here = builtInFS.getPath(
            GetPropertyAction.privilegedGetProperty("user.dir"));

    private static final Path EMPTY_PATH = builtInFS.getPath("");
    private static final Path DASH_PATH = builtInFS.getPath("-");
    private static final Path DOTDOT_PATH = builtInFS.getPath("..");

    /**
     * A private constructor that clones some and updates some,
     * always with a different name.
     * @param input
     */
    private FilePermission(String name,
                           FilePermission input,
                           Path npath,
                           Path npath2,
                           int mask,
                           String actions) {
        super(name);
        // Customizables
        this.npath = npath;
        this.npath2 = npath2;
        this.actions = actions;
        this.mask = mask;
        // Cloneds
        this.allFiles = input.allFiles;
        this.invalid = input.invalid;
        this.recursive = input.recursive;
        this.directory = input.directory;
        this.cpath = input.cpath;
    }

    /**
     * Returns the alternative path as a Path object, i.e. absolute path
     * for a relative one, or vice versa.
     *
     * @param in a real path w/o "-" or "*" at the end, and not <<ALL FILES>>.
     * @return the alternative path, or null if cannot find one.
     */
    private static Path altPath(Path in) {
        try {
            if (!in.isAbsolute()) {
                return here.resolve(in).normalize();
            } else {
                return here.relativize(in).normalize();
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static {
        SharedSecrets.setJavaIOFilePermissionAccess(
            /**
             * Creates FilePermission objects with special internals.
             * See {@link FilePermCompat#newPermPlusAltPath(Permission)} and
             * {@link FilePermCompat#newPermUsingAltPath(Permission)}.
             */
            new JavaIOFilePermissionAccess() {
                public FilePermission newPermPlusAltPath(FilePermission input) {
                    if (!input.invalid && input.npath2 == null && !input.allFiles) {
                        Path npath2 = altPath(input.npath);
                        if (npath2 != null) {
                            // Please note the name of the new permission is
                            // different than the original so that when one is
                            // added to a FilePermissionCollection it will not
                            // be merged with the original one.
                            return new FilePermission(input.getName() + "#plus",
                                    input,
                                    input.npath,
                                    npath2,
                                    input.mask,
                                    input.actions);
                        }
                    }
                    return input;
                }
                public FilePermission newPermUsingAltPath(FilePermission input) {
                    if (!input.invalid && !input.allFiles) {
                        Path npath2 = altPath(input.npath);
                        if (npath2 != null) {
                            // New name, see above.
                            return new FilePermission(input.getName() + "#using",
                                    input,
                                    npath2,
                                    null,
                                    input.mask,
                                    input.actions);
                        }
                    }
                    return null;
                }
            }
        );
    }

    /**
     * initialize a FilePermission object. Common to all constructors.
     * Also called during de-serialization.
     *
     * @param mask the actions mask to use.
     *
     */
    @SuppressWarnings("removal")
    private void init(int mask) {
        if ((mask & ALL) != mask)
                throw new IllegalArgumentException("invalid actions mask");

        if (mask == NONE)
                throw new IllegalArgumentException("invalid actions mask");

        if (FilePermCompat.nb) {
            String name = getName();

            if (name == null)
                throw new NullPointerException("name can't be null");

            this.mask = mask;

            if (name.equals("<<ALL FILES>>")) {
                allFiles = true;
                npath = EMPTY_PATH;
                // other fields remain default
                return;
            }

            boolean rememberStar = false;
            if (name.endsWith("*")) {
                rememberStar = true;
                recursive = false;
                name = name.substring(0, name.length()-1) + "-";
            }

            try {
                // new File() can "normalize" some name, for example, "/C:/X" on
                // Windows. Some JDK codes generate such illegal names.
                npath = builtInFS.getPath(new File(name).getPath())
                        .normalize();
                // lastName should always be non-null now
                Path lastName = npath.getFileName();
                if (lastName != null && lastName.equals(DASH_PATH)) {
                    directory = true;
                    recursive = !rememberStar;
                    npath = npath.getParent();
                }
                if (npath == null) {
                    npath = EMPTY_PATH;
                }
                invalid = false;
            } catch (InvalidPathException ipe) {
                // Still invalid. For compatibility reason, accept it
                // but make this permission useless.
                npath = builtInFS.getPath("-u-s-e-l-e-s-s-");
                invalid = true;
            }

        } else {
            if ((cpath = getName()) == null)
                throw new NullPointerException("name can't be null");

            this.mask = mask;

            if (cpath.equals("<<ALL FILES>>")) {
                allFiles = true;
                directory = true;
                recursive = true;
                cpath = "";
                return;
            }

            // Validate path by platform's default file system
            try {
                String name = cpath.endsWith("*") ? cpath.substring(0, cpath.length() - 1) + "-" : cpath;
                builtInFS.getPath(new File(name).getPath());
            } catch (InvalidPathException ipe) {
                invalid = true;
                return;
            }

            // store only the canonical cpath if possible
            cpath = AccessController.doPrivileged(new PrivilegedAction<>() {
                public String run() {
                    try {
                        String path = cpath;
                        if (cpath.endsWith("*")) {
                            // call getCanonicalPath with a path with wildcard character
                            // replaced to avoid calling it with paths that are
                            // intended to match all entries in a directory
                            path = path.substring(0, path.length() - 1) + "-";
                            path = new File(path).getCanonicalPath();
                            return path.substring(0, path.length() - 1) + "*";
                        } else {
                            return new File(path).getCanonicalPath();
                        }
                    } catch (IOException ioe) {
                        return cpath;
                    }
                }
            });

            int len = cpath.length();
            char last = ((len > 0) ? cpath.charAt(len - 1) : 0);

            if (last == RECURSIVE_CHAR &&
                    cpath.charAt(len - 2) == File.separatorChar) {
                directory = true;
                recursive = true;
                cpath = cpath.substring(0, --len);
            } else if (last == WILD_CHAR &&
                    cpath.charAt(len - 2) == File.separatorChar) {
                directory = true;
                //recursive = false;
                cpath = cpath.substring(0, --len);
            } else {
                // overkill since they are initialized to false, but
                // commented out here to remind us...
                //directory = false;
                //recursive = false;
            }

            // XXX: at this point the path should be absolute. die if it isn't?
        }
    }

    /**
     * Creates a new FilePermission object wi