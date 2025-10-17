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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception;

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.application.config.CPerception;
import org.eclipse.mosaic.interactions.traffic.TrafficLightUpdates;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.lib.database.Database;
import org.eclipse.mosaic.lib.geo.CartesianRectangle;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;
import org.eclipse.mosaic.lib.perception.PerceptionIndex;
import org.eclipse.mosaic.lib.routing.VehicleRouting;
import org.eclipse.mosaic.lib.routing.database.DatabaseRouting;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import com.google.common.collect.Iterables;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


/**
 * The {@link CentralPerceptionComponent} is responsible for keeping a spatial index of all vehicles,
 * which allows fast querying of nearby vehicles.
 */
public class CentralPerceptionComponent {

    private final static Logger LOG = LoggerFactory.getLogger(CentralPerceptionComponent.class);

    /**
     * Configuration containing parameters for setting up the spatial indexes.
     */
    private final CPerception configuration;

    /**
     * The spatial index used to store and find objects by their positions.
     */
    private PerceptionIndex perceptionIndex;

    /**
     * The last {@link VehicleUpdates} interaction which is used to update the vehicleIndex.
     */
    private final Map<String, VehicleUpdates> latestVehicleUpdates = new HashMap<>();

    /**
     * The last {@link TrafficLightUpdates} interaction which is used to update the vehicleIndex.
     */
    private TrafficLightUpdates latestTrafficLightUpdates;

    /**
     * If set to true, the vehicle light index will be updated when {@code updateSpatialIndices} is called.
     */
    private boolean updateVehicleIndex = false;

    /**
     * If set to true, the traffic light index will be updated when {@code updateSpatialIndices} is called.
     */
    private boolean updateTrafficLightIndex = false;

    public CentralPerceptionComponent(CPerception perceptionConfiguration) {
        this.configuration = Validate.notNull(perceptionConfiguration, "perceptionConfiguration must not be null");
    }

    /**
     * Initializes the spatial index used for perception.
     *
     * @throws InternalFederateException if perception backend wasn't properly defined
     */
    public void initialize() throws InternalFederateException {
        try {
            VehicleRouting routing = SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting();
            // evaluate bounding box for perception
            final CartesianRectangle scenarioBounds = configuration.perceptionArea == null
                    ? routing.getScenarioBounds()
                    : configuration.perceptionArea.toCartesian();

            PerceptionIndex.Builder indexBuilder = new PerceptionIndex.Builder(LOG, scenarioBounds);
            if (configuration.vehicleIndex != null) {
                indexBuilder.withVehicleIndex(configuration.vehicleIndex.create());
            }
            if (configuration.trafficLightIndex != null) {
                indexBuilder.withTrafficLightIndex(configuration.trafficLightIndex.create());
            }
            if (routing instanceof DatabaseRouting dbRouting) {
                Database database = dbRouting.getScenarioDatabase();
                if (!database.getBuildings().isEmpty() && configuration.wallIndex != null && configuration.wallIndex.enabled) {
                    indexBuilder.withWallIndex(configuration.wallIndex.create(), database);
                }
            }
            perceptionIndex = indexBuilder.build();
        } catch (Exception e) {
            throw new InternalFederateException("Couldn't initialize CentralPerceptionComponent", e);
        }
    }

    /**
     * Returns the {@link PerceptionIndex} storing all perception relevant objects, such
     * as vehicles, traffic lights, or buildings.
     */
    public PerceptionIndex getPerceptionIndex() {
        return perceptionIndex;
    }

    /**
     * Updates the spatial indices (currently only vehicles).
     * If the positions of vehicles have not changed since last call of this method, nothing is done.
     */
    public void updateSpatialIndices() {
        if (updateVehicleIndex) {
            // do not update index until next VehicleUpdates interaction is received
            updateVehicleIndex = false;
            for (VehicleUpdates updates : latestVehicleUpdates.values()) {
                // using Iterables.concat allows iterating over both lists subsequently without creating a new list
                perceptionIndex.updateVehicles(Iterables.concat(updates.getAdded(), updates.getUpdated()));
            }
        }
        if (updateTrafficLightIndex) {
            // do not update index until next TrafficLightUpdates interaction is received
            updateVehicleIndex = false;
            // using Iterables.concat allows iterating over both lists subsequently without creating a new list
            perceptionIndex.updateTrafficLights(latestTrafficLightUpdates.getUpdated());
        }
    }

    /**
     * Registers a vehicle and stores its corresponding vehicle type by name.
     * This is required to extract vehicle dimensions.
     *
     * @param vehicleId   id of the vehicle to register
     * @param vehicleType the vehicle type of the vehicle
     */
    public void registerVehicleType(String vehicleId, VehicleType vehicleType) {
        perceptionIndex.registerVehicleType(vehicleId, vehicleType);
    }

    /**
     * Store new updates of all Vehicles to be used in the next update of the spatial index.
     *
     * @param vehicleUpdates the interaction holding all vehicle updates
     */
    public void updateVehicles(VehicleUpdates vehicleUpdates) {
        latestVehicleUpdates.put(vehicleUpdates.getSenderId(), vehicleUpdates);
        updateVehicleIndex = true;
        // we need to remove arrived vehicles in every simulation step, otherwise we could have dead vehicles in the index
        if (perceptionIndex.getNumberOfVehicles() > 0) {
            perceptionIndex.removeVehicles(vehicleUpdates.getRemovedNames());
        }
    }

    /**
     * Adds traffic lights to the spatial index, as their positions are static it is sufficient
     * to store positional information only once.
     *
     * @param trafficLightGroup the registered traffic light group interaction
     */
    public void addTrafficLightGroup(TrafficLightGroup trafficLightGroup) {
        perceptionIndex.addTrafficLightGroup(trafficLightGroup);
    }

    /**
     * Updates the traffic light index in regard to traffic lights. The unit simulator has to be queried as
     * {@link TrafficLightUpdates} do not contain all necessary information.
     *
     * @param trafficLightUpdates a list of information packages transmitted by the traffic simulator
     */
    public void updateTrafficLights(TrafficLightUpdates trafficLightUpdates) {
        latestTrafficLightUpdates = trafficLightUpdates;
        updateTrafficLightIndex = true;
    }

}
