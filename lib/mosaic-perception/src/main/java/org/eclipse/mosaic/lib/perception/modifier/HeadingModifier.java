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

import org.eclipse.mosaic.lib.math.MathUtils;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.math.VectorUtils;
import org.eclipse.mosaic.lib.perception.PerceptionEgo;
import org.eclipse.mosaic.lib.perception.PerceptionModifier;
import org.eclipse.mosaic.lib.perception.objects.SpatialObject;
import org.eclipse.mosaic.lib.perception.objects.VehicleObject;

import org.apache.commons.lang3.Validate;

import java.util.List;

/**
 * Adjusts the heading of perceived {@link VehicleObject}s. Since the position
 * of vehicles is assumed to refer to their front bumper and instead bounding box center,
 * their position is adjusted accordingly when the heading of the vehicle was changed.
 */
public class HeadingModifier implements PerceptionModifier {

    /**
     * Default standard deviation for heading error.
     */
    private static final double SIGMA_HEADING_OFFSET = 4; // given in degree

    /**
     * Default chance of the car being perceived heading in the complete other direction (180° error).
     */
    private static final double DEFAULT_CHANCE_WRONG_DIR = 0.01;

    private final RandomNumberGenerator rng;

    /**
     * Standard deviation for heading error.
     */
    private final double headingStandardDeviation;

    private final double chanceOfWrongDirection;

    public HeadingModifier(RandomNumberGenerator rng) {
        this.rng = rng;
        this.headingStandardDeviation = SIGMA_HEADING_OFFSET;
        this.chanceOfWrongDirection = DEFAULT_CHANCE_WRONG_DIR;
    }

    /**
     * Constructor for the {@link HeadingModifier} using configured values.
     *
     * @param rng                      {@link RandomNumberGenerator} for evaluation of probabilities
     * @param headingStandardDeviation the sigma value used for the applied heading offset [0, 360]
     * @param chanceOfWrongDirection   chance for a heading to be rotated by 180° [0, 1]
     */
    public HeadingModifier(RandomNumberGenerator rng, double headingStandardDeviation, double chanceOfWrongDirection) {
        Validate.inclusiveBetween(0, 360, headingStandardDeviation, "Heading deviation should lie between 0 and 360");
        Validate.inclusiveBetween(0, 1, chanceOfWrongDirection, "Wrong direction probability should lie between 0 and 1");

        this.rng = rng;
        this.headingStandardDeviation = headingStandardDeviation;
        this.chanceOfWrongDirection = chanceOfWrongDirection;
    }

    @Override
    public <T extends SpatialObject<?>> List<T> apply(PerceptionEgo ego, List<T> spatialObjects) {
        spatialObjects.stream()
                .filter(o -> o instanceof VehicleObject)
                .forEach(o -> adjustHeadingOfVehicle((VehicleObject) o));

        return spatialObjects;
    }

    private void adjustHeadingOfVehicle(VehicleObject vehicleObject) {
        double oldHeading = vehicleObject.getHeading();

        if (rng.nextDouble() < chanceOfWrongDirection) {
            vehicleObject.setHeading((vehicleObject.getHeading() + 180) % 360);
        }
        vehicleObject.setHeading(rng.nextGaussian(vehicleObject.getHeading(), headingStandardDeviation) % 360);

        double newHeading = vehicleObject.getHeading();

        if (MathUtils.isFuzzyEqual(oldHeading, newHeading)) {
            return;
        }

        // move position of vehicle based on heading diff since vehicle position is assumed to refer to front bumper and we want to
        // rotate around bounding box center
        Vector3d oldHeadingVector = VectorUtils.getDirectionVectorFromHeading(oldHeading, new Vector3d());
        Vector3d newHeadingVector = VectorUtils.getDirectionVectorFromHeading(newHeading, new Vector3d());

        Vector3d newPosition = new Vector3d(vehicleObject.getPosition())
                .subtract(oldHeadingVector.multiply(vehicleObject.getLength() / 2))
                .add(newHeadingVector.multiply(vehicleObject.getLength() / 2));
        vehicleObject.setPosition(newPosition.x, newPosition.y, newPosition.z);
    }
}
