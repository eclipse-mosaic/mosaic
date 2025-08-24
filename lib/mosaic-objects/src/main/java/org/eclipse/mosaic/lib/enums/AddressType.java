/*
 * Copyright (c) 2025 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.lib.enums;

import org.eclipse.mosaic.lib.objects.addressing.NetworkAddress;

/**
 * Enumeration of destination types.
 */
public enum AddressType {
    IPV4_UNICAST,
    IPV4_BROADCAST,
    IPV4_ANYCAST;

    public static AddressType getEnum(NetworkAddress address) {
        if (address == null) throw new IllegalArgumentException("Address is null");
        if (address.isUnicast()) return IPV4_UNICAST;
        if (address.isBroadcast()) return IPV4_BROADCAST;
        if (address.isAnycast()) return IPV4_ANYCAST;
        throw new IllegalArgumentException("Unknown type for address: " + address);
    }
}
