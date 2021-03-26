/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SERVICES_ATTACHLISTENER_HPP
#define SHARE_SERVICES_ATTACHLISTENER_HPP

#include "memory/allStatic.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

// The AttachListener thread services a queue of operations that are enqueued
// by client tools. Each operation is identified by a name and has up to 3
// arguments. The operation name is mapped to a function which performs the
// operation. The function is called with an outputStream which is can use to
// write any result data (for examples the properties command serializes
// properties names and values to the output stream). When the function
// complets the result value and any result data is returned to the client
// tool.

class AttachOperation;

typedef jint (*AttachOperationFunction)(AttachOperation* op, outputStream* out);

struct AttachOperationFunctionInfo {
  const char* name;
  AttachOperationFunction func;
};

enum AttachListenerState {
  AL_NOT_INITIALIZED,
  AL_INITIALIZING,
  AL_INITIALIZED
};

class AttachListener: AllStatic {
 public:
  static void vm_start() NOT_SERVICES_RETURN;
  static void init()  NOT_SERVICES_RETURN;
  static void abort() NOT_SERVICES_RETURN;

  // invoke to perform clean-up tasks when all clients detach
  static void detachall() NOT_SERVICES_RETURN;

  // check unix domain socket file on filesystem
  static bool check_socket_file() NOT_SERVICES_RETURN_(false);

  // indicates if the 