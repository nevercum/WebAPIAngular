
/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.x509;

import java.io.IOException;
import sun.security.util.DerValue;
import sun.security.util.DerOutputStream;

/**
 * This class defines the X400Address of the GeneralName choice.
 * <p>
 * The ASN.1 syntax for this is:
 * <pre>
 * ORAddress ::= SEQUENCE {
 *    built-in-standard-attributes BuiltInStandardAttributes,
 *    built-in-domain-defined-attributes
 *                         BuiltInDomainDefinedAttributes OPTIONAL,
 *    -- see also teletex-domain-defined-attributes
 *    extension-attributes ExtensionAttributes OPTIONAL }
 * --      The OR-address is semantically absent from the OR-name if the
 * --      built-in-standard-attribute sequence is empty and the
 * --      built-in-domain-defined-attributes and extension-attributes are
 * --      both omitted.
 *
 * --      Built-in Standard Attributes
 *
 * BuiltInStandardAttributes ::= SEQUENCE {
 *    country-name CountryName OPTIONAL,
 *    administration-domain-name AdministrationDomainName OPTIONAL,
 *    network-address      [0] NetworkAddress OPTIONAL,
 *    -- see also extended-network-address
 *    terminal-identifier  [1] TerminalIdentifier OPTIONAL,
 *    private-domain-name  [2] PrivateDomainName OPTIONAL,
 *    organization-name    [3] OrganizationName OPTIONAL,
 *    -- see also teletex-organization-name
 *    numeric-user-identifier      [4] NumericUserIdentifier OPTIONAL,
 *    personal-name        [5] PersonalName OPTIONAL,
 *    -- see also teletex-personal-name
 *    organizational-unit-names    [6] OrganizationalUnitNames OPTIONAL
 *    -- see also teletex-organizational-unit-names -- }
 *
 * CountryName ::= [APPLICATION 1] CHOICE {
 *    x121-dcc-code NumericString
 *                 (SIZE (ub-country-name-numeric-length)),
 *    iso-3166-alpha2-code PrintableString
 *                 (SIZE (ub-country-name-alpha-length)) }
 *
 * AdministrationDomainName ::= [APPLICATION 2] CHOICE {
 *    numeric NumericString (SIZE (0..ub-domain-name-length)),
 *    printable PrintableString (SIZE (0..ub-domain-name-length)) }
 *
 * NetworkAddress ::= X121Address  -- see also extended-network-address
 *
 * X121Address ::= NumericString (SIZE (1..ub-x121-address-length))
 *
 * TerminalIdentifier ::= PrintableString (SIZE (1..ub-terminal-id-length))
 *
 * PrivateDomainName ::= CHOICE {
 *    numeric NumericString (SIZE (1..ub-domain-name-length)),
 *    printable PrintableString (SIZE (1..ub-domain-name-length)) }
 *
 * OrganizationName ::= PrintableString
 *                             (SIZE (1..ub-organization-name-length))
 * -- see also teletex-organization-name
 *
 * NumericUserIdentifier ::= NumericString
 *                             (SIZE (1..ub-numeric-user-id-length))
 *