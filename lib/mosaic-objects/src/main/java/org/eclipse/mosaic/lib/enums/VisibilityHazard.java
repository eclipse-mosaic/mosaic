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
 * Defines a collection of reasons for reduced visibility which could
 * be potentially hazardous for a vehicle.
 */
public enum VisibilityHazard {

    /**
     * Reduced visibility due to rain.
     */
    RAIN,
    /**
     * Reduced visibility due to snow.
     */
    SNOW,
    /**
     * Reduced visibility due to hail.
     */
    HAIL,
    /**
     * Reduced visibility due to fog.
     */
    FOG,
    /**
     * Reduced visibility due to dust in the air.
     */
    DUST,
    /**
     * Reduced visibility due to glare from sunlight or artificial light sources.
     */
    GLARE;

    public static @Nullable VisibilityHazard fromObject(@Nullable Object o) {
        if (o == null) {
            return null;
        } else if (o instanceof VisibilityHazard hazard) {
            return hazard;
        } else if (o instanceof String string) {
            return VisibilityHazard.valueOf(StringUtils.upperCase(string));
        }
        throw new IllegalArgumentException("Could not translate object of type " + o.getClass() + " to VisibilityHazard.");
    }


}
