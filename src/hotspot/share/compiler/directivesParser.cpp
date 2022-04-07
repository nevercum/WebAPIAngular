/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compileBroker.hpp"
#include "compiler/directivesParser.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "opto/phasetype.hpp"
#include "runtime/os.hpp"
#include <string.h>

void DirectivesParser::push_tmp(CompilerDirectives* dir) {
  _tmp_depth++;
  dir->set_next(_tmp_top);
  _tmp_top = dir;
}

CompilerDirectives* DirectivesParser::pop_tmp() {
  if (_tmp_top == nullptr) {
    return nullptr;
  }
  CompilerDirectives* tmp = _tmp_top;
  _tmp_top = _tmp_top->next();
  tmp->set_next(nullptr);
  _tmp_depth--;
  return tmp;
}

void DirectivesParser::clean_tmp() {
  CompilerDirectives* tmp = pop_tmp();
  while (tmp != nullptr) {
    delete tmp;
    tmp = pop_tmp();
  }
  assert(_tmp_depth == 0, "Consistency");
}

int DirectivesParser::parse_string(const char* text, outputStream* st) {
  DirectivesParser cd(text, st, false);
  if (cd.valid()) {
    return cd.install_directives();
  } else {
    cd.clean_tmp();
    st->flush();
    st->print_cr("Parsing of compiler directives failed");
    return -1;
  }
}

bool DirectivesParser::has_file() {
  return CompilerDirectivesFile != nullptr;
}

bool DirectivesParser::parse_from_flag() {
  return parse_from_file(CompilerDirectivesFile, tty);
}

bool DirectivesParser::parse_from_file(const char* filename, outputStream* st) {
  assert(filename != nullptr, "Test before calling this");
  if (!parse_from_file_inner(filename, st)) {
    st->print_cr("Could not load file: %s", filename);
    return false;
  }
  return true;
}

bool DirectivesParser::parse_from_file_inner(const char* filename, outputStream* stream) {
  struct stat st;
  ResourceMark rm;
  if (os::stat(filename, &st) == 0) {
    // found file, open it
    int file_handle = os::open(filename, 0, 0);
    if (file_handle != -1) {
      // read contents into resource array
      char* buffer = NEW_RESOURCE_ARRAY(char, st.st_size+1);
      ssize_t num_read = ::read(file_handle, (char*) buffer, st.st_size);
      ::close(file_handle);
      if (num_read >= 0) {
  