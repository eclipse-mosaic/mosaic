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
import org.eclipse.mosaic.lib.spatial.Grid;

import java.util.List;

public class VehicleGrid extends VehicleIndex {

    private final double cellWidth;

    private final double cellHeight;

    /**
     * The Grid to be used for spatial search of {@link VehicleObject}s.
     */
    private Grid<VehicleObject> vehicleGrid;

    public VehicleGrid(double cellWidth, double cellHeight) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
    }

    /**
     * Configures a grid as a spatial index for vehicles.
     */
    @Override
    public void initialize() {
        BoundingBox boundingArea = new BoundingBox();
        boundingArea.add(bounds.getA().toVector3d(), bounds.getB().toVector3d());
        vehicleGrid = new Grid<>(new SpatialObjectAdapter<>(), cellWidth, cellHeight, boundingArea);

    }

    @Override
    public List<VehicleObject> getVehiclesInRange(PerceptionModel searchRange) {
        return vehicleGrid.getItemsInBoundingArea(searchRange.getBoundingBox(), searchRange::isInRange);
    }

    @Override
    protected void onVehicleAdded(VehicleObject vehicleObject) {
        vehicleGrid.addItem(vehicleObject);
    }

    @Override
    protected void onIndexUpdate() {
        vehicleGrid.updateGrid();
    }

    @Override
    protected void onVehicleRemoved(VehicleObject vehicleObject) {
        vehicleGrid.removeItem(vehicleObject);
    }
}
