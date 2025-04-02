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

package org.eclipse.mosaic.lib.objects.trafficsign;

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.math.MathUtils;
import org.eclipse.mosaic.lib.util.objects.Position;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The basics of any traffic sign.
 */
public abstract class TrafficSign<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<T> signContents = new ArrayList<>();

    /**
     * The ID of the traffic sign.
     */
    private final String id;

    /**
     * Represents the position of the traffic sign
     * on the SUMO map.
     */
    private final CartesianPoint position;

    /**
     * Represents the position of the traffic sign as geographic position.
     */
    private final GeoPoint geoPosition;

    /**
     * The edge the traffic sign is meant for.
     */
    private final String connectionId;

    /**
     * Tells the lane the traffic sign is meant for.
     * "-1" means the traffic sign counts for every lane on the edge.
     */
    private int lane = -1;

    /**
     * The visibility of the traffic sign for vehicles.
     * 0: not visible
     * 1: very good visible
     */
    private double visibility = 1.0;

    /**
     * The angle of the traffic sign for vehicles.
     * 0.0 is north, 90.0 is east
     */
    private double angle = 0.0;

    /**
     * Tells if the traffic sign is variable.
     */
    private boolean isVariable = false;

    /**
     * Creates a traffic sign with the minimum values: position and edge id.
     *
     * @param id       The traffic sign id.
     * @param position The position of the sign.
     * @param connectionId     The corresponding edge of the sign.
     */
    public TrafficSign(String id, Position position, String connectionId) {
        this.id = id;
        this.position = position.getProjectedPosition();
        this.geoPosition = position.getGeographicPosition();
        this.connectionId = connectionId;
    }

    void addSignContents(List<T> signContents) {
        this.signContents.addAll(signContents);
    }

    /**
     * Returns the contents of the sign.
     * Needs to be public, because it is accessed by the FileVisualizer using reflection.
     *
     * @return a collection of contents of the sign
     */
    @SuppressWarnings("WeakerAccess")
    public List<T> getSignContents() {
        return signContents;
    }

    /**
     * Sets the lane the traffic sign is valid for.
     * -1 means traffic sign is valid for all lanes.
     *
     * @param lane The lane index.
     */
    public TrafficSign setLane(int lane) {
        this.lane = lane;
        return this;
    }

    /**
     * Sets the visibility of the traffic sign (0-1).
     *
     * @param visibility The visibility of the sign.
     */
    public TrafficSign setVisibility(double visibility) {
        this.visibility = MathUtils.clamp(visibility, 0, 1.0d);
        return this;
    }

    /**
     * Sets the angle of the traffic sign.
     * 0.0 is north, 90.0 is east
     *
     * @param angle The angle of the sign.
     */
    public TrafficSign setAngle(double angle) {
        this.angle = angle;
        return this;
    }

    /**
     * Sets whether the variability of the traffic sign.
     *
     * @param isVariable Whether the sign is variable.
     */
    public TrafficSign setVariability(boolean isVariable) {
        this.isVariable = isVariable;
        return this;
    }

    /**
     * Returns the id of the traffic sign.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the positions of the traffic sign.
     *
     * @return the positions of the traffic sign
     */
    public CartesianPoint getPosition() {
        return position;
    }

    /**
     * Returns the geographic position of the traffic sign.
     *
     * @return the geographic position of the traffic sign
     */
    public GeoPoint getGeoPosition() {
        return geoPosition;
    }

    /**
     * Returns the visibility of the traffic sign between 0 and 1.
     * 0 is not visible.
     * 1 is very good visible.
     */
    public double getVisibility() {
        return visibility;
    }

    /**
     * Returns the angle of the traffic sign.
     * 0.0 is north, 90.0 is east
     */
    public double getAngle() {
        return angle;
    }

    /**
     * Returns the lane of the traffic sign.
     * -1 means the traffic sign is valid for every lane of the edge.
     */
    public int getLane() {
        return lane;
    }

    /**
     * Returns the edge of the traffic sign.
     */
    public String getConnectionId() {
        return connectionId;
    }

    /**
     * Tells whether the traffic sign is variable.
     */
    public boolean isVariable() {
        return isVariable;
    }

    /**
     * Returns the type of the traffic sign.
     */
    public abstract String getTypeId();

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("position", position)
                .append("geoPosition", geoPosition)
                .append("angle", angle)
                .append("connection", connectionId)
                .append("lane", lane)
                .append("isVariable", isVariable)
                .append("visibility", visibility)
                .toString();
    }
}
