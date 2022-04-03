
/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.jgss.spnego;

import org.ietf.jgss.*;
import sun.security.jgss.*;
import sun.security.jgss.spi.*;
import sun.security.jgss.krb5.Krb5MechFactory;
import sun.security.jgss.krb5.Krb5InitCredential;
import sun.security.jgss.krb5.Krb5AcceptCredential;
import sun.security.jgss.krb5.Krb5NameElement;
import java.security.Provider;
import java.util.Vector;

/**
 * SpNego Mechanism plug in for JGSS
 * This is the properties object required by the JGSS framework.
 * All mechanism specific information is defined here.
 *
 * @author Seema Malkani
 * @since 1.6
 */

public final class SpNegoMechFactory implements MechanismFactory {

    static final Provider PROVIDER =
        new sun.security.jgss.SunProvider();

    static final Oid GSS_SPNEGO_MECH_OID =
        GSSUtil.createOid("1.3.6.1.5.5.2");

    private static final Oid[] nameTypes =
        new Oid[] { GSSName.NT_USER_NAME,
                        GSSName.NT_HOSTBASED_SERVICE,
                        GSSName.NT_EXPORT_NAME};

    // The default underlying mech of SPNEGO, must not be SPNEGO itself.
    private static final Oid DEFAULT_SPNEGO_MECH_OID =
            ProviderList.DEFAULT_MECH_OID.equals(GSS_SPNEGO_MECH_OID)?
                GSSUtil.GSS_KRB5_MECH_OID:
                ProviderList.DEFAULT_MECH_OID;

    // Use an instance of a GSSManager whose provider list
    // does not include native provider
    final GSSManagerImpl manager;
    final Oid[] availableMechs;

    private static SpNegoCredElement getCredFromSubject(GSSNameSpi name,
                                                        boolean initiate)
        throws GSSException {
        Vector<SpNegoCredElement> creds =