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

import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.perception.PerceptionEgo;
import org.eclipse.mosaic.lib.perception.PerceptionModifier;
import org.eclipse.mosaic.lib.perception.objects.SpatialObject;

import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * This {@link PerceptionModifier} tries to emulate the behaviour of occlusion.
 * The general approach is to look at the angle of all vehicles that are
 * perceived closer to the current vehicle.
 * If any of those previously perceived vehicles has an angle smaller than
 * defined by {@link #getOcclusionAngle} the current vehicle will not be perceived.
 * <p/>
 * Additionally, a linear function between {@link #minDetectionAngle} and {@link #maxDetectionAngle}
 * is fitted, which makes it necessary for further vehicles to have a larger "free" angle.
 */
public class SimpleOcclusion implements PerceptionModifier {

    /**
     * The "free" angle required by the closest vehicle. [rad]
     */
    private final double minDetectionAngle;

    /**
     * The "free" angle required by the furthest vehicle. [rad]
     */
    private final double maxDetectionAngle;

    /**
     * Constructor for the {@link SimpleOcclusion}.
     *
     * @param minDetectionAngle the "free" angle that will be required by closest vehicles [degree]
     * @param maxDetectionAngle the "free" angle that will be required by furthest vehicles [degree]
     */
    public SimpleOcclusion(double minDetectionAngle, double maxDetectionAngle) {
        Validate.isTrue(minDetectionAngle > 0 && maxDetectionAngle >= minDetectionAngle,
                "Angles have to be larger than 0 and maxDetectionAngle >= minDetectionAngle.");
        this.minDetectionAngle = Math.toRadians(minDetectionAngle);
        this.maxDetectionAngle = Math.toRadians(maxDetectionAngle);
    }

    @Override
    public <T extends SpatialObject<?>> List<T> apply(PerceptionEgo ego, List<T> spatialObjects) {
        if (spatialObjects.isEmpty()) {
            return spatialObjects;
        }
        Vector3d ownerPosition = ego.getProjectedPosition().toVector3d();
        // sort by distances
        List<T> sortedByDistance = new ArrayList<>(spatialObjects);
        sortedByDistance.sort(Comparator.comparingDouble(vehicleObject -> vehicleObject.getPosition().distanceTo(ownerPosition)));
        // fit linear function to (closest distance, min angle) and (furthest distance, max angle)
        double closestPerceivedDistance = ownerPosition.distanceTo(sortedByDistance.get(0).getPosition());
        double furthestPerceivedDistance = ownerPosition.distanceTo(sortedByDistance.get(sortedByDistance.size() - 1).getPosition());
        double m = (maxDetectionAngle - minDetectionAngle) / (furthestPerceivedDistance - closestPerceivedDistance);
        double n = minDetectionAngle - (closestPerceivedDistance * m);

        List<T> notOccludedObjects = new ArrayList<>();
        List<Vector3d> nonOccludedVectors = new ArrayList<>();
        notOccludedObjects.add(sortedByDistance.get(0)); // closest vehicle is always perceived
        nonOccludedVectors.add(getVectorRelativeTo(ownerPosition, sortedByDistance.get(0).getPosition()));
        for (int i = 1; i < sortedByDistance.size(); i++) { // iterate over all other sorted vehicles
            Vector3d currentRelativeVector = getVectorRelativeTo(ownerPosition, sortedByDistance.get(i).getPosition());
            double occlusionAngle = getOcclusionAngle(ownerPosition.distanceTo(sortedByDistance.get(i).getPosition()), m, n);
            isOccluded:
            {
                for (Vector3d otherVector : nonOccludedVectors) { // iterate over all previously selected vectors
                    if (Math.abs(currentRelativeVector.angle(otherVector)) <= occlusionAngle) {
                        break isOccluded;
                    }
                }
                notOccludedObjects.add(sortedByDistance.get(i));
                nonOccludedVectors.add(currentRelativeVector);
            }
        }
        spatialObjects.retainAll(notOccludedObjects); // apply changes on initial list
        return spatialObjects;
    }

    private Vector3d getVectorRelativeTo(Vector3d origin, Vector3d relative) {
        return new Vector3d(origin).subtract(relative);
    }

    /**
     * There can't be any other vehicles closer to the current vehicle,
     * that has an angle smaller than the return value of this function.
     * The distance value is fitted to a linear function: angle(distance) = m*distance+n
     *
     * @param distance the distance of the current vehicle vector
     * @return the required "free" angle
     */
    private double getOcclusionAngle(double distance, double m, double n) {
        return m * distance + n;
    }

}
