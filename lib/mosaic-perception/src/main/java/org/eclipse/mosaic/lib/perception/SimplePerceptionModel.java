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

package org.eclipse.mosaic.lib.perception;

import static java.lang.Math.toRadians;

import org.eclipse.mosaic.lib.math.MathUtils;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.math.VectorUtils;
import org.eclipse.mosaic.lib.perception.objects.SpatialObject;
import org.eclipse.mosaic.lib.spatial.BoundingBox;

import org.apache.commons.lang3.Validate;

/**
 * Checks whether the pre-selection of vehicles actually fall in the viewing range of the
 * ego vehicle. This implementation works on the 2D plane and handles the perceiving entity as
 * well as the perceiving objects as single-points. This model includes no occlusion whatsoever.
 */
public class SimplePerceptionModel implements PerceptionModel {

    private final String ownerId;
    private final PerceptionConfiguration configuration;

    private final Vector3d origin = new Vector3d();
    private final Vector3d directionVector = new Vector3d();
    private final Vector3d rightBoundVector = new Vector3d();
    private final Vector3d leftBoundVector = new Vector3d();

    /**
     * The axis-aligned bounding box around the sight area.
     */
    private final BoundingBox sightAreaBoundingBox = new BoundingBox();

    /**
     * Vector object used for temporary calculations, to avoid unnecessary object allocations.
     */
    private final Vector3d tmpVector1 = new Vector3d();

    /**
     * Vector object used for temporary calculations, to avoid unnecessary object allocations.
     */
    private final Vector3d tmpVector2 = new Vector3d();

    public SimplePerceptionModel(String ownerId, PerceptionConfiguration configuration) {
        Validate.isTrue(configuration.getViewingAngle() >= 0 && configuration.getViewingAngle() <= 360,
                "Only viewing angles from 0 to 360 degrees are supported.");

        this.ownerId = ownerId;
        this.configuration = configuration;
    }

    /**
     * Updates the position and heading for subsequent checks via {@link #isInRange}.
     *
     * @param ego the ego providing position and heading information
     */
    public void updateOrigin(PerceptionEgo ego) {
        ego.getProjectedPosition().toVector3d(this.origin);
        calculateSightBoundingVectors(ego.getHeading());
        calculateMinimumBoundingRectangle(ego.getHeading());
    }

    @Override
    public BoundingBox getBoundingBox() {
        return sightAreaBoundingBox;
    }

    @Override
    public boolean isInRange(SpatialObject<?> other) {
        if (other.getId().equals(this.ownerId)) { // cannot see itself
            return false;
        }
        synchronized (tmpVector1) {
            // writes position of other to tmpVector1 and subtract origin to create a relative vector pointing to the other object
            other.getProjectedPosition().toVector3d(tmpVector1).subtract(origin);
            // we use tmpVector2 as origin from the viewpoint of this object
            tmpVector2.set(0, 0, 0);

            if (tmpVector1.magnitude() > configuration.getViewingRange()) { // the other vehicle is NOT in range
                return false;
            }
            if (MathUtils.isFuzzyEqual(configuration.getViewingAngle(), 360d)) { // for 360° viewing angle FOV check is obsolete
                return true;
            } else if (configuration.getViewingAngle() < 180d) { // for < 180 degree viewing angle we use left and right vector
                return isBetweenVectors(tmpVector1, tmpVector2, leftBoundVector, rightBoundVector)
                        || liesOnVector(tmpVector1, leftBoundVector)
                        || liesOnVector(tmpVector1, rightBoundVector);
            } else { // for >= 180 degree do two checks: 1st between direction vector and right or 2nd between direction vector and left
                return isBetweenVectors(tmpVector1, tmpVector2, directionVector, rightBoundVector)
                        || isBetweenVectors(tmpVector1, tmpVector2, leftBoundVector, directionVector)
                        || liesOnVector(tmpVector1, leftBoundVector)
                        || liesOnVector(tmpVector1, rightBoundVector)
                        || liesOnVector(tmpVector1, directionVector);
            }
        }
    }

    private boolean isBetweenVectors(Vector3d pointToEvaluate, Vector3d linePoint, Vector3d leftVector, Vector3d rightVector) {
        return VectorUtils.isLeftOfLine(pointToEvaluate, linePoint, rightVector) // is left of right edge
                && !VectorUtils.isLeftOfLine(pointToEvaluate, linePoint, leftVector); // is right of left vector
    }

    /**
     * Calculates the magnitude of the cross product of the two vectors
     * and checks if they point in the same direction (to avoid objects being located exactly in the opposite direction).
     * If the magnitude is equal to 0 the point will be on the line, however could be anywhere in each direction, so
     * we additionally check if the directions match.
     * Note: This method only works, since we know that we work around the origin.
     *
     * @param pointToEvaluate point to be evaluated
     * @param line            check point against this line
     * @return {@code true} if point lies on line, otherwise {@code false}
     */
    private boolean liesOnVector(Vector3d pointToEvaluate, Vector3d line) {
        // first check if the magnitude of the cross product is equal to 0
        double magCross = pointToEvaluate.z * line.x - pointToEvaluate.x * line.z;
        if (!MathUtils.isFuzzyEqual(magCross, 0)) {
            return false;
        }
        // check if point vector is pointing in the right direction
        if (Math.abs(line.z) >= Math.abs(line.x)) {
            return line.z > 0 ? pointToEvaluate.z > 0 : pointToEvaluate.z <= 0;
        } else {
            return line.x > 0 ? pointToEvaluate.x > 0 : pointToEvaluate.x <= 0;
        }
    }

    /**
     * Calculates the two unit vectors circumscribing the circular sector of the viewing field.
     */
    private void calculateSightBoundingVectors(double heading) {
        synchronized (tmpVector1) {
            // getting the direction vector of the heading from origin (result is written into direction)
            VectorUtils.getDirectionVectorFromHeading(heading, directionVector);
            double viewingAngleRadHalf = toRadians(configuration.getViewingAngle()) / 2;
            // scale vector by range for minimum bounding rectangle
            directionVector.multiply(configuration.getViewingRange());
            // rotate the direction vector to the right
            rightBoundVector.set(directionVector).rotate(-viewingAngleRadHalf, VectorUtils.UP);
            // rotate the direction vector to the left
            leftBoundVector.set(directionVector).rotate(viewingAngleRadHalf, VectorUtils.UP);
        }
    }

    private void calculateMinimumBoundingRectangle(double heading) {
        synchronized (tmpVector1) {
            double headingRad = toRadians(heading);
            double halfViewingAngleRad = toRadians(configuration.getViewingAngle()) / 2;
            sightAreaBoundingBox.clear();
            // add origin, end of leftbound and end of rightbound vector as possible extremes of the bounding rectangle
            sightAreaBoundingBox.add(origin, tmpVector1.set(origin).add(leftBoundVector), tmpVector2.set(origin).add(rightBoundVector));

            // check if the opening angle includes any of the following extremes in both directions on x- and z-axis
            if (Math.abs(MathUtils.angleDif(headingRad, 0)) < halfViewingAngleRad) { // NORTH
                sightAreaBoundingBox.add(tmpVector1.set(origin.x, origin.y, origin.z - configuration.getViewingRange()));
            }
            if (Math.abs(MathUtils.angleDif(headingRad, Math.PI / 2)) < halfViewingAngleRad) { // EAST
                sightAreaBoundingBox.add(tmpVector1.set(origin.x + configuration.getViewingRange(), origin.y, origin.z));
            }
            if (Math.abs(MathUtils.angleDif(headingRad, Math.PI)) < halfViewingAngleRad) { // SOUTH
                sightAreaBoundingBox.add(tmpVector1.set(origin.x, origin.y, origin.z + configuration.getViewingRange()));
            }
            if (Math.abs(MathUtils.angleDif(headingRad, (3 * Math.PI) / 2.0)) < halfViewingAngleRad) { // WEST
                sightAreaBoundingBox.add(tmpVector1.set(origin.x - configuration.getViewingRange(), origin.y, origin.z));
            }
        }
    }
}