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
bool CompilerOracle::has_option_value(const methodHandle& method, enum CompileCommand option, T& value) {
  assert(option_matches_type(option, value), "Value must match option type");
  if (!has_command(option)) {
    return false;
  }
  if (option_list != nullptr) {
    TypedMethodOptionMatcher* m = option_list->match(method, option);
    if (m != nullptr) {
      value = m->value<T>();
      return true;
    }
  }
  return false;
}

static bool resolve_inlining_predicate(enum CompileCommand option, const methodHandle& method) {
  assert(option == CompileCommand::Inline || option == CompileCommand::DontInline, "Sanity");
  bool v1 = false;
  bool v2 = false;
  bool has_inline = CompilerOracle::has_option_value(method, CompileCommand::Inline, v1);
  bool has_dnotinline = CompilerOracle::has_option_value(method, CompileCommand::DontInline, v2);
  if (has_inline && has_dnotinline) {
    if (v1 && v2) {
      // Conflict options detected
      // Find the last one for that method and return the predicate accordingly
      // option_list lists options in reverse order. So the first option we find is the last which was specified.
      enum CompileCommand last_one = CompileCommand::Unknown;
      TypedMethodOptionMatcher* current = option_list;
      while (current != nullptr) {
        last_one = current->option();
        if (last_one == CompileCommand::Inline || last_one == CompileCommand::DontInline) {
          if (current->matches(method)) {
            return last_one == option;
          }
        }
        current = current->next();
      }
      ShouldNotReachHere();
      return false;
    } else {
      // No conflicts
      return option == CompileCommand::Inline ? v1 : v2;
    }
  } else {
    if (option == CompileCommand::Inline) {
      return has_inline ? v1 : false;
    } else {
      return has_dnotinline ? v2 : false;
    }
  }
}

static bool check_predicate(enum CompileCommand option, const methodHandle& method) {
  // Special handling for Inline and DontInline since conflict options may be specified
  if (option == CompileCommand::Inline || option == CompileCommand::DontInline) {
    return resolve_inlining_predicate(option, method);
  }

  bool value = false;
  if (CompilerOracle::has_option_value(method, option, value)) {
    return value;
  }
  return false;
}

bool CompilerOracle::has_any_command_set() {
  return any_set;
}

// Explicit instantiation for all OptionTypes supported.
template bool CompilerOracle::has_option_value<intx>(const methodHandle& method, enum CompileCommand option, intx& value);
template bool CompilerOracle::has_option_value<uintx>(const methodHandle& method, enum CompileCommand option, uintx& value);
template bool CompilerOracle::has_option_value<bool>(const methodHandle& method, enum CompileCommand option, bool& value);
template bool CompilerOracle::has_option_value<ccstr>(const methodHandle& method, enum CompileCommand option, ccstr& value);
template bool CompilerOracle::has_option_value<double>(const methodHandle& method, enum CompileCommand option, double& value);

template<typename T>
bool CompilerOracle::option_matches_type(enum CompileCommand option, T& value) {
  enum OptionType option_type = option2type(option);
  if (option_type == OptionType::Unknown) {
    return false; // Can't query options with type Unknown.
  }
  if (option_type == OptionType::Ccstrlist) {
    option_type = OptionType::Ccstr; // CCstrList type options are stored as Ccstr
  }
  return (get_type_for<T>() == option_type);
}

template bool CompilerOracle::option_matches_type<intx>(enum CompileCommand option, intx& value);
template bool CompilerOracle::option_matches_type<uintx>(enum CompileCommand option, uintx& value);
template bool CompilerOracle::option_matches_type<bool>(enum CompileCommand option, bool& value);
template bool CompilerOracle::option_matches_type<ccstr>(enum CompileCommand option, ccstr& value);
template bool CompilerOracle::option_matches_type<double>(enum CompileCommand option, double& value);

bool CompilerOracle::has_option(const methodHandle& method, enum CompileCommand option) {
  bool value = false;
  has_option_value(method, option, value);
  return value;
}

bool CompilerOracle::should_exclude(const methodHandle& method) {
  if (check_predicate(CompileCommand::Exclude, method)) {
    return true;
  }
  if (has_command(CompileCommand::CompileOnly)) {
    return !check_predicate(CompileCommand::CompileOnly, method);
  }
  return false;
}

bool CompilerOracle::should_inline(const methodHandle& method) {
  return (check_predicate(CompileCommand::Inline, method));
}

bool CompilerOracle::should_not_inline(const methodHandle& method) {
  return check_predicate(CompileCommand::DontInline, method) || check_predicate(CompileCommand::Exclude, method);
}

bool CompilerOracle::should_print(const methodHandle& method) {
  return check_predicate(CompileCommand::Print, method);
}

bool CompilerOracle::should_print_methods() {
  return has_command(CompileCommand::Print);
}

bool CompilerOracle::should_log(const methodHandle& method) {
  if (!LogCompilation) return false;
  if (!has_command(CompileCommand::Log)) {
    return true;  // by default, log all
  }
  return (check_predicate(CompileCommand::Log, method));
}

bool CompilerOracle::should_break_at(const methodHandle& method) {
  return check_predicate(CompileCommand::Break, method);
}

void CompilerOracle::tag_blackhole_if_possible(const methodHandle& method) {
  if (!check_predicate(CompileCommand::Blackhole, method)) {
    return;
  }
  guarantee(UnlockExperimentalVMOptions, "Checked during initial parsing");
  if (method->result_type() != T_VOID) {
    warning("Blackhole compile option only works for methods with void type: %s",
            method->name_and_sig_as_C_string());
    return;
  }
  if (!method->is_empty_method()) {
    warning("Blackhole compile option only works for empty methods: %s",
            method->name_and_sig_as_C_string());
    return;
  }
  if (!method->is_static()) {
    warning("Blackhole compile option only works for static methods: %s",
            method->name_and_sig_as_C_string());
    return;
  }
  if (method->intrinsic_id() == vmIntrinsics::_blackhole) {
    return;
  }
  if (method->intrinsic_id() != vmIntrinsics::_none) {
    warning("Blackhole compile option only works for methods that do not have intrinsic set: %s, %s",
            method->name_and_sig_as_C_string(), vmIntrinsics::name_at(method->intrinsic_id()));
    return;
  }
  method->set_intrinsic_id(vmIntrinsics::_blackhole);
}

static enum CompileCommand match_option_name(const char* line, int* bytes_read, char* errorbuf, int bufsize) {
  assert(ARRAY_SIZE(option_names) == static_cast<int>(CompileCommand::Count), "option_names size mismatch");

  *bytes_read = 0;
  char option_buf[256];
  int matches = sscanf(line, "%255[a-zA-Z0-9]%n", option_buf, bytes_read);
  if (matches > 0 && strcasecmp(option_buf, "unknown") != 0) {
    for (uint i = 0; i < ARRAY_SIZE(option_names); i++) {
      if (strcasecmp(option_buf, option_names[i]) == 0) {
        return static_cast<enum CompileCommand>(i);
      }
    }
  }
  jio_snprintf(errorbuf, bufsize, "Unrecognized option '%s'", option_buf);
  return CompileCommand::Unknown;
}

// match exactly and don't mess with errorbuf
enum CompileCommand CompilerOracle::parse_option_name(const char* line) {
  for (uint i = 0; i < ARRAY_SIZE(option_names); i++) {
    if (strcasecmp(line, option_names[i]) == 0) {
      return static_cast<enum CompileCommand>(i);
    }
  }
  return CompileCommand::Unknown;
}

enum OptionType CompilerOracle::parse_option_type(const char* type_str) {
  for (uint i = 0; i < ARRAY_SIZE(optiontype_names); i++) {
    if (strcasecmp(type_str, optiontype_names[i]) == 0) {
      return static_ca