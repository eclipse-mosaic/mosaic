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

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.CartesianRectangle;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;
import org.eclipse.mosaic.lib.perception.PerceptionModel;
import org.eclipse.mosaic.lib.perception.PerceptionIndex;
import org.eclipse.mosaic.lib.perception.objects.VehicleObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class VehicleIndex {

    /**
     * Stores {@link VehicleObject}s for fast removal and position update.
     */
    final Map<String, VehicleObject> indexedVehicles = new HashMap<>();

    /**
     * Stores the types of vehicles to extract vehicle dimensions.
     */
    private final Map<String, VehicleType> registeredVehicleTypes = new HashMap<>();

    protected CartesianRectangle bounds;

    /**
     * Method called to initialize index after configuration has been read.
     */
    public void initialize(CartesianRectangle bounds) {
        this.bounds = bounds;
        initialize();
    }

    protected abstract void initialize();

    /**
     * Adds a vehicle to the index if it hasn't been added to keep track of its dimensions.
     *
     * @param vehicleData the data containing information about the vehicle
     */
    VehicleObject addOrGetVehicle(VehicleData vehicleData) {
        String vehicleId = vehicleData.getName();
        VehicleObject vehicleObject = indexedVehicles.get(vehicleId);
        if (vehicleObject == null) {
            vehicleObject = new VehicleObject(vehicleId)
                    .setHeading(vehicleData.getHeading())
                    .setSpeed(vehicleData.getSpeed())
                    .setPosition(vehicleData.getProjectedPosition());
            VehicleType vehicleType = registeredVehicleTypes.get(vehicleId);
            if (vehicleType != null) { // vehicles with no cached type will have (0,0,0) dimensions
                vehicleObject.setDimensions(
                        vehicleType.getLength(),
                        vehicleType.getWidth(),
                        vehicleType.getHeight()
                );
            }
            indexedVehicles.put(vehicleId, vehicleObject);
            onVehicleAdded(vehicleObject);
        }
        return vehicleObject;
    }

    /**
     * Returns the number of indexed vehicles.
     *
     * @return the number of vehicles
     */
    public int getNumberOfVehicles() {
        return indexedVehicles.size();
    }

    /**
     * Queries the {@link PerceptionIndex} and returns all vehicles inside the {@link PerceptionModel}.
     */
    public abstract List<VehicleObject> getVehiclesInRange(PerceptionModel perceptionModel);

    /**
     * Abstract method to be implemented by vehicle indexes.
     * Shall include functionality to add a vehicle object to the specific index.
     *
     * @param vehicleObject the vehicle to be added
     */
    protected abstract void onVehicleAdded(VehicleObject vehicleObject);

    /**
     * Abstract method to be implemented by vehicle indexes.
     * Updates the specific data type implemented by the index.
     */
    protected abstract void onIndexUpdate();

    /**
     * Abstract method to be implemented by vehicle indexes.
     * Shall include functionality to remove a vehicle object from the specific index.
     *
     * @param vehicleObject the vehicle to be removed
     */
    protected abstract void onVehicleRemoved(VehicleObject vehicleObject);

    /**
     * Registers a vehicle and stores its corresponding vehicle type by name.
     * This is required to extract vehicle dimensions.
     *
     * @param vehicleId   id of the vehicle to register
     * @param vehicleType the vehicle type of the vehicle
     */
    public void registerVehicleType(String vehicleId, VehicleType vehicleType) {
        registeredVehicleTypes.put(vehicleId, vehicleType);
    }

    /**
     * Remove all vehicles from the {@link VehicleIndex} by a list of vehicle ids.
     * When vehicles are removed from simulation we can remove the cached {@link VehicleType}.
     *
     * @param vehiclesToRemove the list of vehicles to remove from the index
     */
    public void removeVehicles(Iterable<String> vehiclesToRemove) {
        vehiclesToRemove.forEach(v -> {
            VehicleObject vehicleObject = indexedVehicles.remove(v);
            if (vehicleObject != null) {
                onVehicleRemoved(vehicleObject);
            }
            registeredVehicleTypes.remove(v);
        });
    }

    /**
     * Updates the {@link PerceptionIndex} with a list of {@link VehicleData} objects.
     *
     * @param vehiclesToUpdate the list of vehicles to add or update in the index
     */
    public void updateVehicles(Iterable<VehicleData> vehiclesToUpdate) {
        vehiclesToUpdate.forEach(v -> {
            CartesianPoint vehiclePosition = v.getProjectedPosition();
            if (bounds.contains(vehiclePosition)) {
                VehicleObject vehicleObject = addOrGetVehicle(v)
                        .setHeading(v.getHeading())
                        .setSpeed(v.getSpeed())
                        .setPosition(vehiclePosition);
                if (v.getRoadPosition() != null) {
                    vehicleObject.setEdgeAndLane(v.getRoadPosition().getConnectionId(), v.getRoadPosition().getLaneIndex());
                }
            } else { // if not inside perception bounding area
                VehicleObject vehicleObject = indexedVehicles.remove(v.getName());
                if (vehicleObject != null) {
                    // remove vehicle from index but keep cached vehicle type, as vehicle could re-enter perception bounding area
                    onVehicleRemoved(vehicleObject);
                }
            }

        });
        onIndexUpdate();
    }
}
