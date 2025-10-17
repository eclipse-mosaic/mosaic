/*
 * Copyright (c) 2023 Fraunhofer FOKUS and others. All rights reserved.
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

import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.math.VectorUtils;
import org.eclipse.mosaic.lib.spatial.Edge;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serial;
import java.util.List;

/**
 * An object representing a vehicles' 2D bounding box.
 * <pre>
 *   frontRightCorner----------rightEdge----------backRightCorner
 *                  |                             |
 *   Heading <== frontEdge                     backEdge
 *                  |                             |
 *    frontLeftCorner----------leftEdge-----------backLeftCorner
 * </pre>
 * All points are in global coordinates.
 */
public class VehicleBoundingBox implements SpatialObjectBoundingBox {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Vector3d> allCorners;
    public final Vector3d frontRightCorner;
    public final Vector3d backRightCorner;
    public final Vector3d backLeftCorner;
    public final Vector3d frontLeftCorner;

    private final List<Edge<Vector3d>> allEdges;
    public final Edge<Vector3d> frontEdge;
    public final Edge<Vector3d> rightEdge;
    public final Edge<Vector3d> backEdge;
    public final Edge<Vector3d> leftEdge;

    private VehicleBoundingBox(Vector3d frontRightCorner, Vector3d backRightCorner, Vector3d backLeftCorner, Vector3d frontLeftCorner) {
        this.frontRightCorner = frontRightCorner;
        this.backRightCorner = backRightCorner;
        this.backLeftCorner = backLeftCorner;
        this.frontLeftCorner = frontLeftCorner;
        allCorners = Lists.newArrayList(frontRightCorner, backRightCorner, backLeftCorner, frontLeftCorner);
        frontEdge = new Edge<>(frontLeftCorner, frontRightCorner);
        rightEdge = new Edge<>(frontRightCorner, backRightCorner);
        backEdge = new Edge<>(backRightCorner, backLeftCorner);
        leftEdge = new Edge<>(backLeftCorner, frontLeftCorner);
        allEdges = Lists.newArrayList(frontEdge, rightEdge, backEdge, leftEdge);
    }

    /**
     * This method is used to generate a {@link VehicleBoundingBox}-object given its position, dimensions, and heading.
     * Note: We assume that the position is defined in the middle of the front bumper and the heading is defined relative to
     * this position.
     *
     * @param vehicleObject the {@link VehicleObject} containing its dimensions and heading
     * @return the build {@link VehicleBoundingBox}
     */
    public static VehicleBoundingBox createFromVehicleObject(VehicleObject vehicleObject) {
        Vector3d headingVector = new Vector3d();
        VectorUtils.getDirectionVectorFromHeading(vehicleObject.getHeading(), headingVector).norm();
        double length = vehicleObject.getLength();
        double halfWidth = vehicleObject.getWidth() / 2;
        // we get the two front corners by rotating the normalized heading vector by +/- 90° and multiplying it by half the vehicle width
        // and then add it on the vehicle position (rotation -> translation)
        Vector3d pointA = new Vector3d(headingVector)
                .rotateDeg(90d, VectorUtils.UP) // rotate to 90° to the right
                .multiply(halfWidth) // adjust the length of the vector to half the vehicles' width
                .add(vehicleObject); // add on top of the vehicle position
        Vector3d pointD = new Vector3d(headingVector)
                .rotateDeg(-90d, VectorUtils.UP) // rotate to 90° to the left
                .multiply(halfWidth) // adjust the length of the vector to half the vehicles' width
                .add(vehicleObject); // add on top of the vehicle position
        // similarly to the front corners, we get the back corners by rotating the heading vector by 180° (i.e., multiplying by -1)
        // then set the appropriate length using the vehicle length and finally add it to the respective corners (rotation -> translation)
        Vector3d pointB = new Vector3d(headingVector)
                .multiply(-1d) // rotate heading vector by 180°
                .multiply(length) // adjust the length of the vector to vehicle length
                .add(pointA); // add on top of the front right corner
        Vector3d pointC = new Vector3d(headingVector)
                .multiply(-1d) // rotate heading vector by 180°
                .multiply(length) // adjust the length of the vector to vehicle length
                .add(pointD); // add on top of the front left corner
        return new VehicleBoundingBox(pointA, pointB, pointC, pointD);
    }

    @Override
    public List<Vector3d> getAllCorners() {
        return allCorners;
    }

    @Override
    public List<Edge<Vector3d>> getAllEdges() {
        return allEdges;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VehicleBoundingBox that = (VehicleBoundingBox) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(frontRightCorner, that.frontRightCorner)
                .append(backRightCorner, that.backRightCorner)
                .append(backLeftCorner, that.backLeftCorner)
                .append(frontLeftCorner, that.frontLeftCorner)
                .append(allCorners, that.allCorners)
                .append(allEdges, that.allEdges)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(5, 11)
                .appendSuper(super.hashCode())
                .append(allCorners)
                .append(allEdges)
                .toHashCode();
    }
}
