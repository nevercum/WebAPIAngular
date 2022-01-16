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

enum OptionType option2type(enum CompileCommand option) {
  return option_types[static_cast<int>(option)];
}

static const char* option_names[] = {
#define enum_of_options(option, name, ctype) name,
        COMPILECOMMAND_OPTIONS(enum_of_options)
#undef enum_of_options
};

const char* option2name(enum CompileCommand option) {
  return option_names[static_cast<int>(option)];
}

/* Methods to map real type names to OptionType */
template<typename T>
static OptionType get_type_for() {
  return OptionType::Unknown;
};

template<> OptionType get_type_for<intx>() {
  return OptionType::Intx;
}

template<> OptionType get_type_for<uintx>() {
  return OptionType::Uintx;
}

template<> OptionType get_type_for<bool>() {
  return OptionType::Bool;
}

template<> OptionType get_type_for<ccstr>() {
  return OptionType::Ccstr;
}

template<> OptionType get_type_for<double>() {
  return OptionType::Double;
}

class MethodMatcher;
class TypedMethodOptionMatcher;

static TypedMethodOptionMatcher* option_list = nullptr;
static bool any_set = false;

// A filter for quick lookup if an option is set
static bool option_filter[static_cast<int>(CompileCommand::Unknown) + 1] = { 0 };

void command_set_in_filter(enum CompileCommand option) {
  assert(option != CompileCommand::Unknown, "sanity");
  assert(option2type(option) != OptionType::Unknown, "sanity");

  if ((option != CompileCommand::DontInline) &&
      (option != CompileCommand::Inline) &&
      (option != CompileCommand::Log)) {
    any_set = true;
  }
  option_filter[static_cast<int>(option)] = true;
}

bool has_command(enum CompileCommand option) {
  return option_filter[static_cast<int>(option)];
}

class TypedMethodOptionMatcher : public MethodMatcher {
 private:
  TypedMethodOptionMatcher* _next;
  enum CompileCommand _option;
 public:

  union {
    bool bool_value;
    intx intx_value;
    uintx uintx_value;
    double double_value;
    ccstr ccstr_value;
  } _u;

  TypedMethodOptionMatcher() : MethodMatcher(),
    _next(nullptr),
    _option(CompileCommand::Unknown) {
      memset(&_u, 0, sizeof(_u));
  }

  ~TypedMethodOptionMatcher();
  static TypedMethodOptionMatcher* parse_method_pattern(char*& line, char* errorbuf, const int buf_size);
  TypedMethodOptionMatcher* match(const methodHandle &method, enum CompileCommand option);

  void init(enum CompileCommand option, TypedMethodOptionMatcher* next) {
    _next = next;
    _option = option;
  }

  void init_matcher(Symbol* class_name, Mode class_mode,
                    Symbol* method_name, Mode method_mode,
                    Symbol* signature) {
    MethodMatcher::init(class_name, class_mode, method_name, method_mode, signature);
  }

  void set_next(TypedMethodOptionMatcher* next) {_next = next; }
  TypedMethodOptionMatcher* next() { return _next; }
  enum CompileCommand option() { return _option; }
  template<typename T> T value();
  template<typename T> void set_value(T value);
  void print();
  void print_all();
  TypedMethodOptionMatcher* clone();
};

// A few templated accessors instead of a full template class.
template<> intx TypedMethodOptionMatcher::value<intx>() {
  return _u.intx_value;
}

template<> uintx TypedMethodOptionMatcher::value<uintx>() {
  return _u.uintx_value;
}

template<> bool TypedMethodOptionMatcher::value<bool>() {
  return _u.bool_value;
}

template<> double TypedMethodOptionMatcher::value<double>() {
  return _u.double_value;
}

template<> ccstr TypedMethodOptionMatcher::value<ccstr>() {
  return _u.ccstr_value;
}

template<> void TypedMethodOptionMatcher::set_value(intx value) {
  _u.intx_value = value;
}

template<> void TypedMethodOptionMatcher::set_value(uintx value) {
  _u.uintx_value = value;
}

template<> void TypedMethodOptionMatcher::set_value(double value) {
  _u.double_value = value;
}

template<> void TypedMethodOptionMatcher::set_value(bool value) {
  _u.bool_value = value;
}

template<> void TypedMethodOptionMatcher::set_value(ccstr value) {
  _u.ccstr_value = (const ccstr)os::strdup_check_oom(value);
}

void TypedMethodOptionMatcher::print() {
  ttyLocker ttyl;
  print_base(tty);
  const char* name = option2name(_option);
  enum OptionType type = option2type(_option);
  switch (type) {
    case OptionType::Intx:
    tty->print_cr(" intx %s = " INTX_FORMAT, name, value<intx>());
    break;
    case OptionType::Uintx:
    tty->print_cr(" uintx %s = " UINTX_FORMAT, name, value<uintx>());
    break;
    case OptionType::Bool:
    tty->print_cr(" bool %s = %s", name, value<bool>() ? "true" : "false");
    break;
    case OptionType::Double:
    tty->print_cr(" double %s = %f", name, value<double>());
    break;
    case OptionType::Ccstr:
    case OptionType::Ccstrlist:
    tty->print_cr(" const char* %s = '%s'", name, value<ccstr>());
    break;
  default:
    ShouldNotReachHere();
  }
}

void TypedMethodOptionMatcher::print_all() {
   print();
   if (_next != nullptr) {
     tty->print(" ");
     _next->print_all();
   }
 }

TypedMethodOptionMatcher* TypedMethodOptionMatcher::clone() {
  TypedMethodOptionMatcher* m = new TypedMethodOptionMatcher();
  m->_class_mode = _class_mode;
  m->_class_name = _class_name;
  m->_method_mode = _method_mode;
  m->_method_name = _method_name;
  m->_signature = _signature;
  // Need to ref count the symbols
  if (_class_name != nullptr) {
    _class_name->increment_refcount();
  }
  if (_method_name != nullptr) {
    _method_name->increment_refcount();
  }
  if (_signature != nullptr) {
    _signature->increment_refcount();
  }
  return m;
}

TypedMethodOptionMatcher::~TypedMethodOptionMatcher() {
  enum OptionType type = option2type(_option);
  if (type == OptionType::Ccstr || type == OptionType::Ccstrlist) {
    ccstr v = value<ccstr>();
    os::free((void*)v);
  }
}

TypedMethodOptionMatcher* TypedMethodOptionMatcher::parse_method_pattern(char*& line, char* errorbuf, const int buf_size) {
  assert(*errorbuf == '\0', "Dont call here with error_msg already set");
  const char* error_msg = nullptr;
  TypedMethodOptionMatcher* tom = new TypedMethodOptionMatcher();
  MethodMatcher::parse_method_pattern(line, error_msg, tom);
  if (error_msg != nullptr) {
    jio_snprintf(errorbuf, buf_size, error_msg);
    delete tom;
    return nullptr;
  }
  return tom;
}

TypedMethodOptionMatcher* TypedMethodOptionMatcher::match(const methodHandle& method, enum CompileCommand option) {
  TypedMethodOptionMatcher* current = this;
  while (current != nullptr) {
    if (current->_option == option) {
      if (current->matches(method)) {
        return current;
      }
    }
    current = current->next();
  }
  return nullptr;
}

template<typename T>
static void register_command(TypedMethodOptionMatcher* matcher,
                             enum CompileCommand option,
                             T value) {
  assert(matcher != option_list, "No circular lists please");
  if (option == CompileCommand::Log && !LogCompilation) {
    tty->print_cr("Warning:  +LogCompilation must be enabled in order for individual methods to be logged with ");
    tty->print_cr("          CompileCommand=log,<method pattern>");
  }
  assert(CompilerOracle::option_matches_type(option, value), "Value must match option type");

  if (option == CompileCommand::Blackhole && !UnlockExperimentalVMOptions) {
    warning("Blackhole compile option is experimental and must be enabled via -XX:+UnlockExperimentalVMOptions");
    return;
  }

  matcher->init(option, option_list);
  matcher->set_value<T>(value);
  option_list = matcher;
  command_set_in_filter(option);

  if (!CompilerOracle::be_quiet()) {
    // Print out the successful registration of a compile command
    ttyLocker ttyl;
    tty->print("CompileCommand: %s ", option2name(option));
    matcher->print();
  }
  return;
}

template<typename T>
bo