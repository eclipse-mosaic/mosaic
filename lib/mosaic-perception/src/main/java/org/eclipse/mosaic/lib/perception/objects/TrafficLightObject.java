/*
 * Copyright (c) 2022 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.lib.perception.objects;

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serial;

public class TrafficLightObject extends SpatialObject<TrafficLightObject> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Id of the group the individual traffic light belongs to.
     */
    private String trafficLightGroupId;
    /**
     * The current state of the traffic light (see {@link TrafficLightState}).
     */
    private TrafficLightState trafficLightState;
    /**
     * The incoming lane controlled by this traffic light.
     */
    private String incomingLane;
    /**
     * The outgoing lane controlled by this traffic light.
     */
    private String outgoingLane;
    /**
     * The bounding box of a traffic light is represented by a single point.
     */
    private transient PointBoundingBox boundingBox;

    public TrafficLightObject(String id) {
        super(id);
    }

    @Override
    public TrafficLightObject setPosition(CartesianPoint position) {
        cartesianPosition.set(position);
        position.toVector3d(this);
        return this;
    }

    @Override
    public SpatialObjectBoundingBox getBoundingBox() {
        if (boundingBox == null) {
            boundingBox = new PointBoundingBox(this);
        }
        return boundingBox;
    }

    public String getTrafficLightGroupId() {
        return trafficLightGroupId;
    }

    public TrafficLightObject setTrafficLightGroupId(String trafficLightGroupId) {
        this.trafficLightGroupId = trafficLightGroupId;
        return this;
    }

    public String getIncomingLane() {
        return incomingLane;
    }

    public TrafficLightObject setIncomingLane(String incomingLane) {
        this.incomingLane = incomingLane;
        return this;
    }

    public String getOutgoingLane() {
        return outgoingLane;
    }

    public TrafficLightObject setOutgoingLane(String outgoingLane) {
        this.outgoingLane = outgoingLane;
        return this;
    }

    public TrafficLightState getTrafficLightState() {
        return trafficLightState;
    }

    public TrafficLightObject setTrafficLightState(TrafficLightState trafficLightState) {
        this.trafficLightState = trafficLightState;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TrafficLightObject that = (TrafficLightObject) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(trafficLightGroupId, that.trafficLightGroupId)
                .append(trafficLightState, that.trafficLightState)
                .append(incomingLane, that.incomingLane)
                .append(outgoingLane, that.outgoingLane)
                .append(boundingBox, that.boundingBox)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(5, 11)
                .appendSuper(super.hashCode())
                .append(trafficLightGroupId)
                .append(trafficLightState)
                .append(incomingLane)
                .append(outgoingLane)
                .toHashCode();
    }

    /**
     * Returns a hard copy of the {@link TrafficLightObject}, this should be used
     * when the data of a perceived traffic light is to be altered or stored in memory.
     *
     * @return a copy of the {@link TrafficLightObject}
     */
    @Override
    public TrafficLightObject copy() {
        return new TrafficLightObject(getId())
                .setIncomingLane(getIncomingLane())
                .setOutgoingLane(getOutgoingLane())
                .setTrafficLightState(getTrafficLightState())
                .setTrafficLightGroupId(getTrafficLightGroupId())
                .setPosition(getProjectedPosition());

    }
}