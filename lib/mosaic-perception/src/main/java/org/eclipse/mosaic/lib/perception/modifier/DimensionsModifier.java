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

import java.util.List;

/**
 * Adjusts the dimensions of perceived {@link VehicleObject}s. Since the position
 * of vehicles is assumed to refer to their front bumper instead of bounding box center,
 * their position is adjusted accordingly when the length of the vehicle was changed.
 */
public class DimensionsModifier implements PerceptionModifier {

    private static final double SIGMA_WIDTH_OFFSET = 0.2; // given in m
    private static final double SIGMA_HEIGHT_OFFSET = 0.2; // given in m
    private static final double SIGMA_LENGTH_OFFSET = 0.5; // given in m

    private final double heightDeviation;
    private final double widthDeviation;
    private final double lengthDeviation;

    private final RandomNumberGenerator rng;


    /**
     * Constructor for {@link DimensionsModifier} given the sigma values for the gaussian distribution applied to different dimensions.
     *
     * @param rng             {@link RandomNumberGenerator} used to generate pseudo-random gaussian numbers
     * @param lengthDeviation sigma value for the length
     * @param widthDeviation  sigma value for the width
     * @param heightDeviation sigma value for the height
     */
    public DimensionsModifier(RandomNumberGenerator rng, double lengthDeviation, double widthDeviation, double heightDeviation) {
        this.rng = rng;
        this.lengthDeviation = lengthDeviation;
        this.widthDeviation = widthDeviation;
        this.heightDeviation = heightDeviation;
    }

    /**
     * Constructor for {@link DimensionsModifier} using default values of 0.2 for the sigma value of the dimensions
     *
     * @param rng @link RandomNumberGenerator} used to generate pseudo-random gaussian numbers
     */
    public DimensionsModifier(RandomNumberGenerator rng) {
        this.rng = rng;
        this.lengthDeviation = SIGMA_LENGTH_OFFSET;
        this.widthDeviation = SIGMA_WIDTH_OFFSET;
        this.heightDeviation = SIGMA_HEIGHT_OFFSET;
    }

    @Override
    public <T extends SpatialObject<?>> List<T> apply(PerceptionEgo ego, List<T> spatialObjects) {
        spatialObjects.stream()
                .filter(o -> o instanceof VehicleObject)
                .forEach(o -> adjustDimensionsOfVehicle((VehicleObject) o));
        return spatialObjects;
    }

    private void adjustDimensionsOfVehicle(VehicleObject vehicleObject) {

        double oldLength = vehicleObject.getLength();

        vehicleObject.setDimensions(
                Math.abs(rng.nextGaussian(vehicleObject.getLength(), lengthDeviation)),
                Math.abs(rng.nextGaussian(vehicleObject.getWidth(), widthDeviation)),
                Math.abs(rng.nextGaussian(vehicleObject.getHeight(), heightDeviation))
        );

        double newLength = vehicleObject.getLength();

        if (MathUtils.isFuzzyEqual(newLength, oldLength)) {
            return;
        }

        // move position of vehicle based on length difference since vehicle position is assumed to refer to front bumper and we want to
        // squeeze the length around bounding box center
        Vector3d direction = VectorUtils.getDirectionVectorFromHeading(vehicleObject.getHeading(), new Vector3d())
                .multiply((newLength - oldLength) / 2);
        vehicleObject.setPosition(
                vehicleObject.getPosition().x + direction.x,
                vehicleObject.getPosition().y + direction.y,
                vehicleObject.getPosition().z + direction.z
        );
    }

}

