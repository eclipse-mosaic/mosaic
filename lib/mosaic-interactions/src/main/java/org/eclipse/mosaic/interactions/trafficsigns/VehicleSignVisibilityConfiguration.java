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

package org.eclipse.mosaic.interactions.trafficsigns;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import org.eclipse.mosaic.rti.api.Interaction;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serial;

/**
 * Informs about a vehicles' sight distance and opening angle to detect traffic signs
 * with the help of mosaic-traffic-signs (Extended feature).
 */
public final class VehicleSignVisibilityConfiguration extends Interaction {

    @Serial
    private static final long serialVersionUID = 1L;

    public final static String TYPE_ID = createTypeIdentifier(VehicleSignVisibilityConfiguration.class);

    /**
     * The vehicles name that has sent the interaction.
     */
    private final String vehicleId;

    /**
     * The sender vehicles current sight distance.
     */
    private final double sightDistance;

    /**
     * Creates an interaction that includes the sender name (vehicle) and its sight distance (in m).
     *
     * @param time          Timestamp of this interaction, unit: [ns]
     * @param vehicleId     vehicle identifier
     * @param sightDistance sight distance of the vehicle, unit: [m]
     */
    public VehicleSignVisibilityConfiguration(long time, String vehicleId, double sightDistance) {
        super(time);
        this.vehicleId = vehicleId;
        this.sightDistance = sightDistance;
    }

    public double getSightDistance() {
        return sightDistance;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 29)
                .append(vehicleId)
                .append(sightDistance)
                .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        VehicleSignVisibilityConfiguration other = (VehicleSignVisibilityConfiguration) obj;
        return new EqualsBuilder()
                .append(this.vehicleId, other.vehicleId)
                .append(this.sightDistance, other.sightDistance)
                .isEquals();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("vehicleId", vehicleId)
                .append("sightDistance", sightDistance)
                .toString();
    }
}
