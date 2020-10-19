
/*
 * Copyright (c) 2002, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2020 SAP SE. All rights reserved.
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

#ifndef CPU_PPC_ASSEMBLER_PPC_INLINE_HPP
#define CPU_PPC_ASSEMBLER_PPC_INLINE_HPP

#include "asm/assembler.inline.hpp"
#include "asm/codeBuffer.hpp"
#include "code/codeCache.hpp"
#include "runtime/vm_version.hpp"

inline void Assembler::emit_int32(int x) {
  AbstractAssembler::emit_int32(x);
}

inline void Assembler::emit_data(int x) {
  emit_int32(x);
}

inline void Assembler::emit_data(int x, relocInfo::relocType rtype) {
  relocate(rtype);
  emit_int32(x);
}

inline void Assembler::emit_data(int x, RelocationHolder const& rspec) {
  relocate(rspec);
  emit_int32(x);
}

// Emit an address
inline address Assembler::emit_addr(const address addr) {
  address start = pc();
  emit_address(addr);
  return start;
}

#if !defined(ABI_ELFv2)
// Emit a function descriptor with the specified entry point, TOC, and
// ENV. If the entry point is NULL, the descriptor will point just
// past the descriptor.
inline address Assembler::emit_fd(address entry, address toc, address env) {
  FunctionDescriptor* fd = (FunctionDescriptor*)pc();

  assert(sizeof(FunctionDescriptor) == 3*sizeof(address), "function descriptor size");

  (void)emit_addr();
  (void)emit_addr();
  (void)emit_addr();

  fd->set_entry(entry == NULL ? pc() : entry);
  fd->set_toc(toc);
  fd->set_env(env);

  return (address)fd;
}
#endif

// Issue an illegal instruction. 0 is guaranteed to be an illegal instruction.
inline void Assembler::illtrap() { Assembler::emit_int32(0); }
inline bool Assembler::is_illtrap(address instr_addr) { return *(uint32_t*)instr_addr == 0u; }