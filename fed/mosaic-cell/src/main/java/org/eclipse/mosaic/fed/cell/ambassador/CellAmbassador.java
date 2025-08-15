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

package org.eclipse.mosaic.fed.cell.ambassador;

import org.eclipse.mosaic.fed.cell.chain.ChainManager;
import org.eclipse.mosaic.fed.cell.config.CCell;
import org.eclipse.mosaic.fed.cell.config.CNetwork;
import org.eclipse.mosaic.fed.cell.config.CRegion;
import org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties;
import org.eclipse.mosaic.fed.cell.config.util.ConfigurationReader;
import org.eclipse.mosaic.fed.cell.data.ConfigurationData;
import org.eclipse.mosaic.fed.cell.data.SimulationData;
import org.eclipse.mosaic.fed.cell.utility.HandoverUtility;
import org.eclipse.mosaic.fed.cell.utility.RegionUtility;
import org.eclipse.mosaic.fed.cell.viz.BandwidthMeasurementManager;
import org.eclipse.mosaic.interactions.agent.AgentUpdates;
import org.eclipse.mosaic.interactions.communication.CellularCommunicationConfiguration;
import org.eclipse.mosaic.interactions.communication.CellularHandoverUpdates;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.interactions.mapping.ChargingStationRegistration;
import org.eclipse.mosaic.interactions.mapping.RsuRegistration;
import org.eclipse.mosaic.interactions.mapping.ServerRegistration;
import org.eclipse.mosaic.interactions.mapping.TmcRegistration;
import org.eclipse.mosaic.interactions.mapping.TrafficLightRegistration;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.objects.UnitData;
import org.eclipse.mosaic.lib.objects.UnitNameGenerator;
import org.eclipse.mosaic.lib.objects.agent.AgentData;
import org.eclipse.mosaic.lib.objects.communication.CellConfiguration;
import org.eclipse.mosaic.lib.objects.communication.HandoverInfo;
import org.eclipse.mosaic.lib.objects.mapping.ChargingStationMapping;
import org.eclipse.mosaic.lib.objects.mapping.RsuMapping;
import org.eclipse.mosaic.lib.objects.mapping.ServerMapping;
import org.eclipse.mosaic.lib.objects.mapping.TmcMapping;
import org.eclipse.mosaic.lib.objects.mapping.TrafficLightMapping;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.AbstractFederateAmbassador;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Ambassador for the Cell network simulator which handles the interaction with Eclipse MOSAIC.
 */
public class CellAmbassador extends AbstractFederateAmbassador {

    /**
     * Reference to SimulationData singleton.
     * For updating simData (mainly time and mobility information)
     * during runtime with information from Eclipse MOSAIC
     */
    private final SimulationData simData = SimulationData.INSTANCE;

    /**
     * Reference to ChainManager.
     * As entry point of cell message transmission.
     */
    private ChainManager chainManager;

    /**
     * Entities to be added to SimData as soon they configure cellular communication.
     * These entities should all be stationary, including RSUs, TrafficLights and ChargingStations.
     */
    private final Map<String, CartesianPoint> registeredEntities = new HashMap<>();

    /**
     * A map of registered servers and their properties, which are position-less (in terms of radio cells)
     * as they are treated as being located in the internet.
     */
    private final Map<String, CNetworkProperties> registeredServers = new HashMap<>();

    /**
     * Mobile nodes to be added to {@link #simData} as soon as they configure cellular communication AND have moved in traffic simulation.
     */
    private final Map<String, AtomicReference<CellConfiguration>> registeredMobileNodes = new HashMap<>();

    /**
     * Store latest {@link VehicleUpdates} as they may be needed twice after enabling cell modules for vehicles.
     */
    private VehicleUpdates latestVehicleUpdates;

    /**
     * Store latest {@link AgentUpdates} as they may be needed twice after enabling cell modules for vehicles.
     */
    private AgentUpdates latestAgentUpdates;

    /**
     * Manager for detailed statistic of network load (e.g. of Upstream and Downstream) in individual regions / cells.
     */
    private BandwidthMeasurementManager bandwidthMeasurementManager;

    /**
     * Constructor for the Cell Ambassador.
     *
     * @param ambassadorParameter the parameters for the ambassador
     */
    public CellAmbassador(AmbassadorParameter ambassadorParameter) {
        super(ambassadorParameter);
    }

    @Override
    public void initialize(long startTime, long endTime) throws InternalFederateException {
        log.info("Start Cell");

        readConfigurations();

        final RandomNumberGenerator rng = rti.createRandomNumberGenerator();

        chainManager = new ChainManager(rti, rng, ambassadorParameter);

        initializeBandwidthMeasurements();

        log.info("Finished Initialization, waiting for Interactions...");
    }

    private void initializeBandwidthMeasurements() {
        bandwidthMeasurementManager = new BandwidthMeasurementManager(log);
        bandwidthMeasurementManager.createStreamListener(chainManager);
    }

    /**
     * Digest the cell-configs in network.json and regions.json before the simulation starts.
     */
    private void readConfigurations() throws InternalFederateException {
        log.debug("Read Cell Configuration");

        if (log.isTraceEnabled()) {
            log.trace("Opening cell configuration file {}", ambassadorParameter.configuration);
        }

        CCell cellConfig = ConfigurationReader.importCellConfig(ambassadorParameter.configuration.getAbsolutePath());

        log.debug("Read Cell Network Configuration");
        String networkConfigurationPath = ambassadorParameter.configuration.getParent()
                + File.separator
                + cellConfig.networkConfigurationFile;

        if (log.isTraceEnabled()) {
            log.trace("Opening configuration file as network configuration {}", networkConfigurationPath);
        }

        CNetwork networkConfig = ConfigurationReader.importNetworkConfig(networkConfigurationPath);

        log.info("Using network config:");
        log.info(networkConfig.toString());

        // Read the region configuration file
        log.debug("Read Cell Region Configuration");
        String regionConfigurationPath = ambassadorParameter.configuration.getParent()
                + File.separator
                + cellConfig.regionConfigurationFile;

        if (log.isTraceEnabled()) {
            log.trace("Opening configuration file {}", regionConfigurationPath);
        }

        CRegion regionConfig = ConfigurationReader.importRegionConfig(regionConfigurationPath);

        log.info("Using region config:");
        log.info(regionConfig.toString());

        // This *has* to be done (set the configuration references for the cell models)
        ConfigurationData.INSTANCE.setCellConfig(cellConfig);
        ConfigurationData.INSTANCE.setNetworkConfig(networkConfig);
        ConfigurationData.INSTANCE.setRegionConfig(regionConfig);

        // Initialize spatial index for fast region lookup
        RegionUtility.initializeRegionsIndex(regionConfig.regions);
    }

    @Override
    public void finishSimulation() {
        log.info("FinishSimulation");
        chainManager.printStatistics();
        bandwidthMeasurementManager.finish();
    }

    @Override
    protected void processTimeAdvanceGrant(long time) {
        log.trace("ProcessTimeAdvanceGrant at t={}", TIME.format(time));
        chainManager.advanceTime(time);
    }

    @Override
    protected void processInteraction(Interaction interaction) throws InternalFederateException {
        if (this.log.isDebugEnabled()) {
            this.log.debug("ProcessInteraction {} at t={}", interaction.getTypeId(), TIME.format(interaction.getTime()));
        }

        // Process interactions as usual in all communication simulators
        if (interaction.getTypeId().equals(RsuRegistration.TYPE_ID)) {
            process((RsuRegistration) interaction);
        } else if (interaction.getTypeId().equals(TrafficLightRegistration.TYPE_ID)) {
            process((TrafficLightRegistration) interaction);
        } else if (interaction.getTypeId().equals(ChargingStationRegistration.TYPE_ID)) {
            process((ChargingStationRegistration) interaction);
        } else if (interaction.getTypeId().equals(ServerRegistration.TYPE_ID)) {
            process((ServerRegistration) interaction);
        } else if (interaction.getTypeId().equals(TmcRegistration.TYPE_ID)) {
            process((TmcRegistration) interaction);
        } else if (interaction.getTypeId().equals(VehicleUpdates.TYPE_ID)) {
            process((VehicleUpdates) interaction);
        } else if (interaction.getTypeId().equals(AgentUpdates.TYPE_ID)) {
            process((AgentUpdates) interaction);
        } else if (interaction.getTypeId().equals(CellularCommunicationConfiguration.TYPE_ID)) {
            final CellularCommunicationConfiguration configInteraction = (CellularCommunicationConfiguration) interaction;
            // Node configuration must be done in the correct order, therefore we must ensure that it is scheduled by the chain manager
            chainManager.addEvent(configInteraction.getTime(), e -> process(configInteraction));
        } else if (interaction.getTypeId().equals(V2xMessageTransmission.TYPE_ID)) {
            // Communication dependent (cell) interactions go directly through the chainManager
            chainManager.startEvent((V2xMessageTransmission) interaction);
        }
    }

    /**
     * Registers the new Road Side Unit (RSU) in the cell simulation and
     * the geographic position will be initialized as cartesian.
     *
     * @param rsuRegistration Road Side Unit (RSU) object to be added to the cell simulation.
     */
    private void process(RsuRegistration rsuRegistration) {
        RsuMapping rsu = rsuRegistration.getMapping();
        if (rsu.hasApplication()) {
            CartesianPoint cartesianPosition = rsu.getPosition().toCartesian();
            registeredEntities.put(rsu.getName(), cartesianPosition);
            if (log.isDebugEnabled()) {
                log.debug("Added RSU (id={}, with app(s)={}) at position={}, t={}",
                        rsu.getName(), rsu.getApplications(), rsu.getPosition(),
                        TIME.format(rsuRegistration.getTime()));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("RSU (id={}) has NO application and is ignored in "
                        + "communication simulation", rsu.getName());
            }
        }
    }

    /**
     * Registers the new Traffic light in the cell simulation while transforming
     * the geographic coordinates into the corresponding cartesian coordinates.
     *
     * @param trafficLightRegistration Traffic light object to be added to the cell simulation.
     */
    private void process(TrafficLightRegistration trafficLightRegistration) {
        TrafficLightMapping tls = trafficLightRegistration.getMapping();
        if (tls.hasApplication()) {
            CartesianPoint cartesianPosition = tls.getPosition().toCartesian();
            registeredEntities.put(tls.getName(), cartesianPosition);
            if (log.isDebugEnabled()) {
                log.debug("Added TL (id={}, with app(s)={}) at position={}, t={}",
                        tls.getName(), tls.getApplications(), tls.getPosition(),
                        TIME.format(trafficLightRegistration.getTime()));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("TL (id={}) has NO application and is ignored in "
                        + "communication simulation", tls.getName());
            }
        }
    }

    /**
     * Registers the new Charging Stations with its applications in the cell simulation while
     * transforming the geographic coordinates into the corresponding cartesian coordinates.
     *
     * @param chargingStationRegistration Charging Station object to be added to the cell simulation.
     */
    private void process(ChargingStationRegistration chargingStationRegistration) {
        ChargingStationMapping cs = chargingStationRegistration.getMapping();
        if (cs.hasApplication()) {
            CartesianPoint cartesianPosition = cs.getPosition().toCartesian();
            registeredEntities.put(cs.getName(), cartesianPosition);
            if (log.isDebugEnabled()) {
                log.debug("Added CS (id={}, with app(s)={}) at position={}, t={}",
                        cs.getName(), cs.getApplications(), cs.getPosition(),
                        TIME.format(chargingStationRegistration.getTime()));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("CS (id={}) has NO application and is ignored in "
                        + "communication simulation", cs.getName());
            }
        }
    }

    /**
     * Registers the new Internet-Server in the cell simulation.
     *
     * @param serverRegistration TMC object to be added to the cell simulation.
     */
    private void process(ServerRegistration serverRegistration) {
        ServerMapping server = serverRegistration.getMapping();
        // only register servers that have applications and the group parameter set
        if (server.hasApplication() && server.getGroup() != null) {
            registerServer(server.getName(), server.getGroup());

            if (log.isDebugEnabled()) {
                log.debug("Added Server (id={}, with app(s)={}), t={}",
                        server.getName(), server.getApplications(),
                        TIME.format(serverRegistration.getTime()));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Server (id={}) has NO application or group and is ignored in "
                        + "communication simulation", server.getName());
            }
        }
    }

    /**
     * Registers the new Traffic Management Center (TMC) as position-less server in the cell simulation.
     *
     * @param tmcRegistration TMC object to be added to the cell simulation.
     */
    private void process(TmcRegistration tmcRegistration) {
        TmcMapping tmc = tmcRegistration.getMapping();
        if (tmc.hasApplication()) {
            registerServer(tmc.getName(), tmc.getGroup());
            if (log.isDebugEnabled()) {
                log.debug("Added Server (TMC) (id={}, with app(s)={}), t={}",
                        tmc.getName(), tmc.getApplications(),
                        TIME.format(tmcRegistration.getTime()));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Server (TMC) (id={}) has NO application and is ignored in "
                        + "communication simulation", tmc.getName());
            }
        }
    }

    /**
     * Register any vehicle updates in the cell simulation and store all vehicle data in a {@link HandoverInfo} list
     * contains the nodeID, previous and current region.
     *
     * @param vehicleUpdates Vehicle movement object
     */
    private void process(VehicleUpdates vehicleUpdates) throws InternalFederateException {
        latestVehicleUpdates = vehicleUpdates;

        processMobileNodesUpdates(vehicleUpdates.getTime(),
                Iterables.concat(vehicleUpdates.getAdded(), vehicleUpdates.getUpdated()),
                VehicleData::getProjectedPosition, VehicleData::getSpeed
        );
    }

    /**
     * Register any agent updates in the cell simulation and store all agent data in a {@link HandoverInfo} list
     * contains the nodeID, previous and current region.
     *
     * @param agentUpdates Agent movement object
     */
    private void process(AgentUpdates agentUpdates) throws InternalFederateException {
        latestAgentUpdates = agentUpdates;

        processMobileNodesUpdates(agentUpdates.getTime(), agentUpdates.getUpdated(),
                a -> a.getPosition().toCartesian(),
                AgentData::getSpeed
        );
    }

    private <UNIT extends UnitData> void processMobileNodesUpdates(long currentTime,
                                                                   Iterable<UNIT> units,
                                                                   Function<UNIT, CartesianPoint> cartesianPointAccess,
                                                                   Function<UNIT, Double> speedAccess) throws InternalFederateException {
        final List<HandoverInfo> handovers = new ArrayList<>();

        for (UNIT unit : units) {
            if (isCellEnabledForMobileNode(unit.getName())) {
                Optional<HandoverInfo> handoverInfo = registerOrUpdateMobileNode(
                        currentTime, unit, cartesianPointAccess.apply(unit), speedAccess.apply(unit)
                );
                handoverInfo.ifPresent(handovers::add);
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("Regionstatus at t={}", TIME.format(currentTime));
            for (CNetworkProperties region : RegionUtility.getAllRegions(true, false)) {
                log.trace(" \"{}\" contains the node(s) {}", region.id, RegionUtility.getNodesForRegion(region));
            }
        }

        if (!handovers.isEmpty()) {
            chainManager.sendInteractionToRti(new CellularHandoverUpdates(currentTime, handovers));
        }
    }

    private Optional<HandoverInfo> registerOrUpdateMobileNode(long time, UnitData unit, CartesianPoint projectedPosition, double speed) throws InternalFederateException {
        String previousRegion = null;
        if (simData.containsCellConfigurationOfNode(unit.getName())) {
            previousRegion = RegionUtility.getRegionIdForNode(unit.getName());
            if (log.isDebugEnabled()) {
                log.debug("Updated MOBILE NODE (id={}) to position={} with speed={}, t={}", unit.getName(), unit.getPosition(), speed, TIME.format(time));
            }
        } else {
            simData.setCellConfigurationOfNode(unit.getName(), registeredMobileNodes.get(unit.getName()).get());
            log.debug("Added MOBILE NODE (id={}) at position={} with speed={}, t={}", unit.getName(), unit.getPosition(), speed, TIME.format(time));
        }

        simData.setPositionOfNode(unit.getName(), projectedPosition);
        simData.setSpeedOfNode(unit.getName(), speed);

        CNetworkProperties currentRegion = RegionUtility.getRegionForPosition(projectedPosition);
        if (HandoverUtility.isAfterHandover(currentRegion.id, previousRegion)) {
            simData.setRegionOfNode(unit.getName(), currentRegion);
            return Optional.of(new HandoverInfo(unit.getName(), currentRegion.id, previousRegion));
        }
        return Optional.empty();
    }

    /**
     * Configure the cellModule of a node (enable, disable or change parameters).
     * For vehicles, the position has to be set as well before the vehicle can be considered for the cell simulation.
     * Hence, the configuration is stored in {@link #registeredMobileNodes}.
     *
     * @param interaction Interaction to configure the cell communication node.
     */
    private void process(CellularCommunicationConfiguration interaction) throws InternalFederateException {
        Validate.notNull(interaction.getConfiguration(), "CellConfiguration is null");
        long interactionTime = interaction.getTime();
        CellConfiguration cellConfiguration = interaction.getConfiguration();

        cellConfiguration.setBitrates(
                ObjectUtils.defaultIfNull(
                        cellConfiguration.getMaxDownlinkBitrate(),
                        ConfigurationData.INSTANCE.getNetworkConfig().defaultDownlinkCapacity
                ),
                ObjectUtils.defaultIfNull(
                        cellConfiguration.getMaxUplinkBitrate(),
                        ConfigurationData.INSTANCE.getNetworkConfig().defaultUplinkCapacity
                )
        );

        final String nodeId = cellConfiguration.getNodeId();
        final boolean isMobileNode = UnitNameGenerator.isVehicle(nodeId) || UnitNameGenerator.isAgent(nodeId);

        Optional<HandoverInfo> handoverInfo = Optional.empty();
        if (cellConfiguration.isEnabled()) {
            if (isMobileNode) { // handle vehicles and agents
                handoverInfo = handleMobileUnitCellConfiguration(nodeId, cellConfiguration, interactionTime);
            } else if (registeredEntities.containsKey(nodeId)) { // handle stationary entities
                handleEntityCellConfiguration(nodeId, cellConfiguration, interactionTime);
            } else if (registeredServers.containsKey(nodeId)) { // handle servers (nodes in Internet, w/o radio region)
                handleServerCellConfiguration(nodeId, cellConfiguration, interactionTime);
            } else {
                throw new InternalFederateException(
                        "Cell Ambassador: Cannot activate Cell module for \"" + nodeId + "\" because the id is unknown"
                );
            }
        } else {
            if (isMobileNode) {
                // disables cell node and removes vehicle or agents
                handoverInfo = unregisterMobileUnit(interactionTime, nodeId);
            } else {
                disableCellForNode(nodeId);
            }
            log.info(
                    "Disabled Cell Communication for {}={}, t={}", (isMobileNode ? "mobile node" : "entity"), nodeId, TIME.format(interactionTime)
            );
        }
        handoverInfo.ifPresent((handover) -> {
            List<HandoverInfo> handoverInfos = Lists.newArrayList(handover);
            CellularHandoverUpdates handoverUpdatesInteraction = new CellularHandoverUpdates(interactionTime, handoverInfos);
            chainManager.sendInteractionToRti(handoverUpdatesInteraction);
        });
    }

    /**
     * Checks whether a mobile unit (vehicle or person) is registered in a cell. If the unit is registered, unregister it.
     *
     * @param time Simulation time.
     * @param name unit ID.
     * @return handover information of unregistered unit
     */
    private Optional<HandoverInfo> unregisterMobileUnit(long time, String name) throws InternalFederateException {
        Optional<HandoverInfo> handoverInfo = Optional.empty();
        if (registeredMobileNodes.containsKey(name)) {
            handoverInfo = disableCellForNode(name);
            registeredMobileNodes.remove(name);
            log.info("Removed MOBILE NODE (id={}) from simulation, t={}",
                    name,
                    TIME.format(time));
        }
        return handoverInfo;
    }

    /**
     * Checks whether cellular communication is enabled or not.
     *
     * @param nodeName the name of the mobile node.
     * @return True if cellular communication enabled.
     */
    private boolean isCellEnabledForMobileNode(String nodeName) {
        final AtomicReference<CellConfiguration> mobileNode = registeredMobileNodes.get(nodeName);
        return mobileNode != null
                && mobileNode.get() != null
                && mobileNode.get().isEnabled();
    }

    private Optional<HandoverInfo> handleMobileUnitCellConfiguration(String nodeId, CellConfiguration cellConfiguration, Long interactionTime) throws InternalFederateException {
        registeredMobileNodes
                .computeIfAbsent(nodeId, k -> new AtomicReference<>())
                .set(cellConfiguration);
        Optional<HandoverInfo> handoverInfo = Optional.empty();
        // update the cell configuration if the cell module was already activated
        if (simData.containsCellConfigurationOfNode(nodeId)) {
            simData.setCellConfigurationOfNode(nodeId, cellConfiguration);
            log.info("Updated (Configured) Cell Communication for mobile node={}, t={}",
                    nodeId, TIME.format(interactionTime));
        } else {
            log.info("Enabled (Configured) Cell Communication for mobile node={}, t={}",
                    nodeId, TIME.format(interactionTime));

            VehicleData vehicleData = fetchVehicleDataFromLastUpdate(nodeId);
            if (vehicleData != null) {
                return registerOrUpdateMobileNode(interactionTime, vehicleData, vehicleData.getProjectedPosition(), vehicleData.getSpeed());
            }

            AgentData agentData = fetchAgentDataFromLastUpdate(nodeId);
            if (agentData != null) {
                return registerOrUpdateMobileNode(interactionTime, agentData, agentData.getPosition().toCartesian(), agentData.getSpeed());
            }
        }
        return handoverInfo;
    }

    private VehicleData fetchVehicleDataFromLastUpdate(String vehicleId) {
        if (latestVehicleUpdates == null) {
            return null;
        }
        // see if vehicle was added or updated within the last vehicle update
        return Stream.concat(latestVehicleUpdates.getAdded().stream(), latestVehicleUpdates.getUpdated().stream())
                .filter(v -> v.getName().equals(vehicleId))
                .findFirst()
                .orElse(null);
    }

    private AgentData fetchAgentDataFromLastUpdate(String agentId) {
        if (latestAgentUpdates == null) {
            return null;
        }
        // see if vehicle was added or updated within the last vehicle update
        return latestAgentUpdates.getUpdated().stream()
                .filter(a -> a.getName().equals(agentId))
                .findFirst()
                .orElse(null);
    }

    private void handleEntityCellConfiguration(String nodeId, CellConfiguration cellConfiguration, long interactionTime) {
        CNetworkProperties regionProperties = RegionUtility.getRegionForPosition(registeredEntities.get(nodeId));
        registerStationaryNode(nodeId, cellConfiguration, interactionTime, regionProperties);
        simData.setPositionOfNode(nodeId, registeredEntities.get(nodeId));
    }

    private void handleServerCellConfiguration(String nodeId, CellConfiguration cellConfiguration, long interactionTime) {
        CNetworkProperties serverProperties = registeredServers.get(nodeId);
        registerStationaryNode(nodeId, cellConfiguration, interactionTime, serverProperties);
    }

    private void registerStationaryNode(String nodeId, CellConfiguration cellConfiguration,
                                        long interactionTime, CNetworkProperties properties) {
        simData.setRegionOfNode(nodeId, properties);
        simData.setSpeedOfNode(nodeId, 0);
        simData.setCellConfigurationOfNode(nodeId, cellConfiguration);

        log.info(
                "Enabled (Configured) Cell Communication for entity={}, t={} with capacity in downlink={} and uplink={}",
                nodeId, TIME.format(interactionTime),
                cellConfiguration.getAvailableDownlinkBitrate(),
                cellConfiguration.getAvailableUplinkBitrate()
        );
    }

    private void registerServer(String serverName, String serverGroup) {
        CNetworkProperties serverProperties = ConfigurationData.INSTANCE.getServerRegionFromConfiguration(serverGroup);
        if (serverProperties != null) {
            registeredServers.put(serverName, serverProperties);
        } else {
            log.warn("""
                            No server properties for server group "{}" found in "{}" config-file.
                            If you intend to use cell-communication with this unit please add a configuration.
                            """,
                    serverGroup, ConfigurationData.INSTANCE.getCellConfig().networkConfigurationFile
            );
        }
    }

    /**
     * Disable cellular communication for a node.
     *
     * @param nodeId Node to disable the cellular communication.
     * @return Information for handover.
     */
    private Optional<HandoverInfo> disableCellForNode(String nodeId) throws InternalFederateException {
        if (simData.containsCellConfigurationOfNode(nodeId)) {
            HandoverInfo handoverInfo = new HandoverInfo(nodeId, null, RegionUtility.getRegionIdForNode(nodeId));
            simData.removeNode(nodeId);
            return Optional.of(handoverInfo);
        }
        return Optional.empty();
    }

    /**
     * This returns {@code false}, since the {@link CellAmbassador} is developed in a way, where it takes
     * care of its own time management and event scheduling.
     *
     * @return {@code false}
     */
    @Override
    public boolean isTimeConstrained() {
        return false;
    }

    /**
     * This returns {@code false}, since the {@link CellAmbassador} is developed in a way, where it takes
     * care of its own time management and event scheduling.
     *
     * @return {@code false}
     */
    @Override
    public boolean isTimeRegulating() {
        return false;
    }
}
