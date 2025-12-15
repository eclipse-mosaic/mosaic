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

package org.eclipse.mosaic.interactions.traffic;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import org.eclipse.mosaic.rti.api.Interaction;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serial;
import java.util.List;

/**
 * Provides information for assigning ride reservations to a fleet vehicle.
 */
public class FleetVehicleAssignment extends Interaction {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String TYPE_ID = createTypeIdentifier(FleetVehicleAssignment.class);

    private final String vehicleId;
    private final List<String> reservationIds;

    public FleetVehicleAssignment(long time, String vehicleId, List<String> reservationIds) {
        super(time);
        this.vehicleId = vehicleId;
        this.reservationIds = reservationIds;
    }

    /**
     * Getter for the vehicle identifier.
     *
     * @return String identifying the vehicle sending this interaction
     */
    public String getVehicleId() {
        return vehicleId;
    }

    /**
     * The list of reservation IDs to be handled by the taxi in the given order.
     */
    public List<String> getReservationIds() {
        return reservationIds;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(5, 67)
                .append(vehicleId)
                .append(reservationIds)
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

        FleetVehicleAssignment other = (FleetVehicleAssignment) obj;
        return new EqualsBuilder()
                .append(this.vehicleId, other.vehicleId)
                .append(this.reservationIds, other.reservationIds)
                .isEquals();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("vehicleId", vehicleId)
                .append("reservationIds", reservationIds)
                .toString();
    }
}
