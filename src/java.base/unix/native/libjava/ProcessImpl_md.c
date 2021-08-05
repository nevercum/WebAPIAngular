/*
 * Copyright (c) 1995, 2022, Oracle and/or its affiliates. All rights reserved.
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

#undef  _LARGEFILE64_SOURCE
#define _LARGEFILE64_SOURCE 1

#include "jni.h"
#include "jvm.h"
#include "jvm_md.h"
#include "jni_util.h"
#include "io_util.h"

/*
 * Platform-specific support for java.lang.Process
 */
#include <assert.h>
#include <stddef.h>
#include <stdlib.h>
#include <sys/types.h>
#include <ctype.h>
#include <sys/wait.h>
#include <signal.h>
#include <string.h>

#include <spawn.h>

#include "childproc.h"

/*
 *
 * When starting a child on Unix, we need to do three things:
 * - fork off
 * - in the child process, do some pre-exec work: duping/closing file
 *   descriptors to set up stdio-redirection, setting environment variables,
 *   changing paths...
 * - then exec(2) the target binary
 *
 * There are three ways to fork off:
 *
 * A) fork(2). Portable and safe (no side effects) but may fail with ENOMEM on
 *    all Unices when invoked from a VM with a high memory footprint. On Unices
 *    with strict no-overcommit policy this problem is most visible.
 *
 *    This is because forking the VM will first create a child process with
 *    theoretically the same memory footprint as the parent - even if you plan
 *    to follow up with exec'ing a tiny binary. In reality techniques like
 *    copy-on-write etc mitigate the problem somewhat but we still run the risk
 *    of hitting system limits.
 *
 *    For a Linux centric description of this problem, see the documentation on
 *    /proc/sys/vm/overcommit_memory in Linux proc(5).
 *
 * B) vfork(2): Portable and fast but very unsafe. It bypasses the memory
 *    problems related to fork(2) by starting the child in the memory image of
 *    the parent. Things that can go wrong include:
 *    - Programming errors in the child process before the exec(2) call may
 *      trash memory in the parent process, most commonly the stack of the
 *      thread invoking vfork.
 *    - Signals received by the child before the exec(2) call may be at best
 *      misdirected to the parent, at worst immediately kill child and parent.
 *
 *    This is mitigated by very strict rules about what one is allowed to do in
 *    the child process between vfork(2) and exec(2), which is basically nothing.
 *    However, we always broke this rule by doing the pre-exec work between
 *    vfork(2) and exec(2).
 *
 *    Also note that vfork(2) has been deprecated by the OpenGroup, presumably
 *    because of its many dangers.
 *
 * C) clone(2): This is a Linux specific call which gives the caller fine
 *    grained control about how exactly the process fork is executed. It is
 *    powerful, but Linux-specific.
 *
 * Aside from these three possibilities there is a forth option:  posix_spawn(3).
 * Where fork/vfork/clone all fork off the process and leave pre-exec work and
 * calling exec(2) to the user, posix_spawn(3) offers the user fork+exec-like
 * functionality in one package, similar to CreateProcess() on Windows.
 *
 * It is not a system call in itself, but usually a wrapper implemented within
 * the libc in terms of one of (fork|vfork|clone)+exec - so whether or not it
 * has advantages over calling the naked (fork|vfork|clone) functions depends
 * on how posix_spawn(3) is implemented.
 *
 * Note that when using posix_spawn(3), we exec twice: first a tiny binary called
 * the jspawnhelper, then in the jspawnhelper we do the pre-exec work and exec a
 * second time, this time the target binary (similar to the "exec-twice-technique"
 * described in http://mail.openjdk.org/pipermail/core-libs-dev/2018-September/055333.html).
 *
 * This is a JDK-specific implementation detail which just happens to be
 * implemented for jdk.lang.Process.launchMechanism=POSIX_SPAWN.
 *
 * --- Linux-specific ---
 *
 * How does glibc implement posix_spawn?
 * (see: sysdeps/posix/spawni.c for glibc < 2.24,
 *       sysdeps/unix/sysv/linux/spawni.c for glibc >= 2.24):
 *
 * 1) Before glibc 2.4 (released 2006), posix_spawn(3) used just fork(2)/exec(2).
 *    This would be bad for the JDK since we would risk the known memory issues with
 *    fork(2). But since this only affects glibc variants which have long been
 *    phased out by modern distributions, this is irrelevant.
 *
 * 2) Between glibc 2.4 and glibc 2.23, posix_spawn uses either fork(2) or
 *    vfork(2) depending on how exactly the user called posix_spawn(3):
 *
 * <quote>
 *       The child process is created using vfork(2) instead of fork(2) when
 *       either of the following is true:
 *
 *       * the spawn-flags element of the attributes object pointed to by
 *          attrp contains the GNU-specific flag POSIX_SPAWN_USEVFORK; or
 *
 *       * file_actions is NULL and the spawn-flags element of the attributes
 *          object pointed to by attrp does not contain
 *          POSIX_SPAWN_SETSIGMASK, POSIX_SPAWN_SETSIGDEF,
 *          POSIX_SPAWN_SETSCHEDPARAM, POSIX_SPAWN_SETSCHEDULER,
 *          POSIX_SPAWN_SETPGROUP, or POSIX_SPAWN_RESETIDS.
 * </quote>
 *
 * Due to the way the JDK calls posix_spawn(3), it would therefore call vfork(2).
 * So we would avoid the fork(2) memory problems. However, there still remains the
 * risk associated with vfork(2). But it is smaller than were we to call vfork(2)
 * directly since we use the jspawnhelper, moving all pre-exec work off to after
 * the first exec, thereby reducing the vulnerable time window.
 *
 * 3) Since glibc >= 2.24, glibc uses clone+exec:
 *
 *    new_pid = CLONE (__spawni_child, STACK (stack, stack_size), stack_size,
 *                     CLONE_VM | CLONE_VFORK | SIGCHLD, &args);
 *
 * This is even better than (2):
 *
 * CLONE_VM means we run in the parent's memory image, as with (2)
 * CLONE_VFORK means parent waits until we exec, as with (2)
 *
 * However, error possibilities are further reduced since:
 * - posix_spawn(3) passes a separate stack for the child to run on, eliminating
 *   the danger of trashing the forking thread's stack in the parent process.
 * - posix_spawn(3) takes care to temporarily block all incoming signals to the
 *   child process until the first exec(2) has been called,
 *
 * TL;DR
 * Calling posix_spawn(3) for glibc
 * (2) < 2.24 is not perfect but still better than using plain vfork(2), since
 *     the chance of an error happening is greatly reduced
 * (3) >= 2.24 is the best option - portable, fast and as safe as possible.
 *
 * ---
 *
 * How does muslc implement posix_spawn?
 *
 * They always did use the clone (.. CLONE_VM | CLONE_VFORK ...)
 * technique. So we are safe to use posix_spawn() here regardless of muslc
 * version.
 *
 * </Linux-specific>
 *
 *
 * Based on the above analysis, we are currently defaulting to posix_spawn()
 * on all Unices including Linux.
 */

static void
setSIGCHLDHandler(JNIEnv *env)
{
    /* There is a subtle difference between having the signal handler
     * for SIGCHLD be SIG_DFL and SIG_IGN.  We cannot obtain process
     * termination information for child processes if the signal
     * handler is SIG_IGN.  It must be SIG_DFL.
     *
     * We used to set the SIGCHLD handler only on Linux, but it's
     * safest to set it unconditionally.
     *
     * Consider what happens if java's parent process sets the SIGCHLD
     * handler to SIG_IGN.  Normally signal handlers are inherited by
     * children, but SIGCHLD is a controversial case.  Solaris appears
     * to always reset it to SIG_DFL, but this behavior may be
     * non-standard-compliant, and we shouldn't rely on it.
     *
     * References:
     * http://www.opengroup.org/onlinepubs/7908799/xsh/exec.html
     * http://www.pasc.org/interps/unofficial/db/p1003.1/pasc-1003.1-132.html
     */
    struct sigaction sa;
    sa.sa_handler = SIG_DFL;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_NOCLDSTOP | SA_RESTART;
    if (sigaction(SIGCHLD, &sa, NULL) < 0)
        JNU_ThrowInternalError(env, "Can't set SIGCHLD handler");
}

static void*
xmalloc(JNIEnv *env, size_t size)
{
    void *p = malloc(size);
    if (p == NULL)
        JNU_ThrowOutOfMemoryError(env, NULL);
   