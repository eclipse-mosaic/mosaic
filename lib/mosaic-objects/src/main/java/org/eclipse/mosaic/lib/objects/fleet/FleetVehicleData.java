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

package org.eclipse.mosaic.lib.objects.fleet;

import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Holds information about fleet/taxi/drt vehicles, such as their
 * transport capacity and service status.
 */
public class FleetVehicleData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum State {
        EMPTY, PICKUP, OCCUPIED, OCCUPIED_AND_PICK_UP;

        public static State of(int stateIdSumo) {
            return switch (stateIdSumo) {
                case 0 -> EMPTY;
                case 1 -> PICKUP;
                case 2 -> OCCUPIED;
                case 3 -> OCCUPIED_AND_PICK_UP;
                default -> throw new IllegalArgumentException("Unknown state: " + stateIdSumo);
            };
        }

    }

    private final String id;
    private final State state;
    private final int personCapacity;
    private final VehicleData vehicleData;
    private final int totalNumPersonsServed;
    private final List<String> personsToPickUpOrOnBoard;

    public FleetVehicleData(
            String id, State state, int personCapacity, VehicleData vehicleData, int totalNumPersonsServed, List<String> personsToPickUpOrOnBoard
    ) {
        this.id = id;
        this.state = state;
        this.personCapacity = personCapacity;
        this.vehicleData = vehicleData;
        this.totalNumPersonsServed = totalNumPersonsServed;
        this.personsToPickUpOrOnBoard = personsToPickUpOrOnBoard;
    }

    /**
     * Returns the vehicle id of the vehicle.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the current service state of the vehicle.
     */
    public State getState() {
        return state;
    }

    /**
     * Returns additional vehicle data of this vehicle.
     */
    public VehicleData getVehicleData() {
        return vehicleData;
    }

    /**
     * Returns the maximum capacity of this vehicle.
     */
    public int getPersonCapacity() {
        return personCapacity;
    }

    /**
     * Returns the total number of persons served by this vehicle.
     */
    public int getTotalNumPersonsServed() {
        return totalNumPersonsServed;
    }

    /**
     * Returns a list of persons planned to be picked up or currently being on board of this vehicle.
     */
    public List<String> getPersonsToPickUpOrOnBoard() {
        return personsToPickUpOrOnBoard;
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

        FleetVehicleData other = (FleetVehicleData) obj;
        return new EqualsBuilder()
                .appendSuper(super.equals(obj))
                .append(this.id, other.id)
                .append(this.state, other.state)
                .append(this.personCapacity, other.personCapacity)
                .append(this.totalNumPersonsServed, other.totalNumPersonsServed)
                .append(this.personsToPickUpOrOnBoard, other.personsToPickUpOrOnBoard)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(3, 89)
                .appendSuper(super.hashCode())
                .append(id)
                .append(state)
                .append(personCapacity)
                .append(totalNumPersonsServed)
                .append(personsToPickUpOrOnBoard)
                .toHashCode();
    }
}
