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
        buffer[num_read] = '\0';
        return parse_string(buffer, stream) > 0;
      }
    }
  }
  return false;
}

int DirectivesParser::install_directives() {
  // Check limit
  if (!DirectivesStack::check_capacity(_tmp_depth, _st)) {
    clean_tmp();
    return 0;
  }

  // Pop from internal temporary stack and push to compileBroker.
  CompilerDirectives* tmp = pop_tmp();
  int i = 0;
  while (tmp != nullptr) {
    i++;
    DirectivesStack::push(tmp);
    tmp = pop_tmp();
  }
  if (i == 0) {
    _st->print_cr("No directives in file");
    return 0;
  } else {
    _st->print_cr("%i compiler directives added", i);
    if (CompilerDirectivesPrint) {
      // Print entire directives stack after new has been pushed.
      DirectivesStack::print(_st);
    }
    return i;
  }
}

DirectivesParser::DirectivesParser(const char* text, outputStream* st, bool silent)
: JSON(text, silent, st), depth(0), current_directive(nullptr), current_directiveset(nullptr), _tmp_top(nullptr), _tmp_depth(0) {
#ifndef PRODUCT
  memset(stack, 0, MAX_DEPTH * sizeof(stack[0]));
#endif
  parse();
}

DirectivesParser::~DirectivesParser() {
  assert(_tmp_top == nullptr, "Consistency");
  assert(_tmp_depth == 0, "Consistency");
}

const DirectivesParser::key DirectivesParser::keys[] = {
    // name, keytype, allow_array, allowed_mask, set_function
    { "c1",     type_c1,     0, mask(type_directives), nullptr, UnknownFlagType },
    { "c2",     type_c2,     0, mask(type_directives), nullptr, UnknownFlagType },
    { "match",  type_match,  1, mask(type_directives), nullptr, UnknownFlagType },
    { "inline", type_inline, 1, mask(type_directives) | mask(type_c1) | mask(type_c2), nullptr, UnknownFlagType },

    // Global flags
    #define common_flag_key(name, type, dvalue, compiler) \
    { #name, type_flag, 0, mask(type_directives) | mask(type_c1) | mask(type_c2), &DirectiveSet::set_##name, type##Flag},
    compilerdirectives_common_flags(common_flag_key)
    compilerdirectives_c2_flags(common_flag_key)
    compilerdirectives_c1_flags(common_flag_key)
    #undef common_flag_key
};

const DirectivesParser::key DirectivesParser::dir_array_key = {
     "top level directives array", type_dir_array, 0, 1 // Lowest bit means allow at top level
};
const DirectivesParser::key DirectivesParser::dir_key = {
   "top level directive", type_directives, 0, mask(type_dir_array) | 1 // Lowest bit means allow at top level
};
const DirectivesParser::key DirectivesParser::value_array_key = {
   "value array", type_value_array, 0, UINT_MAX // Allow all, checked by allow_array on other keys, not by allowed_mask from this key
};

const DirectivesParser::key* DirectivesParser::lookup_key(const char* str, size_t len) {
  for (size_t i = 0; i < (sizeof(keys) / sizeof(keys[0])); i++) {
    if (strncasecmp(keys[i].name, str, len) == 0) {
      return &keys[i];
    }
  }
  return nullptr;
}

uint DirectivesParser::mask(keytype kt) {
  return 1 << (kt + 1);
}

bool DirectivesParser::push_key(const char* str, size_t len) {
  bool result = true;
  const key* k = lookup_key(str, len);

  if (k == nullptr) {
    // os::strdup
    char* s = NEW_C_HEAP_ARRAY(char, len + 1, mtCompiler);
    strncpy(s, str, len);
    s[len] = '\0';
    error(KEY_ERROR, "No such key: '%s'.", s);
    FREE_C_HEAP_ARRAY(char, s);
    return false;
  }

  return push_key(k);
}

bool DirectivesParser::push_key(const key* k) {
  assert(k->allowedmask != 0, "not allowed anywhere?");

  // Exceeding the stack should not be possible with a valid compiler directive,
  // and an invalid should abort before this happens
  assert(depth < MAX_DEPTH, "exceeded stack depth");
  if (depth >= MAX_DEPTH) {
    error(INTERNAL_ERROR, "Stack depth exceeded.");
    return false;
  }

  assert(stack[depth] == nullptr, "element not nulled, something is wrong");

  if (depth == 0 && !(k->allowedmask & 1)) {
    error(KEY_ERROR, "Key '%s' not allowed at top level.", k->name);
    return false;
  }

  if (depth > 0) {
    const key* prev = stack[depth - 1];
    if (!(k->allowedmask & mask(prev->type))) {
      error(KEY_ERROR, "Key '%s' not allowed after '%s' key.", k->name, prev->name);
      return false;
    }
  }

  stack[depth] = k;
  depth++;
  return true;
}

const DirectivesParser::key* DirectivesParser::current_key() {
  assert(depth > 0, "getting key from empty stack");
  if (depth == 0) {
    return nullptr;
  }
  return stack[depth - 1];
}

const DirectivesParser::key* DirectivesParser::pop_key() {
  assert(depth > 0, "popping empty stack");
  if (depth == 0) {
    error(INTERNAL_ERROR, "Popping empty stack.");
    return nullptr;
  }
  depth--;

  const key* k = stack[depth];
#ifndef PRODUCT
  stack[depth] = nullptr;
#endif

  return k;
}

bool DirectivesParser::set_option_flag(JSON_TYPE t, JSON_VAL* v, const key* option_key, DirectiveSet* set) {

  void (DirectiveSet::*test)(void *args);
  test = option_key->set;

  switch (t) {
    case JSON_TRUE:
      if (option_key->flag_type != boolFlag) {
        error(VALUE_ERROR, "Cannot use bool value for an %s flag", flag_type_names[option_key->flag_type]);
        return false;
      } else {
        bool val = true;
        (set->*test)((void *)&val);
      }
      break;

    case JSON_FALSE:
      if (option_key->flag_type != boolFlag) {
        error(VALUE_ERROR, "Cannot use bool value for an %s flag", flag_type_names[option_key->flag_type]);
        return false;
      } else {
        bool val = false;
        (set->*test)((void *)&val);
      }
      break;

    case JSON_NUMBER_INT:
      if (option_key->flag_type == intxFlag) {
        intx ival = v->int_value;
        (set->*test)((void *)&ival);
      } else if (option_key->flag_type == uintxFlag) {
        uintx ival = v->uint_value;
        (set->*test)((void *)&ival);
      } else if (option_key->flag_type == doubleFlag) {
        double dval = (double)v->int_value;
        (set->*test)((void *)&dval);
      } else {
        error(VALUE_ERROR, "Cannot use int value for an %s flag", flag_type_names[option_key->flag_type]);
        return false;
      }
      break;

    case JSON_NUMBER_FLOAT:
      if (option_key->flag_type != doubleFlag) {
        error(VALUE_ERROR, "Cannot use double value for an %s flag", flag_type_names[option_key->flag_type]);
        return false;
      } else {
        double dval = v->double_value;
        (set->*test)((void *)&dval);
      }
      break;

    case JSON_STRING:
      if (option_key->flag_type != ccstrFlag && option_key->flag_type != ccstrlistFlag) {
        error(VALUE_ERROR, "Cannot use string value for a %s flag", flag_type_names[option_key->flag_type]);
        return false;
      } else {
        char* s = NEW_C_HEAP_ARRAY(char, v->str.length+1,  mtCompiler);
        strncpy(s, v->str.start, v->str.length + 1);
        s[v->str.length] = '\0';
        (set->*test)((void *)&s);

        if (strncmp(option_key->name, "ControlIntrinsic", 16) == 0) {
          ControlIntrinsicValidator validator(s, false/*disabled_all*/);

          if (!validator.is_valid()) {
            error(VALUE_ERROR, "Unrecognized intrinsic detected in ControlIntrinsic: %s", validator.what());
            return false;
          }
        } else if (strncmp(option_key->name, "DisableIntrinsic", 16) == 0) {
          ControlIntrinsicValidator validator(s, true/*disabled_all*/);

          if (!validator.is_valid()) {
            error(VALUE_ERROR, "Unrecognized intrinsic detected in DisableIntrinsic: %s", validator.what());
            return false;
          }
        } else if (strncmp(option_key->name, "PrintIdealPhase", 15) == 0) {
          uint6