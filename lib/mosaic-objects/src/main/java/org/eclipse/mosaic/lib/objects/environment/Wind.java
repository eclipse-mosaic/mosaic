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

package org.eclipse.mosaic.lib.objects.environment;

import org.eclipse.mosaic.lib.util.ConversionUtils;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Map;

/**
 *
 * @param speed
 * @param direction
 */
public record Wind(double speed, double direction) {

    /**
     * Converts the input object to a {@link Wind} object, if applicable.
     * Currently only supports the input object ot be already of {@link Wind},
     * or a {@link Map} with set key-value pairs for "speed" and "direction".
     *
     * @throws IllegalArgumentException if input object could not be converted.
     */
    static Wind fromObject(Object o) {
        if (o == null) {
            return null;
        } else if (o instanceof Wind wind) {
            return wind;
        } else if (o instanceof Map<?, ?> props) { // GSON automatically creates a map for unmapped objects.
            return new Wind(
                    ObjectUtils.defaultIfNull(ConversionUtils.toDouble(props.get("speed")), 0d),
                    ObjectUtils.defaultIfNull(ConversionUtils.toDouble(props.get("direction")), 0d)
            );
        }
        throw new IllegalArgumentException("Could not convert input to Wind object.");
    }

}
