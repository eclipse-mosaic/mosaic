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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serial;
import javax.annotation.Nullable;

public class VehicleObject extends SpatialObject<VehicleObject> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The current speed of the vehicle. [m/s]
     */
    private double speed;
    /**
     * The current heading of the vehicle. [degrees clockwise from north]
     */
    private double heading;
    /**
     * The edge the vehicle is currently on.
     */
    private String edgeId;
    /**
     * The lane index the vehicle is currently on.
     */
    private int laneIndex;
    /**
     * The length of the vehicle. [m]
     */
    private double length;
    /**
     * The width of the vehicle. [m]
     */
    private double width;
    /**
     * The height of the vehicle. [m]
     */
    private double height;
    /**
     * The 2D bounding box of a vehicle from birds eye view.
     */
    private transient VehicleBoundingBox boundingBox = null;

    public VehicleObject(String id) {
        super(id);
    }

    @Override
    public VehicleObject setPosition(CartesianPoint position) {
        cartesianPosition.set(position);
        position.toVector3d(this);
        this.boundingBox = null;
        return this;
    }

    public VehicleObject setEdgeAndLane(String edgeId, int laneIndex) {
        this.edgeId = edgeId;
        this.laneIndex = laneIndex;
        return this;
    }

    @Nullable
    public String getEdgeId() {
        return edgeId;
    }

    public int getLaneIndex() {
        return laneIndex;
    }

    public VehicleObject setSpeed(double speed) {
        this.speed = speed;
        return this;
    }

    public double getSpeed() {
        return speed;
    }

    public VehicleObject setHeading(double heading) {
        this.heading = heading;
        this.boundingBox = null;
        return this;
    }

    public double getHeading() {
        return heading;
    }

    public VehicleObject setDimensions(double length, double width, double height) {
        this.length = length;
        this.width = width;
        this.height = height;
        this.boundingBox = null;
        return this;
    }

    public double getLength() {
        return length;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    /**
     * Returns the bounding box for a spatial object if requested.
     * Calculation is only triggered if bounding box is requested.
     */
    @Override
    public SpatialObjectBoundingBox getBoundingBox() {
        if (boundingBox == null) {
            boundingBox = VehicleBoundingBox.createFromVehicleObject(this);
        }
        return boundingBox;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VehicleObject that = (VehicleObject) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(speed, that.speed)
                .append(heading, that.heading)
                .append(edgeId, that.edgeId)
                .append(laneIndex, that.laneIndex)
                .append(length, that.length)
                .append(width, that.width)
                .append(height, that.height)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(5, 11)
                .appendSuper(super.hashCode())
                .append(speed)
                .append(heading)
                .append(edgeId)
                .append(laneIndex)
                .append(length)
                .append(width)
                .append(height)
                .toHashCode();
    }

    /**
     * Returns a hard copy of the {@link VehicleObject}, this should be used
     * when the data of a perceived vehicle is to be altered or stored in memory.
     *
     * @return a copy of the {@link VehicleObject}
     */
    @Override
    public VehicleObject copy() {
        return new VehicleObject(getId())
                .setHeading(getHeading())
                .setSpeed(getSpeed())
                .setEdgeAndLane(getEdgeId(), getLaneIndex())
                .setDimensions(getLength(), getWidth(), getHeight())
                .setPosition(getProjectedPosition());
    }
}