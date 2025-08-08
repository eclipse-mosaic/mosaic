/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.mapping.ambassador;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import org.eclipse.mosaic.fed.mapping.ambassador.spawning.AgentSpawner;
import org.eclipse.mosaic.fed.mapping.ambassador.spawning.ChargingStationSpawner;
import org.eclipse.mosaic.fed.mapping.ambassador.spawning.RoadSideUnitSpawner;
import org.eclipse.mosaic.fed.mapping.ambassador.spawning.ServerSpawner;
import org.eclipse.mosaic.fed.mapping.ambassador.spawning.StationaryUnitSpawner;
import org.eclipse.mosaic.fed.mapping.ambassador.spawning.TrafficLightSpawner;
import org.eclipse.mosaic.fed.mapping.ambassador.spawning.TrafficManagementCenterSpawner;
import org.eclipse.mosaic.fed.mapping.ambassador.weighting.StochasticSelector;
import org.eclipse.mosaic.fed.mapping.ambassador.weighting.WeightedSelector;
import org.eclipse.mosaic.fed.mapping.config.CMappingAmbassador;
import org.eclipse.mosaic.fed.mapping.config.CMappingConfiguration;
import org.eclipse.mosaic.fed.mapping.config.CPrototype;
import org.eclipse.mosaic.fed.mapping.config.units.CAgent;
import org.eclipse.mosaic.fed.mapping.config.units.CChargingStation;
import org.eclipse.mosaic.fed.mapping.config.units.COriginDestinationMatrixMapper;
import org.eclipse.mosaic.fed.mapping.config.units.CRoadSideUnit;
import org.eclipse.mosaic.fed.mapping.config.units.CServer;
import org.eclipse.mosaic.fed.mapping.config.units.CTrafficLight;
import org.eclipse.mosaic.fed.mapping.config.units.CTrafficManagementCenter;
import org.eclipse.mosaic.fed.mapping.config.units.CVehicle;
import org.eclipse.mosaic.interactions.mapping.TrafficLightRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioTrafficLightRegistration;
import org.eclipse.mosaic.interactions.traffic.VehicleTypesInitialization;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.objects.UnitNameGenerator;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.RtiAmbassador;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Framework doing the actual work.
 */
public class SpawningFramework {

    private static final Logger LOG = LoggerFactory.getLogger(SpawningFramework.class);

    private final List<CPrototype> prototypeConfigurations = new ArrayList<>();
    private final Map<String, List<CPrototype>> typeDistributions = new HashMap<>();
    private final Map<String, TrafficLightSpawner> tls = new HashMap<>();
    private final List<VehicleFlowGenerator> vehicleFlowGenerators = new ArrayList<>();
    private final List<AgentSpawner> agentSpawners = new ArrayList<>();
    private final List<RoadSideUnitSpawner> rsus = new ArrayList<>();
    private final List<TrafficManagementCenterSpawner> tmcs = new ArrayList<>();
    private final List<ServerSpawner> servers = new ArrayList<>();
    private final List<ChargingStationSpawner> chargingStations = new ArrayList<>();
    private final List<StationaryUnitSpawner> stationarySpawners = new ArrayList<>();
    private final CMappingConfiguration config;

    private ScenarioTrafficLightRegistration scenarioTrafficLightRegistration;

    /**
     * The simulation time. Unit: [ns].
     */
    private long time = 0;
    /**
     * The ambassador of the runtime infrastructure.
     */
    private RtiAmbassador rti;
    /**
     * Whether mapping already was initialized. We use it in timeAdvance method
     * for initializing static objects such as RSUs, TMCs and charging stations.
     */
    private boolean immobileUnitsInitialized = false;
    /**
     * Flag used to indicate if traffic lights have been initialized
     * (relevant for external scenarios).
     */
    private boolean trafficLightsInitialized = false;

    /**
     * Constructor for {@link SpawningFramework}.
     *
     * @param mappingConfiguration             the configuration set through json
     * @param scenarioTrafficLightRegistration used for traffic light mapping if an external scenario is used
     * @param rti                              the runtime infrastructure ambassador
     * @param rng                              {@link RandomNumberGenerator} used for example for flow noise of vehicle spawners
     */
    public SpawningFramework(CMappingAmbassador mappingConfiguration,
                             ScenarioTrafficLightRegistration scenarioTrafficLightRegistration,
                             RtiAmbassador rti,
                             RandomNumberGenerator rng) {
        this.scenarioTrafficLightRegistration = scenarioTrafficLightRegistration;
        this.rti = rti;
        // config refers to meta parameters like start-/end-time, flow noise etc.
        this.config = mappingConfiguration.config;

        // Prototypes
        if (mappingConfiguration.prototypes != null) {
            for (CPrototype prototypeConfiguration : mappingConfiguration.prototypes) {
                if (prototypeConfiguration != null) {
                    prototypeConfigurations.add(prototypeConfiguration);
                }
            }
        }
        // Traffic lights
        if (mappingConfiguration.trafficLights != null) {
            int i = 0;
            for (CTrafficLight trafficLightConfigurations : mappingConfiguration.trafficLights) {
                if (trafficLightConfigurations != null) {
                    TrafficLightSpawner tl = new TrafficLightSpawner(trafficLightConfigurations);
                    tls.put(ObjectUtils.defaultIfNull(tl.getTlName(), Integer.toString(++i)), tl);
                }
            }
        }

        // Servers
        if (mappingConfiguration.servers != null) {
            for (CServer serverConfiguration : mappingConfiguration.servers) {
                if (serverConfiguration != null) {
                    ServerSpawner serverSpawner = new ServerSpawner(serverConfiguration);
                    servers.add(serverSpawner);
                    stationarySpawners.add(serverSpawner);
                }
            }
        }
        // TMCs
        if (mappingConfiguration.tmcs != null) {
            for (CTrafficManagementCenter trafficManagementCenterConfiguration : mappingConfiguration.tmcs) {
                if (trafficManagementCenterConfiguration != null) {
                    TrafficManagementCenterSpawner trafficManagementCenterSpawner =
                            new TrafficManagementCenterSpawner(trafficManagementCenterConfiguration);
                    tmcs.add(trafficManagementCenterSpawner);
                    stationarySpawners.add(trafficManagementCenterSpawner);
                }
            }
        }
        // RSUs
        if (mappingConfiguration.rsus != null) {
            for (CRoadSideUnit roadSideUnitConfiguration : mappingConfiguration.rsus) {
                if (roadSideUnitConfiguration != null) {
                    RoadSideUnitSpawner roadSideUnitSpawner = new RoadSideUnitSpawner(roadSideUnitConfiguration);
                    rsus.add(roadSideUnitSpawner);
                    stationarySpawners.add(roadSideUnitSpawner);
                }
            }
        }
        // Charging stations
        if (mappingConfiguration.chargingStations != null) {
            for (CChargingStation chargingStationConfiguration : mappingConfiguration.chargingStations) {
                if (chargingStationConfiguration != null) {
                    ChargingStationSpawner chargingStationSpawner =
                            new ChargingStationSpawner(chargingStationConfiguration);
                    chargingStations.add(chargingStationSpawner);
                    stationarySpawners.add(chargingStationSpawner);
                }
            }
        }

        // Store type-distributions
        if (mappingConfiguration.typeDistributions != null) {
            typeDistributions.putAll(mappingConfiguration.typeDistributions);
        }

        // randomize weights in type-distributions
        if (mappingConfiguration.config != null && mappingConfiguration.config.randomizeWeights) {
            for (List<CPrototype> prototypes : typeDistributions.values()) {
                randomizeWeights(rng, prototypes);
            }
        }

        // Vehicle Spawners
        boolean spawnersExist = false;
        if (mappingConfiguration.vehicles != null) {
            spawnersExist = generateVehicleFlows(mappingConfiguration.vehicles, mappingConfiguration.config, rng);
        }

        // Agent spawners
        if (mappingConfiguration.agents != null) {
            for (CAgent agentConfiguration : mappingConfiguration.agents) {
                if (agentConfiguration != null) {
                    agentSpawners.add(new AgentSpawner(agentConfiguration));
                    spawnersExist = true;
                }
            }
        }

        // OD-Matrices
        List<OriginDestinationVehicleFlowGenerator> matrices = new ArrayList<>();
        if (mappingConfiguration.matrixMappers != null) {
            for (COriginDestinationMatrixMapper mapper : mappingConfiguration.matrixMappers) {
                if (mapper != null) {
                    matrices.add(new OriginDestinationVehicleFlowGenerator(mapper));
                }
            }
        }

        boolean flowNoise = mappingConfiguration.config != null && mappingConfiguration.config.randomizeFlows;
        boolean fixedOrder = mappingConfiguration.config != null && mappingConfiguration.config.fixedOrder;
        for (OriginDestinationVehicleFlowGenerator mapper : matrices) {
            mapper.generateVehicleStreams(this, rng, flowNoise, fixedOrder);
            spawnersExist = true;
        }

        if (!spawnersExist) {
            LOG.info("No vehicle spawners defined in mapping config. Only external vehicles will be simulated.");
        }

        // Use the prototype configurations to complete the spawner-definitions:
        completeSpawnerDefinitions();
    }

    public long getTime() {
        return time;
    }

    public RtiAmbassador getRti() {
        return rti;
    }

    void setScenarioTrafficLightRegistration(ScenarioTrafficLightRegistration trafficLightsRegistration) {
        this.scenarioTrafficLightRegistration = trafficLightsRegistration;
    }

    /**
     * Used by {@link OriginDestinationVehicleFlowGenerator} to externally add
     * additional {@link VehicleFlowGenerator}s.
     *
     * @param vehicleFlowGenerator the {@link VehicleFlowGenerator} to be added
     */
    void addVehicleStream(VehicleFlowGenerator vehicleFlowGenerator) {
        this.vehicleFlowGenerators.add(vehicleFlowGenerator);
    }

    private void randomizeStartingTimes(RandomNumberGenerator rng, CVehicle spawner) {
        if (spawner.spawningMode != CVehicle.SpawningMode.CONSTANT) {
            return;
        }
        spawner.startingTime = Math.max(0, Math.round(spawner.startingTime + rng.nextDouble(-20, 20)));
    }

    private void randomizeWeights(RandomNumberGenerator rng, List<CPrototype> types) {
        double sum = 0d;
        for (CPrototype type : types) {
            if (type.weight != null) {
                sum += type.weight;
            }
        }
        CPrototype last = null;
        for (CPrototype type : types) {
            if (type.weight != null && type.weight > 0) {
                // randomize weight within a reasonable range

                double newWeight = Math.round(rng.nextGaussian(type.weight, sum / 100d) * 100d) / 100d;
                type.weight = Math.min(Math.max(type.weight - sum / 100d, newWeight), type.weight + sum / 100d);
                sum -= type.weight;
                last = type;
            }
        }
        if (last != null && last.weight > 0) {
            // makes sure that the new sum of weights is equal to the previous sum
            last.weight = Math.round((last.weight + sum) * 100d) / 100d;
        }
    }

    private boolean generateVehicleFlows(List<CVehicle> vehicles, CMappingConfiguration config, RandomNumberGenerator rng) {
        // A remainder after one scaling is always less than 1.
        // If after some scalings remainderSum became more than 1,
        // we add one to maxNumberVehicles of the current vehicle spawner and subtract one from remainderSum,
        // getting back to less than 1 value.
        boolean spawnersExist = false;
        double remainderSum = 0;
        double scaleTraffic = config != null ? config.scaleTraffic : 1d;
        for (CVehicle vehicleConfiguration : vehicles) {

            //The "continue" still can be called if the spawner exists but the value of
            // maxNumberVehicles was explicitly set to 0 in the mapping
            //(convenient for testing of different mapping variations)
            if (vehicleConfiguration == null
                    || (vehicleConfiguration.maxNumberVehicles != null
                    && vehicleConfiguration.maxNumberVehicles == 0)) {
                continue;
            }
            if (vehicleConfiguration.maxNumberVehicles == null) {
                vehicleConfiguration.maxNumberVehicles = Integer.MAX_VALUE;
            }
            spawnersExist = true;

            if ((Math.abs(scaleTraffic - 1.0) > 0.0001d)) {
                if (vehicleConfiguration.maxNumberVehicles != Integer.MAX_VALUE) {
                    double numberOfVehiclesScaled = vehicleConfiguration.maxNumberVehicles * scaleTraffic;
                    remainderSum += (numberOfVehiclesScaled) - (int) numberOfVehiclesScaled;

                    vehicleConfiguration.maxNumberVehicles = (int) (Math.floor(numberOfVehiclesScaled) + Math.floor(remainderSum));
                    remainderSum -= Math.floor(remainderSum);
                }
                vehicleConfiguration.targetFlow = Math.round(vehicleConfiguration.targetFlow * scaleTraffic);
            }

            vehicleConfiguration.types = replaceWithTypesFromPredefinedDistribution(
                    vehicleConfiguration, rng
            );

            if (config != null && config.adjustStartingTimes && config.start != null) {
                vehicleConfiguration.startingTime = vehicleConfiguration.startingTime - config.start;
                if (vehicleConfiguration.maxTime != null) {
                    vehicleConfiguration.maxTime = vehicleConfiguration.maxTime - config.start;
                }
            }

            if (vehicleConfiguration.startingTime >= 0) {
                boolean flowNoise = config != null && config.randomizeFlows;
                boolean fixedOrder = config != null && config.fixedOrder;

                vehicleFlowGenerators.add(
                        new VehicleFlowGenerator(vehicleConfiguration, rng, flowNoise, fixedOrder)
                );
            }
        }
        return spawnersExist;
    }


    private List<CPrototype> replaceWithTypesFromPredefinedDistribution(CVehicle spawner, RandomNumberGenerator rng) {
        // return distribution given by typeDistribution
        if (spawner.typeDistribution != null) {
            return new ArrayList<>(typeDistributions.get(spawner.typeDistribution));
        }

        if (config != null && config.randomizeStartingTimes) {
            randomizeStartingTimes(rng, spawner);
        }

        if (spawner.types != null) {
            List<CPrototype> newTypes = new ArrayList<>();
            for (CPrototype type : spawner.types) {
                List<CPrototype> typesFromDistribution = typeDistributions.get(type.name);
                if (typesFromDistribution == null) {
                    newTypes.add(type);
                } else {
                    double typeWeight = spawner.types.size() == 1 && type.weight == null
                            ? 1.0 :
                            defaultIfNull(type.weight, 0d);

                    double totalWeightOfDistribution = typesFromDistribution.stream().mapToDouble((t) -> t.weight).sum();
                    for (CPrototype typeFromDistribution : typesFromDistribution) {
                        CPrototype copy = typeFromDistribution.copy();
                        copy.weight = (typeFromDistribution.weight / totalWeightOfDistribution) * typeWeight;
                        newTypes.add(copy);
                    }

                }
            }
            return newTypes;
        }
        // if no types could be defined
        return null;
    }

    /**
     * Uses the prototype configurations to complete the spawner-definitions.
     */
    private void completeSpawnerDefinitions() {
        for (RoadSideUnitSpawner rsu : rsus) {
            rsu.fillInPrototype(getPrototypeByName(rsu.getPrototypeName()));
        }

        for (TrafficManagementCenterSpawner tmc : tmcs) {
            tmc.fillInPrototype(getPrototypeByName(tmc.getPrototypeName()));
        }

        for (ServerSpawner server : servers) {
            server.fillInPrototype(getPrototypeByName(server.getPrototypeName()));
        }

        for (TrafficLightSpawner tl : tls.values()) {
            tl.fillInPrototype(getPrototypeByName(tl.getPrototypeName()));
        }

        for (ChargingStationSpawner chargingStation : chargingStations) {
            chargingStation.fillInPrototype(getPrototypeByName(chargingStation.getPrototypeName()));
        }

        // If adjustStartingTimes is configured, only the end time is relevant for remaining spawners.
        if (config != null && config.adjustStartingTimes && config.start != null) {
            config.end = config.end != null ? config.end - config.start : null;
            config.start = null;
        }

        for (VehicleFlowGenerator spawner : vehicleFlowGenerators) {
            spawner.fillInPrototype(this);

            if (config != null) {
                spawner.configure(config);
            }
        }

        for (AgentSpawner agentSpawner : agentSpawners) {
            agentSpawner.fillInPrototype(getPrototypeByName(agentSpawner.getPrototypeName()));

            if (config != null) {
                agentSpawner.configure(config);
            }
        }
    }

    /**
     * Generates the {@link VehicleTypesInitialization} interaction, which
     * is sent to inform the simulation about all initialized vehicle.
     *
     * @return the created {@link VehicleTypesInitialization}
     */
    VehicleTypesInitialization generateVehicleTypesInitialization() {
        HashMap<String, VehicleType> types = new HashMap<>();

        vehicleFlowGenerators.forEach((spawner -> spawner.collectVehicleTypes(types)));

        return new VehicleTypesInitialization(0, types);
    }

    /**
     * Searches for the {@link CPrototype} represented by the input string
     * and returns it. If no match is found null is returned.
     *
     * @param name {@link String} representation of the desired prototype
     * @return the found prototype configuration or null
     */
    CPrototype getPrototypeByName(String name) {
        if (name == null) {
            return null;
        }

        for (CPrototype prototypeConfiguration : prototypeConfigurations) {
            if (prototypeConfiguration.name.contentEquals(name)) {
                return prototypeConfiguration;
            }
        }

        return null;
    }

    public List<CPrototype> getTypeDistributionByName(String name) {
        if (name == null) {
            return Lists.newArrayList();
        }

        final List<CPrototype> types = typeDistributions.get(name);
        if (types != null) {
            return types;
        }

        return Lists.newArrayList();
    }

    /**
     * This method handles a time advance called by the {@link MappingAmbassador}.
     *
     * @param time the time to handle
     * @param rti  the {@link RtiAmbassador} used for scenario traffic light registration
     * @param rng  the {@link RandomNumberGenerator} used for scenario traffic light registration
     * @throws InternalFederateException thrown if a time advance couldn't be processed
     */
    void timeAdvance(long time, RtiAmbassador rti, RandomNumberGenerator rng) throws InternalFederateException {
        this.time = time;

        // Server, TMC, RSU, Charging Station Initialization
        if (!immobileUnitsInitialized) {
            initStationaryUnits();
            immobileUnitsInitialized = true;
        }
        // traffic light initialization
        if (scenarioTrafficLightRegistration != null && !trafficLightsInitialized) {
            initTrafficLights(time, rti, rng);
            trafficLightsInitialized = true;
        }

        Iterator<VehicleFlowGenerator> vehicleFlowIt = vehicleFlowGenerators.iterator();
        while (vehicleFlowIt.hasNext()) {
            VehicleFlowGenerator vehicleFlowGenerator = vehicleFlowIt.next();
            if (vehicleFlowGenerator.timeAdvance(this)) {
                vehicleFlowIt.remove();
            }
        }

        Iterator<AgentSpawner> agentSpawnerIt = agentSpawners.iterator();
        while (agentSpawnerIt.hasNext()) {
            AgentSpawner agentSpawner = agentSpawnerIt.next();
            if (agentSpawner.timeAdvance(this)) {
                agentSpawnerIt.remove();
            }
        }
    }

    private void initTrafficLights(long time, RtiAmbassador rti, RandomNumberGenerator rng) throws InternalFederateException {
        WeightedSelector<TrafficLightSpawner> selector = null;
        List<TrafficLightSpawner> itemsWithWeight = tls.values().stream().filter(tl -> tl.getWeight() != 0).toList();
        if (!itemsWithWeight.isEmpty()) {
            selector = new StochasticSelector<>(itemsWithWeight, rng);
        }

        for (TrafficLightGroup tlGroup : scenarioTrafficLightRegistration.getTrafficLightGroups()) {
            // do we have a specific overwrite?
            TrafficLightSpawner prototype = null;
            for (TrafficLightSpawner spawner : tls.values()) {
                if ((spawner.getTlName() != null) && (spawner.getTlName().contentEquals(tlGroup.getGroupId()))) {
                    prototype = spawner;
                }
            }

            // otherwise just select one tl by random
            if ((prototype == null) && (selector != null)) {
                prototype = selector.nextItem();
            }

            List<String> apps;

            String name = UnitNameGenerator.nextTlName();
            String group;
            if (prototype != null) {
                apps = prototype.getApplications();
                group = ObjectUtils.defaultIfNull(prototype.getGroup(), tlGroup.getGroupId());
            } else {
                apps = new ArrayList<>();
                group = null;
            }

            TrafficLightRegistration tlRegistration = new TrafficLightRegistration(
                    time, name, group, apps, tlGroup,
                    scenarioTrafficLightRegistration.getLanesControlledByGroups().get(tlGroup.getGroupId())
            );
            LOG.info("Creating Traffic Light Group: name={},tlGroupId={},apps={}", name, tlGroup.getGroupId(), apps);
            try {
                rti.triggerInteraction(tlRegistration);
            } catch (IllegalValueException e) {
                LOG.error("Couldn't send {}, for tlGroup={}",
                        TrafficLightRegistration.class.getSimpleName(), tlRegistration.getTrafficLightGroup().getGroupId(), e);
                throw new InternalFederateException(e);
            }
        }
    }

    private void initStationaryUnits() throws InternalFederateException {
        // First time-advance. We need to process RSUs, ChargingStations, TMCs and Servers:
        for (StationaryUnitSpawner spawner : stationarySpawners) {
            spawner.init(this);
        }
    }
}
