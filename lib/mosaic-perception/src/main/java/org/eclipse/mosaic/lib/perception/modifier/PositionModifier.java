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

package org.eclipse.mosaic.lib.perception.modifier;

import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.math.VectorUtils;
import org.eclipse.mosaic.lib.perception.PerceptionEgo;
import org.eclipse.mosaic.lib.perception.PerceptionModifier;
import org.eclipse.mosaic.lib.perception.objects.SpatialObject;

import java.util.List;

/**
 * A {@link PerceptionModifier} that adjusts position measurements for perceived vehicle.
 * Based on {@see <a href="https://mediatum.ub.tum.de/1519952">
 * Virtual Sensorics: Simulated Environmental Perception for Automated Driving Systems</a>}.
 * The basic principle is that lateral and longitudinal position measurements are afflicted by
 * different gaussian distributed errors.
 * To calculate these, all points are transformed into a coordinate system relative to the position and
 * orientation of the ego vehicle, and after error calculation re-transformed.
 */
public class PositionModifier implements PerceptionModifier {

    /**
     * Default standard deviation for longitudinal error. (Taken from referenced source)
     */
    private static final double SIGMA_LON_OFFSET = 1.333; // [m]

    /**
     * Default standard deviation for lateral error. (Taken from referenced source)
     */
    private static final double SIGMA_LAT_OFFSET = 0.390; // [m]

    /**
     * Standard deviation for longitudinal error.
     */
    private final double longitudinalStandardDeviation;

    /**
     * Standard deviation for lateral error.
     */
    private final double lateralStandardDeviation;

    private final RandomNumberGenerator rng;

    /**
     * Constructor using default standard deviations for error calculation.
     *
     * @param rng {@link RandomNumberGenerator} to draw gaussian variables from
     */
    public PositionModifier(RandomNumberGenerator rng) {
        this.rng = rng;
        this.longitudinalStandardDeviation = SIGMA_LON_OFFSET;
        this.lateralStandardDeviation = SIGMA_LAT_OFFSET;
    }

    /**
     * Constructor allowing to configure the standard deviation for the lateral and longitudinal errors.
     *
     * @param rng                           {@link RandomNumberGenerator} to draw gaussian variables from
     * @param longitudinalStandardDeviation sigma for longitudinal error
     * @param lateralStandardDeviation      sigma for lateral error
     */
    public PositionModifier(RandomNumberGenerator rng, double longitudinalStandardDeviation, double lateralStandardDeviation) {
        this.rng = rng;
        this.longitudinalStandardDeviation = longitudinalStandardDeviation;
        this.lateralStandardDeviation = lateralStandardDeviation;
    }

    @Override
    public <T extends SpatialObject<?>> List<T> apply(PerceptionEgo ego, List<T> spatialObjects) {
        Vector3d ownerPosition = ego.getProjectedPosition().toVector3d();
        Vector3d ownerDirection = new Vector3d();
        VectorUtils.getDirectionVectorFromHeading(ego.getHeading(), ownerDirection);
        double angleToNorth = ownerDirection.angle(VectorUtils.NORTH);
        spatialObjects.forEach(
                spatialObject -> {
                    Vector3d relativePosition = getVectorRelativeTo(ownerPosition, spatialObject.getPosition()); // pos relative to owner
                    Vector3d adjustedVector = new Vector3d(relativePosition);
                    adjustedVector.rotate(-angleToNorth, VectorUtils.UP); // rotate vector according to orientation
                    // add lateral and longitudinal errors
                    adjustedVector.set(
                            rng.nextGaussian(adjustedVector.x, lateralStandardDeviation),
                            adjustedVector.y,
                            rng.nextGaussian(adjustedVector.z, longitudinalStandardDeviation)
                    );
                    adjustedVector.rotate(angleToNorth, VectorUtils.UP); // rotate back
                    // move vector back to absolute position and set values
                    spatialObject.setPosition(
                            spatialObject.getPosition().x + (adjustedVector.x - relativePosition.x),
                            spatialObject.getPosition().y,
                            spatialObject.getPosition().z + (adjustedVector.z - relativePosition.z)
                    );
                }
        );

        return spatialObjects;
    }

    private Vector3d getVectorRelativeTo(Vector3d origin, Vector3d relative) {
        return new Vector3d(origin).subtract(relative);
    }
}
