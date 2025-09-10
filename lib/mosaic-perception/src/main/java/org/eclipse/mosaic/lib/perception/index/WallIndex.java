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

package org.eclipse.mosaic.lib.perception.index;

import org.eclipse.mosaic.lib.database.Database;
import org.eclipse.mosaic.lib.database.building.Building;
import org.eclipse.mosaic.lib.database.building.Wall;
import org.eclipse.mosaic.lib.database.spatial.Edge;
import org.eclipse.mosaic.lib.perception.PerceptionModel;
import org.eclipse.mosaic.lib.perception.objects.BuildingWall;
import org.eclipse.mosaic.lib.spatial.KdTree;
import org.eclipse.mosaic.lib.spatial.SpatialItemAdapter;
import org.eclipse.mosaic.lib.spatial.SpatialTreeTraverser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WallIndex {

    private final int bucketSize;

    /**
     * The longest wall, relevant for tree setup, so that all walls will be included. [m]
     */
    private double maxWallLength = 50;
    private KdTree<BuildingWall> wallTree;
    private SpatialTreeTraverser.InRadius<BuildingWall> wallTraverser;

    public WallIndex(int bucketSize) {
        this.bucketSize = bucketSize;
    }

    public void initialize(Database database) {
        List<BuildingWall> walls = new ArrayList<>();
        double maxWallLength = 0;
        for (Building building : database.getBuildings()) {
            for (Wall wall : building.getWalls()) {
                walls.add(new BuildingWall(
                        wall.getFromCorner().getCartesianPosition().toVector3d(),
                        wall.getToCorner().getCartesianPosition().toVector3d()
                ));
                if (wall.getLength() > maxWallLength) {
                    maxWallLength = wall.getLength();
                }
            }
        }
        this.maxWallLength = maxWallLength;
        wallTree = new KdTree<>(new SpatialItemAdapter.EdgeAdapter<>(), walls, bucketSize);
        wallTraverser = new Edge.InRadius<>();
    }

    public Collection<BuildingWall> getSurroundingWalls(PerceptionModel perceptionModel) {
        // overestimating the initial list of walls by extending max bounding box radius with maximal wall length
        wallTraverser.setup(perceptionModel.getBoundingBox().center,
                perceptionModel.getBoundingBox().center.distanceTo(perceptionModel.getBoundingBox().min) + maxWallLength);
        wallTraverser.traverse(wallTree);
        return wallTraverser.getResult();
    }
}
