/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/symbolTable.hpp"
#include "compiler/compilerDirectives.hpp"
#include "compiler/compilerOracle.hpp"
#include "compiler/methodMatcher.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.hpp"
#include "oops/method.inline.hpp"
#include "oops/symbol.hpp"
#include "opto/phasetype.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/os.hpp"

static const char* optiontype_names[] = {
#define enum_of_types(type, name) name,
        OPTION_TYPES(enum_of_types)
#undef enum_of_types
};

const char* optiontype2name(enum OptionType type) {
  return optiontype_names[static_cast<int>(type)];
}

static enum OptionType option_types[] = {
#define enum_of_options(option, name, ctype) OptionType::ctype,
        COMPILECOMMAND_OPTIONS(enum_of_options)
#undef enum_of_options
};

enum Optio