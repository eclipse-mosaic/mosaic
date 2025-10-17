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

package org.eclipse.mosaic.lib.perception.modifier;

import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.math.VectorUtils;
import org.eclipse.mosaic.lib.perception.PerceptionEgo;
import org.eclipse.mosaic.lib.perception.PerceptionModifier;
import org.eclipse.mosaic.lib.perception.objects.SpatialObject;
import org.eclipse.mosaic.lib.perception.objects.SpatialObjectBoundingBox;
import org.eclipse.mosaic.lib.perception.objects.TrafficLightObject;
import org.eclipse.mosaic.lib.spatial.Edge;

import java.util.ArrayList;
import java.util.List;

/**
 * An occlusion modifier for other vehicles configuration is done using {@link #pointsPerSide} and {@link #detectionThreshold}.
 * Example for {@code pointsPerSide = 3} (x's are the corners which will be evaluated anyway, o's are the added points):
 * <pre>
 *     x-----o-----x
 *     |           |
 *     |           |
 *     o           o
 *     |           |
 *     |           |
 *     x-----o-----x
 * </pre>
 */
public class BoundingBoxOcclusion implements PerceptionModifier {

    private final Vector3d intersectionResult = new Vector3d();
    /**
     * This defines how many equidistant points shall be evaluated per edge of a vehicle.
     * Note generally for the front and rear edge this will result in a higher resolution compared to the sides of the vehicle.
     * Default: 2
     */
    private final int pointsPerSide;

    /**
     * Threshold that defines how many of the points defined through {@link #pointsPerSide} need to be visible in order for a
     * object to be treated as detected.
     * Default: 2
     */
    private final int detectionThreshold;

    /**
     * Default constructor for the {@link BoundingBoxOcclusion}.
     * Uses {@link #pointsPerSide} = 2 and {@link #detectionThreshold} = 2 as default values.
     */
    public BoundingBoxOcclusion() {
        this.pointsPerSide = 2;
        this.detectionThreshold = 2;
    }

    /**
     * Constructor for {@link BoundingBoxOcclusion}, validates and sets
     * the parameters {@link #pointsPerSide} and {@link #detectionThreshold}.
     *
     * @param pointsPerSide      the number of points that will be evaluated per object side (corners count towards 2 edges)
     * @param detectionThreshold how many points have to be visible in order for an object to be treated as detected
     * @throws IllegalArgumentException if pointsPerSide or detectionThreshold is configured wrongly
     */
    public BoundingBoxOcclusion(int pointsPerSide, int detectionThreshold) {
        if (pointsPerSide < 2) {
            throw new IllegalArgumentException("Need at least 2 points per edge, meaning every corner will be checked for occlusion.");
        }
        if (detectionThreshold < 1) {
            throw new IllegalArgumentException("At least one point has to be checked for occlusion, else no objects will be occluded");
        }
        if (detectionThreshold > pointsPerSide * 4 - 4) {
            throw new IllegalArgumentException("The detection threshold exceeds the number of points evaluated per object");
        }
        this.pointsPerSide = pointsPerSide;
        this.detectionThreshold = detectionThreshold;
    }

    @Override
    public <T extends SpatialObject<?>> List<T> apply(PerceptionEgo ego, List<T> spatialObjects) {
        List<T> newObjects = new ArrayList<>();
        // the ego object cannot occlude vision
        List<T> occludingObjects = spatialObjects.stream()
                .filter(object -> !object.getId().equals(ego.getId()))
                .toList();
        Vector3d egoPosition = ego.getProjectedPosition().toVector3d();
        for (T objectToEvaluate : spatialObjects) {
            if (objectToEvaluate instanceof TrafficLightObject) { // Traffic Lights are treated to not be occluded
                newObjects.add(objectToEvaluate);
                continue;
            }
            List<Vector3d> pointsToEvaluate = createPointsToEvaluate(objectToEvaluate);
            final int requiredVisiblePoints = pointsToEvaluate.size() == 1 ? 1 : detectionThreshold;
            int numberOfPointsVisible = 0;
            for (Vector3d point : pointsToEvaluate) {
                boolean pointVisible = isVisible(egoPosition, point, objectToEvaluate.getId(), occludingObjects);
                if (pointVisible) { // increment visible counter
                    numberOfPointsVisible++;
                }
                // if the required number of points is visible, we don't need to evaluate more
                if (numberOfPointsVisible == requiredVisiblePoints) {
                    newObjects.add(objectToEvaluate);
                    break;
                }
            }
        }
        return newObjects;
    }

    /**
     * Method to evaluate whether a point is visible by any edge spanned by any other bounding box of any other vehicle.
     *
     * @param egoPosition      position of the ego vehicle
     * @param pointToEvaluate  the point that should be checked for occlusion
     * @param objectId         id that the point belongs to (required for points not to be occluded by the same vehicle)
     * @param occludingObjects all objects that potentially occlude the vehicle
     * @return {@code true} if the point is visible, else {@code false}
     */
    private <T extends SpatialObject<?>> boolean isVisible(Vector3d egoPosition, Vector3d pointToEvaluate,
                                                           String objectId, List<T> occludingObjects) {
        for (T occludingObject : occludingObjects) {
            if (occludingObject.getId().equals(objectId)) {
                continue; // cannot be occluded by itself
            }
            SpatialObjectBoundingBox boundingBox = occludingObject.getBoundingBox();
            // SpatialObjects with PointBoundingBoxes won't occlude anything, as they have no edges defined
            for (Edge<Vector3d> side : boundingBox.getAllEdges()) {
                boolean isOccluded = VectorUtils.computeXZEdgeIntersectionPoint(
                        egoPosition,
                        pointToEvaluate, side.a, side.b, intersectionResult
                );
                if (isOccluded) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Creates a list of all points that shall be tested for occlusion. If {@link #pointsPerSide} is set to a value larger than 2,
     * each side will have additional equidistant points added.
     * Example for {@code pointsPerSide = 3} (x's are the corners which will be evaluated anyway, o's are the added points):
     * <pre>
     *     x-----o-----x
     *     |           |
     *     |           |
     *     o           o
     *     |           |
     *     |           |
     *     x-----o-----x
     * </pre>
     *
     * @param spatialObject a {@link SpatialObject} for which the occlusion should be evaluated
     */
    private <T extends SpatialObject<?>> List<Vector3d> createPointsToEvaluate(T spatialObject) {
        List<Vector3d> pointsToEvaluate = new ArrayList<>();
        SpatialObjectBoundingBox boundingBox = spatialObject.getBoundingBox();
        // if object has edges and more than 2 points per side are to be evaluated, calculate points that have to be evaluated
        if (pointsPerSide > 2 && !boundingBox.getAllEdges().isEmpty()) {
            for (Edge<Vector3d> edge : boundingBox.getAllEdges()) {
                Vector3d start = edge.a;
                if (pointNotPresent(pointsToEvaluate, start)) {
                    pointsToEvaluate.add(start);
                }
                Vector3d end = edge.b;
                if (pointNotPresent(pointsToEvaluate, end)) {
                    pointsToEvaluate.add(end);
                }
                for (int i = 1; i < pointsPerSide - 1; i++) {
                    double ratio = (double) i / (pointsPerSide + 1);
                    double xNew = start.x + ratio * (end.x - start.x);
                    double zNew = start.z + ratio * (end.z - start.z);
                    Vector3d newPoint = new Vector3d(xNew, 0, zNew);
                    if (pointNotPresent(pointsToEvaluate, newPoint)) {
                        pointsToEvaluate.add(newPoint);
                    }
                }
            }
        } else { // else just add all corners
            pointsToEvaluate.addAll(boundingBox.getAllCorners());
        }
        return pointsToEvaluate;
    }

    private boolean pointNotPresent(List<Vector3d> points, Vector3d newPoint) {
        return points.stream().noneMatch(vector3d -> vector3d.isFuzzyEqual(newPoint));
    }
}
