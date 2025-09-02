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

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

/**
 * Defines a collection of reasons for reduced fraction which could
 * be potentially hazardous for a vehicle.
 */
public enum TractionHazard {

    /**
     * Reduced traction due to water on the road surface.
     */
    AQUA_PLANNING,
    /**
     * Reduced traction due to snow on the road surface.
     */
    SNOW,
    /**
     * Reduced traction due to ice on the road surface.
     */
    ICE,
    /**
     * Reduced traction due to wet leaves on the road surface.
     */
    WET_LEAVES,
    /**
     * Reduced traction due to mud on the road surface.
     */
    MUD,
    /**
     * Reduced traction due to sand or loose particles on the road surface.
     */
    SAND;

    public static @Nullable TractionHazard fromObject(@Nullable Object o) {
        if (o == null) {
            return null;
        } else if (o instanceof TractionHazard hazard) {
            return hazard;
        } else if (o instanceof String string) {
            return TractionHazard.valueOf(StringUtils.upperCase(string));
        }
        throw new IllegalArgumentException("Could not translate object of type " + o.getClass() + " to TractionHazard.");
    }
}
