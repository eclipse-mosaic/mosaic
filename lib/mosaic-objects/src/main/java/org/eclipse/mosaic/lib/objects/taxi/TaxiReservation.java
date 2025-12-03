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

package org.eclipse.mosaic.lib.objects.taxi;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;

public class TaxiReservation {

    public enum ReservationState {
        NEW, RETRIEVED, ASSIGNED, PICKED_UP;

        public static ReservationState of(int stateIdSumo) {
            return switch (stateIdSumo) {
                case 1 -> NEW;
                case 2 -> RETRIEVED;
                case 4 -> ASSIGNED;
                case 8 -> PICKED_UP;
                default -> throw new IllegalArgumentException("Unknown state: " + stateIdSumo);
            };
        }

    }

    private final String id;
    private final ReservationState reservationState;
    private final List<String> personList;
    private final String fromEdge;
    private final String toEdge;

    private TaxiReservation(String id, ReservationState reservationState, List<String> personList, String fromEdge, String toEdge) {
        this.id = id;
        this.reservationState = reservationState;
        this.personList = personList;
        this.fromEdge = fromEdge;
        this.toEdge = toEdge;
    }

    public String getId() {
        return id;
    }

    public ReservationState getReservationState() {
        return reservationState;
    }

    public List<String> getPersonList() {
        return personList;
    }

    public String getFromEdge() {
        return fromEdge;
    }

    public String getToEdge() {
        return toEdge;
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

        TaxiReservation other = (TaxiReservation) obj;
        return new EqualsBuilder()
                .appendSuper(super.equals(obj))
                .append(this.id, other.id)
                .append(this.reservationState, other.reservationState)
                .append(this.personList, other.personList)
                .append(this.fromEdge, other.fromEdge)
                .append(this.toEdge, other.toEdge)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(3, 89)
                .appendSuper(super.hashCode())
                .append(id)
                .append(reservationState)
                .append(personList)
                .append(fromEdge)
                .append(toEdge)
                .toHashCode();
    }

    public static class Builder {
        private String id;
        private ReservationState reservationState;
        private List<String> personList;
        private String fromEdge;
        private String toEdge;

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withReservationState(ReservationState reservationState) {
            this.reservationState = reservationState;
            return this;
        }

        public Builder withPersonList(List<String> personList) {
            this.personList = personList;
            return this;
        }

        public Builder withFromEdge(String fromEdge) {
            this.fromEdge = fromEdge;
            return this;
        }

        public Builder withToEdge(String toEdge) {
            this.toEdge = toEdge;
            return this;
        }

        public TaxiReservation build() {
            return new TaxiReservation(id, reservationState, personList, fromEdge, toEdge);
        }
    }
}
