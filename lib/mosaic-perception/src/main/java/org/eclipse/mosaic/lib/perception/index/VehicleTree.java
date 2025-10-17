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

package org.eclipse.mosaic.lib.perception.index;

import org.eclipse.mosaic.lib.perception.PerceptionModel;
import org.eclipse.mosaic.lib.perception.objects.SpatialObjectAdapter;
import org.eclipse.mosaic.lib.perception.objects.VehicleObject;
import org.eclipse.mosaic.lib.spatial.BoundingBox;
import org.eclipse.mosaic.lib.spatial.QuadTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Quad-tree based implementation of a {@link VehicleIndex}.
 */
public class VehicleTree extends VehicleIndex {
    /**
     * The maximum amount of vehicles in one leaf before it gets split into four sub-leaves.
     */
    private final int splitSize;

    /**
     * Maximum depth of the quad tree.
     */
    private final int maxDepth;

    /**
     * The Quad-Tree to be used for spatial search of {@link VehicleObject}s.
     */
    private QuadTree<VehicleObject> vehicleTree;

    public VehicleTree(int splitSize, int maxDepth) {
        this.splitSize = splitSize;
        this.maxDepth = maxDepth;
    }

    /**
     * Configures a QuadTree as spatial index for vehicles on first use.
     */
    @Override
    public void initialize() {
        QuadTree.configure(splitSize, splitSize / 2, maxDepth);
        BoundingBox boundingArea = new BoundingBox();
        boundingArea.add(bounds.getA().toVector3d(), bounds.getB().toVector3d());
        vehicleTree = new QuadTree<>(new SpatialObjectAdapter<>(), boundingArea);
    }

    @Override
    public List<VehicleObject> getVehiclesInRange(PerceptionModel searchRange) {
        return vehicleTree.getObjectsInBoundingArea(searchRange.getBoundingBox(), searchRange::isInRange, new ArrayList<>());
    }

    @Override
    protected void onVehicleAdded(VehicleObject vehicleObject) {
        vehicleTree.addItem(vehicleObject);
    }

    @Override
    protected void onIndexUpdate() {
        vehicleTree.updateTree();
    }

    @Override
    protected void onVehicleRemoved(VehicleObject vehicleObject) {
        vehicleTree.removeObject(vehicleObject);
    }
}
