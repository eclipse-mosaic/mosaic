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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index;

import org.eclipse.mosaic.fed.application.ambassador.simulation.VehicleUnit;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.NopPerceptionModule;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.PerceptionModel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.SimplePerceptionConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.TrafficLightObject;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.providers.TrafficLightIndex;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.providers.VehicleIndex;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.providers.WallIndex;
import org.eclipse.mosaic.fed.application.app.api.perception.PerceptionModule;
import org.eclipse.mosaic.lib.database.Database;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroupInfo;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;
import org.eclipse.mosaic.lib.spatial.Edge;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A {@link TrafficObjectIndex} is a representation of space using a special data structure.
 * The goal is to allow for efficient querying of nearby entities.
 */
public class TrafficObjectIndex {
    private final Logger log;
    private final VehicleIndex vehicleIndex;
    private final TrafficLightIndex trafficLightIndex;
    private final WallIndex wallIndex;

    private TrafficObjectIndex(Logger log, VehicleIndex vehicleIndex, TrafficLightIndex trafficLightIndex, WallIndex wallIndex) {
        this.log = log;
        this.vehicleIndex = vehicleIndex;
        this.trafficLightIndex = trafficLightIndex;
        this.wallIndex = wallIndex;
        if (vehicleIndexConfigured()) {
            this.vehicleIndex.initialize();
        }
        if (trafficLightIndexConfigured()) {
            this.trafficLightIndex.initialize();
        }
        if (wallIndexConfigured()) {
            this.wallIndex.initialize();
        }
    }

    private boolean vehicleIndexConfigured() {
        return vehicleIndex != null;
    }

    private boolean trafficLightIndexConfigured() {
        return trafficLightIndex != null;
    }

    private boolean wallIndexConfigured() {
        return wallIndex != null;
    }

    /**
     * Queries the {@link TrafficObjectIndex} and returns all vehicles inside the {@link PerceptionModel}.
     */
    public List<VehicleObject> getVehiclesInRange(PerceptionModel perceptionModel) {
        if (!vehicleIndexConfigured()) {
            log.debug("No Traffic Light Index Provider configured. No Vehicles will be in range.");
            return new ArrayList<>();
        }
        return vehicleIndex.getVehiclesInRange(perceptionModel);
    }

    /**
     * Registers a vehicle and stores its corresponding vehicle type by name.
     * This is required to extract vehicle dimensions.
     *
     * @param vehicleId   id of the vehicle to register
     * @param vehicleType the vehicle type of the vehicle
     */
    public void registerVehicleType(String vehicleId, VehicleType vehicleType) {
        if (!vehicleIndexConfigured()) {
            log.debug("No Vehicle Index Provider configured. Vehicle Type won't be registered.");
            return;
        }
        vehicleIndex.registerVehicleType(vehicleId, vehicleType);
    }

    /**
     * Remove all vehicles from the {@link TrafficObjectIndex} by a list of vehicle ids.
     *
     * @param vehiclesToRemove the list of vehicles to remove from the index
     */
    public void removeVehicles(Iterable<String> vehiclesToRemove) {
        if (!vehicleIndexConfigured()) {
            log.debug("No Vehicle Index Provider configured. No Vehicle will be removed.");
            return;
        }
        vehicleIndex.removeVehicles(vehiclesToRemove);
    }

    /**
     * Updates the {@link TrafficObjectIndex} with a list of {@link VehicleData} objects.
     *
     * @param vehiclesToUpdate the list of vehicles to add or update in the index
     */
    public void updateVehicles(Iterable<VehicleData> vehiclesToUpdate) {
        if (!vehicleIndexConfigured()) {
            log.debug("No Vehicle Index Provider configured. Index won't be updated.");
            return;
        }
        vehicleIndex.updateVehicles(vehiclesToUpdate);
    }

    /**
     * Returns the number of indexed vehicles.
     *
     * @return the number of vehicles
     */
    public int getNumberOfVehicles() {
        if (!vehicleIndexConfigured()) {
            log.debug("No Vehicle Index Provider configured. There are no indexed Vehicles.");
            return 0;
        }
        return vehicleIndex.getNumberOfVehicles();
    }

    /**
     * Queries the {@link TrafficObjectIndex} and returns all traffic lights inside the {@link PerceptionModel}.
     */
    public List<TrafficLightObject> getTrafficLightsInRange(PerceptionModel perceptionModel) {
        if (!trafficLightIndexConfigured()) {
            log.debug("No Traffic Light Index Provider configured. No Traffic Lights will be in range.");
            return new ArrayList<>();
        }
        return trafficLightIndex.getTrafficLightsInRange(perceptionModel);
    }

    /**
     * Adds traffic lights to the spatial index, as their positions are static,
     * it is enough to store positional information only once.
     *
     * @param trafficLightGroup the registered traffic light group
     */
    public void addTrafficLightGroup(TrafficLightGroup trafficLightGroup) {
        if (!trafficLightIndexConfigured()) {
            log.debug("No Traffic Light Index Provider configured. Traffic Light won't be added.");
            return;
        }
        trafficLightIndex.addTrafficLight(trafficLightGroup);
    }

    /**
     * Updates the {@link TrafficObjectIndex} in regard to traffic lights. The unit simulator has to be queried as
     * {@code TrafficLightUpdates} do not contain all necessary information.
     *
     * @param trafficLightsToUpdate a list of information packages transmitted by the traffic simulator
     */
    public void updateTrafficLights(Map<String, TrafficLightGroupInfo> trafficLightsToUpdate) {
        if (!trafficLightIndexConfigured()) {
            log.debug("No Traffic Light Index Provider configured. Index won't be updated.");
            return;
        }
        trafficLightIndex.updateTrafficLights(trafficLightsToUpdate);

    }

    public Collection<Edge<Vector3d>> getSurroundingWalls(PerceptionModel perceptionModel) {
        if (!wallIndexConfigured()) {
            log.debug("No Wall Index defined.");
            return new ArrayList<>();
        }
        return wallIndex.getSurroundingWalls(perceptionModel);
    }

    /**
     * Creates the perception module from the configured {@link VehicleIndex}.
     *
     * @param unit     unit to create the perception module for
     * @param database the network database used as a backend for the road network
     * @param osLog    log of the operating system
     * @return the created {@link PerceptionModule}
     */
    public PerceptionModule<SimplePerceptionConfiguration> createPerceptionModule(VehicleUnit unit, Database database, Logger osLog) {
        if (!vehicleIndexConfigured()) {
            return new NopPerceptionModule(unit, database, osLog);
        }
        return vehicleIndex.createPerceptionModule(unit, database, osLog);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static class Builder {
        private final Logger log;
        private VehicleIndex vehicleIndex = null;
        private TrafficLightIndex trafficLightIndex = null;
        private WallIndex wallIndex = null;

        public Builder(Logger log) {
            this.log = log;
        }

        public Builder withVehicleIndex(VehicleIndex vehicleIndex) {
            this.vehicleIndex = vehicleIndex;
            return this;
        }

        public Builder withTrafficLightIndex(TrafficLightIndex trafficLightIndex) {
            this.trafficLightIndex = trafficLightIndex;
            return this;
        }

        public Builder withWallIndex(WallIndex wallIndex, Database database) {
            this.wallIndex = wallIndex;
            this.wallIndex.setDatabase(database);
            return this;
        }

        public TrafficObjectIndex build() {
            return new TrafficObjectIndex(log, vehicleIndex, trafficLightIndex, wallIndex);
        }
    }
}