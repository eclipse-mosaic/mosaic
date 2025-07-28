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

package org.eclipse.mosaic.lib.objects.taxi;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TaxiVehicleData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final int ALL_TAXIS = -1;
    public static final int EMPTY_TAXIS = 0;
    public static final int EMPTY_TO_PICK_UP_TAXIS = 1;
    public static final int OCCUPIED_TAXIS = 2;
    public static final int OCCUPIED_TO_PICK_UP_TAXIS = 3;

    private final String id;
    private final int state;
    private final int personCapacity;
    private final VehicleData vehicleData;
    private final String numberOfCustomersServed;
    private final String totalOccupiedDistanceInMeters;
    private final String totalOccupiedTimeInSeconds;
    private final List<String> customersToPickUpOrOnBoard;

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

        TaxiVehicleData other = (TaxiVehicleData) obj;
        return new EqualsBuilder()
                .appendSuper(super.equals(obj))
                .append(this.id, other.id)
                .append(this.state, other.state)
                .append(this.personCapacity, other.personCapacity)
                .append(this.numberOfCustomersServed, other.numberOfCustomersServed)
                .append(this.totalOccupiedDistanceInMeters, other.totalOccupiedDistanceInMeters)
                .append(this.totalOccupiedTimeInSeconds, other.totalOccupiedTimeInSeconds)
                .append(this.customersToPickUpOrOnBoard, other.customersToPickUpOrOnBoard)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(3, 89)
                .appendSuper(super.hashCode())
                .append(id)
                .append(state)
                .append(personCapacity)
                .append(numberOfCustomersServed)
                .append(totalOccupiedDistanceInMeters)
                .append(totalOccupiedTimeInSeconds)
                .append(customersToPickUpOrOnBoard)
                .toHashCode();
    }
}
