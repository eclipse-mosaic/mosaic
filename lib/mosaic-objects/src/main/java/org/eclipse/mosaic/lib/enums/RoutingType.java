/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
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

/**
 * Enumeration of destination types.
 */
public enum RoutingType {
    // ad hoc types
    AD_HOC_GEOCAST,
    AD_HOC_TOPOCAST,
    // cell types
    CELL_GEOCAST,
    CELL_GEOCAST_MBMS,
    CELL_TOPOCAST;

    public boolean isAdHoc() {
        return this == AD_HOC_GEOCAST || this == AD_HOC_TOPOCAST;
    }

    public boolean isCell() {
        return this == CELL_GEOCAST || this == CELL_GEOCAST_MBMS || this == CELL_TOPOCAST;
    }

    public boolean isGeocast() {
        return this == CELL_GEOCAST || this == CELL_GEOCAST_MBMS || this == AD_HOC_GEOCAST;
    }

    public boolean isTopocast() {
        return this == AD_HOC_TOPOCAST || this == CELL_TOPOCAST;
    }

}
