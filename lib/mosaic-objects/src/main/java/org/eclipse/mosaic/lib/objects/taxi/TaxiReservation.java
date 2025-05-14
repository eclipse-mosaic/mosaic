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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;

public class TaxiReservation {

    public static final int STATE_ALL_RESERVATIONS = 0;
    public static final int ONLY_NEW_RESERVATIONS = 1;
    public static final int ALREADY_RETRIEVED_RESERVATIONS = 2;
    public static final int ALREADY_ASSIGNED_RESERVATIONS = 4;
    public static final int ALREADY_PICKED_UP_RESERVATIONS = 8;

    private final String id;
    private final int reservationState;
    private final List<String> personList;
    private final String group;
    private final String fromEdge;
    private final String toEdge;
    private final double departPos;
    private final double arrivalPos;
    private final double depart;
    private final double reservationTime;

    private TaxiReservation(String id, int reservationState, List<String> personList, String group, String fromEdge,
                            String toEdge, double departPos, double arrivalPos, double depart, double reservationTime) {
        this.id = id;
        this.reservationState = reservationState;
        this.personList = personList;
        this.group = group;
        this.fromEdge = fromEdge;
        this.toEdge = toEdge;
        this.departPos = departPos;
        this.arrivalPos = arrivalPos;
        this.depart = depart;
        this.reservationTime = reservationTime;
    }

    public String getId() {
        return id;
    }

    public int getReservationState() {
        return reservationState;
    }

    public List<String> getPersonList() {
        return personList;
    }

    public String getGroup() {
        return group;
    }

    public String getFromEdge() {
        return fromEdge;
    }

    public String getToEdge() {
        return toEdge;
    }

    public double getDepartPos() {
        return departPos;
    }

    public double getArrivalPos() {
        return arrivalPos;
    }

    public double getDepart() {
        return depart;
    }

    public double getReservationTime() {
        return reservationTime;
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
                .append(this.group, other.group)
                .append(this.fromEdge, other.fromEdge)
                .append(this.toEdge, other.toEdge)
                .append(this.departPos, other.departPos)
                .append(this.arrivalPos, other.arrivalPos)
                .append(this.depart, other.depart)
                .append(this.reservationTime, other.reservationTime)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(3, 89)
                .appendSuper(super.hashCode())
                .append(id)
                .append(reservationState)
                .append(personList)
                .append(group)
                .append(fromEdge)
                .append(toEdge)
                .append(departPos)
                .append(arrivalPos)
                .append(depart)
                .append(reservationTime)
                .toHashCode();
    }

    public static class Builder {
        private String id;
        private int reservationState;
        private List<String> personList;
        private String group;
        private String fromEdge;
        private String toEdge;
        private double departPos;
        private double arrivalPos;
        private double depart;
        private double reservationTime;

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withReservationState(int reservationState) {
            this.reservationState = reservationState;
            return this;
        }

        public Builder withPersonList(List<String> personList) {
            this.personList = personList;
            return this;
        }

        public Builder withGroup(String group) {
            this.group = group;
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

        public Builder withDepartPos(double departPos) {
            this.departPos = departPos;
            return this;
        }

        public Builder withArrivalPos(double arrivalPos) {
            this.arrivalPos = arrivalPos;
            return this;
        }

        public Builder withDepart(double depart) {
            this.depart = depart;
            return this;
        }

        public Builder withReservationTime(double reservationTime) {
            this.reservationTime = reservationTime;
            return this;
        }

        public TaxiReservation build() {
            return new TaxiReservation(id, reservationState, personList, group, fromEdge, toEdge, departPos,
                    arrivalPos, depart, reservationTime);
        }
    }
}
