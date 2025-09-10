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
import org.eclipse.mosaic.lib.perception.PerceptionEgo;
import org.eclipse.mosaic.lib.perception.PerceptionModifier;
import org.eclipse.mosaic.lib.perception.objects.SpatialObject;

import org.apache.commons.lang3.Validate;

import java.util.List;

/**
 * This implementation of {@link PerceptionModifier} is a stochastic filter, that works on the
 * principle that closer vehicles are more likely to be perceived than further vehicles.
 * This is done by giving perceived objects ratings relating to the distance of the ego vehicle.
 * We give the closest vehicle the best rating (0) and the furthest vehicle the worst (1).
 * Additionally, we use square distances for comparisons based on the inverse-square law,
 * giving closer vehicles proportionally better scores.
 * This modifier can also be configured using {@link #offset}, which allows adjusting the
 * stochastic component to allow for more or less perceptions.
 */
public class DistanceFilter implements PerceptionModifier {

    private final RandomNumberGenerator rng;

    /**
     * This offset is added to the stochastic part of this modifier.
     * A value of 1 will cause all vehicles to be perceived, and a value -1 none. [-1, 1]
     */
    private final double offset;

    public DistanceFilter(RandomNumberGenerator rng, double offset) {
        Validate.isTrue(offset >= -1 && offset <= 1, "The offset has to be between -1 and 1.");
        this.rng = rng;
        this.offset = offset;
    }

    @Override
    public <T extends SpatialObject<?>> List<T> apply(PerceptionEgo ego, List<T> spatialObjects) {
        if (spatialObjects.isEmpty()) {
            return spatialObjects;
        }
        Vector3d ownerPosition = ego.getProjectedPosition().toVector3d();
        double furthestPerceivedDistance = Math.pow(ego.getViewingRange(), 2);
        spatialObjects.removeIf(currentSpatialObject ->
                getDistanceRating(ownerPosition.distanceSqrTo(currentSpatialObject.getPosition()), furthestPerceivedDistance)
                        >= rng.nextDouble(0, 1) - offset);
        return spatialObjects;
    }

    /**
     * Gives the best rating (0) to the closest vehicles and the worst to the furthest (1).
     *
     * @param distance         distance of the current vehicle to the ego vehicle
     * @param furthestDistance the viewing distance or distance of the furthest vehicle
     * @return a rating for the current vehicle depended on distance to the ego vehicle
     */
    private double getDistanceRating(double distance, double furthestDistance) {
        return distance / furthestDistance;
    }
}
