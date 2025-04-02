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

package org.eclipse.mosaic.fed.mapping.config;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class CParameterDeviations {

    public Double length;
    public Double width;
    public Double height;
    public Double minGap;
    public Double maxSpeed;
    public Double speedFactor;
    public Double accel;
    public Double decel;
    public Double tau;

    public CParameterDeviations copy() {
        final CParameterDeviations copy = new CParameterDeviations();
        copy.length = length;
        copy.width = width;
        copy.height = height;
        copy.minGap = minGap;
        copy.maxSpeed = maxSpeed;
        copy.speedFactor = speedFactor;
        copy.accel = accel;
        copy.decel = decel;
        copy.tau = tau;
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CParameterDeviations that = (CParameterDeviations) o;

        return new EqualsBuilder()
                .append(length, that.length)
                .append(width, that.width)
                .append(height, that.height)
                .append(minGap, that.minGap)
                .append(maxSpeed, that.maxSpeed)
                .append(speedFactor, that.speedFactor)
                .append(accel, that.accel)
                .append(decel, that.decel)
                .append(tau, that.tau)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(length)
                .append(width)
                .append(height)
                .append(minGap)
                .append(maxSpeed)
                .append(speedFactor)
                .append(accel)
                .append(decel)
                .append(tau)
                .toHashCode();
    }

    @Override
    public String toString() {
        return "CDeviations[length: " + length
                + ", width: " + width
                + ", height: " + height
                + ", minGap: " + minGap
                + ", maxSpeed: " + maxSpeed
                + ", speedFactor: " + speedFactor
                + ", accel: " + accel
                + ", decel: " + decel
                + ", tau: " + tau
                + "]";
    }
}
