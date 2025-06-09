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

import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

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

    private TaxiVehicleData(String id, int state, int personCapacity, VehicleData vehicleData, String numberOfCustomersServed,
        String totalOccupiedDistanceInMeters, String totalOccupiedTimeInSeconds, List<String> customersToPickUpOrOnBoard) {
        this.id = id;
        this.state = state;
        this.personCapacity = personCapacity;
        this.vehicleData = vehicleData;
        this.numberOfCustomersServed = numberOfCustomersServed;
        this.totalOccupiedDistanceInMeters = totalOccupiedDistanceInMeters;
        this.totalOccupiedTimeInSeconds = totalOccupiedTimeInSeconds;
        this.customersToPickUpOrOnBoard = customersToPickUpOrOnBoard;
    }

    public String getId() {
        return id;
    }

    public int getState() {
        return state;
    }

    public int getPersonCapacity() {
        return personCapacity;
    }

    public VehicleData getVehicleData() {
        return vehicleData;
    }

    public String getNumberOfCustomersServed() {
        return numberOfCustomersServed;
    }

    public String getTotalOccupiedDistanceInMeters() {
        return totalOccupiedDistanceInMeters;
    }

    public String getTotalOccupiedTimeInSeconds() {
        return totalOccupiedTimeInSeconds;
    }

    public List<String> getCustomersToPickUpOrOnBoard() {
        return customersToPickUpOrOnBoard;
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

    public static class Builder {
        private String id;
        private int state;
        private int personCapacity;
        private VehicleData vehicleData;
        private String customersServed;
        private String totalOccupiedDistanceInMeters;
        private String totalOccupiedTimeInSeconds;
        private List<String> customersToPickUpOrOnBoard;

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withState(int state) {
            this.state = state;
            return this;
        }

        public Builder withPersonCapacity(int personCapacity) {
            this.personCapacity = personCapacity;
            return this;
        }

        public Builder withVehicleData(VehicleData vehicleData) {
            this.vehicleData = vehicleData;
            return this;
        }

        public Builder withCustomersServed(String customersServed) {
            this.customersServed = customersServed;
            return this;
        }

        public Builder withTotalOccupiedDistanceInMeters(String totalOccupiedDistanceInMeters) {
            this.totalOccupiedDistanceInMeters = totalOccupiedDistanceInMeters;
            return this;
        }

        public Builder withTotalOccupiedTimeInSeconds(String totalOccupiedTimeInSeconds) {
            this.totalOccupiedTimeInSeconds = totalOccupiedTimeInSeconds;
            return this;
        }

        public Builder withCustomersToPickUpOrOnBoard(String customersToPickUpOrOnBoard) {
            this.customersToPickUpOrOnBoard = Arrays.stream(customersToPickUpOrOnBoard.split(",")).toList();
            return this;
        }

        public TaxiVehicleData build() {
            return new TaxiVehicleData(id, state, personCapacity,vehicleData, customersServed, totalOccupiedDistanceInMeters,
                    totalOccupiedTimeInSeconds, customersToPickUpOrOnBoard);
        }
    }
}
