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
import org.eclipse.mosaic.lib.math.VectorUtils;
import org.eclipse.mosaic.lib.perception.PerceptionEgo;
import org.eclipse.mosaic.lib.perception.PerceptionModifier;
import org.eclipse.mosaic.lib.perception.index.WallIndex;
import org.eclipse.mosaic.lib.perception.objects.BuildingWall;
import org.eclipse.mosaic.lib.perception.objects.SpatialObject;
import org.eclipse.mosaic.lib.spatial.Edge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A simple perception modifier which removes all vehicles behind walls.
 * A list of walls in the vicinity of the ego vehicle is provided by the
 * {@link WallIndex}.
 * The check for hidden vehicles is done by finding intersection of vectors between
 * ego and all other vehicles and all walls in its vicinity.
 */
public class WallOcclusion implements PerceptionModifier {

    @Override
    public <T extends SpatialObject<?>> List<T> apply(PerceptionEgo ego, List<T> spatialObjects) {
        if (spatialObjects.isEmpty()) {
            return spatialObjects;
        }

        final Collection<BuildingWall> walls = ego.getSurroundingWalls();
        if (walls.isEmpty()) {
            return spatialObjects;
        }
        Vector3d ownerPosition = ego.getProjectedPosition().toVector3d();
        final List<T> result = new ArrayList<>();
        for (T spatialObject : spatialObjects) {
            List<Vector3d> pointsToEvaluate = spatialObject.getBoundingBox().getAllCorners();
            // we say that at least half of the corners have to be visible rounding up for odd numbers
            final int requiredVisiblePoints = (int) Math.ceil((double) pointsToEvaluate.size() / 2);
            int numberOfPointsVisible = 0;
            for (Vector3d point : pointsToEvaluate) {
                boolean pointOccluded = false;
                for (Edge<Vector3d> wall : walls) {
                    // SpatialObjects with PointBoundingBoxes won't occlude anything, as they have no edges defined
                    if (VectorUtils.doesXZIntersect(ownerPosition, point, wall.a, wall.b)) {
                        pointOccluded = true;
                        break;
                    }
                }
                if (!pointOccluded) {
                    numberOfPointsVisible++;
                }
                if (numberOfPointsVisible == requiredVisiblePoints) {
                    result.add(spatialObject);
                    break;
                }
            }
        }
        return result;
    }
}
