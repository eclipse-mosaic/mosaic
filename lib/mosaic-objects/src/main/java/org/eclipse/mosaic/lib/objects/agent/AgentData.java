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

package org.eclipse.mosaic.lib.objects.agent;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.UnitData;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Holds relevant and volatile data about an agent, such as its
 * position, state, or current transportation leg.
 */
public class AgentData extends UnitData {

    private static final long serialVersionUID = 1L;

    public enum TripStatus {
        /**
         * Agent is waiting outside for the next transportation
         */
        WAITING,
        /**
         * Agent is walking
         */
        WALKING,
        /**
         * Agent is inside a shared vehicle, such as ride-sharing vehicle or shuttle bus
         */
        IN_SHARED_VEHICLE,
        /**
         * Agent is inside a private vehicle which was spawned by the agent.
         */
        IN_PRIVATE_VEHICLE,
        /**
         * Agent is inside a public transport vehicle, such as bus or subway.
         */
        IN_PT_VEHICLE,
        /**
         * Agent is inside a public transport vehicle, which is currently waiting at a stop or station.
         */
        IN_PT_VEHICLE_AT_STOP
    }

    private final double speed;
    private final String assignedVehicle;
    private final AgentRoute.Leg currentLeg;
    private final TripStatus tripStatus;

    /**
     * Creates a new agent data object.
     *
     * @param time            The time at which this data object was created.
     * @param name            The name of the agent.
     * @param position        The current geo position of the agent.
     * @param speed           The speed the agent in [m/s].
     * @param assignedVehicle The currently assigned vehicle of the agent (if any).
     * @param currentLeg      The currently assigned leg of the agent (if any).
     * @param status          The current trip status of the agent, such as waiting, walking, or inside a vehicle.
     */
    public AgentData(long time, String name, GeoPoint position, double speed, String assignedVehicle, AgentRoute.Leg currentLeg, TripStatus status) {
        super(time, name, position);
        this.speed = speed;
        this.assignedVehicle = assignedVehicle;
        this.currentLeg = currentLeg;
        this.tripStatus = status;
    }

    /**
     * Returns the speed of the agent in [m/s].
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * Returns the private or shared vehicle this agent is assigned to. Can be null.
     */
    public String getAssignedVehicle() {
        return assignedVehicle;
    }

    /**
     * Returns the current {@link AgentRoute.Leg} of the agent, which determines the route and mode of transport
     * of a part of the agents' journey. Can be null, e.g., if no leg is planned.
     */
    public AgentRoute.Leg getCurrentLeg() {
        return currentLeg;
    }

    /**
     * Returns the {@link TripStatus} of the agent, determining if the agent is waiting, walking, or inside a vehicle.
     */
    public TripStatus getTripStatus() {
        return tripStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AgentData other = (AgentData) o;
        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(assignedVehicle, other.assignedVehicle)
                .append(tripStatus, other.tripStatus)
                .append(currentLeg, other.currentLeg)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .appendSuper(super.hashCode())
                .append(speed)
                .append(assignedVehicle)
                .append(tripStatus)
                .append(currentLeg)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("speed", speed)
                .append("assignedVehicle", assignedVehicle)
                .append("state", tripStatus)
                .append("currentLeg", currentLeg)
                .toString();
    }
}