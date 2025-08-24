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

package org.eclipse.mosaic.fed.sns.ambassador;

import org.eclipse.mosaic.fed.sns.config.CSns;
import org.eclipse.mosaic.interactions.communication.AdHocCommunicationConfiguration;
import org.eclipse.mosaic.interactions.communication.V2xMessageReception;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.interactions.mapping.ChargingStationRegistration;
import org.eclipse.mosaic.interactions.mapping.RsuRegistration;
import org.eclipse.mosaic.interactions.mapping.TrafficLightRegistration;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.model.delay.GammaSpeedDelay;
import org.eclipse.mosaic.lib.model.transmission.TransmissionResult;
import org.eclipse.mosaic.lib.objects.UnitNameGenerator;
import org.eclipse.mosaic.lib.objects.communication.AdHocConfiguration;
import org.eclipse.mosaic.lib.objects.mapping.ChargingStationMapping;
import org.eclipse.mosaic.lib.objects.mapping.RsuMapping;
import org.eclipse.mosaic.lib.objects.mapping.TrafficLightMapping;
import org.eclipse.mosaic.lib.objects.v2x.V2xReceiverInformation;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.AbstractFederateAmbassador;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the Simple Network Simulator (SNS).
 * SNS is a fast alternative to other network simulators such as ns-3 or OMNeT++,
 * it models different transmission options for ad-hoc communication (ETSI ITS-G5)
 * including single-hop broadcast as well as multi-hop / geo-routing protocols.
 */
public class SnsAmbassador extends AbstractFederateAmbassador {

    /**
     * The transmissionSimulator is the component that performs the actual simple network simulation.
     */
    private TransmissionSimulator transmissionSimulator;

    /**
     * Distance configurations of vehicles which have not been moved yet by the traffic simulator.
     */
    final private Map<String, Double> registeredVehicles = new HashMap<>();

    /**
     * Value for the radius of a single hop extracted from the configuration in the initialization.
     */
    private double singlehopRadius;

    /**
     * Stores the latest {@link VehicleUpdates} interaction, so it can be used
     * to look up the last known position of vehicle which wants to enable its adhoc module.
     */
    private VehicleUpdates latestVehicleUpdates;

    /**
     * Construct the Ambassador.
     *
     * @param ambassadorParameter ambassadorId and configuration.
     */
    public SnsAmbassador(AmbassadorParameter ambassadorParameter) {
        super(ambassadorParameter);
    }

    @Override
    public void initialize(final long startTime, final long endTime) throws InternalFederateException {
        super.initialize(startTime, endTime);
        this.log.info("Init simulation with startTime={}, endTime={}", startTime, endTime);

        if (log.isTraceEnabled()) {
            log.trace("subscribedMessages: {}", Arrays.toString(this.rti.getSubscribedInteractions().toArray()));
        }

        try {
            CSns configuration = new ObjectInstantiation<>(CSns.class, log).readFile(ambassadorParameter.configuration);
            if (configuration.singlehopDelay instanceof GammaSpeedDelay) {
                log.info("Detected GammaSpeedDelay for. The SNS is currently ignoring speed of entities in its evaluation of delay values");
            }
            this.singlehopRadius = configuration.singlehopRadius;
            final RandomNumberGenerator rng = rti.createRandomNumberGenerator();
            transmissionSimulator = new TransmissionSimulator(rng, configuration);
        } catch (InstantiationException e) {
            log.error("Could not read configuration. Reason: {}", e.getMessage());
        }

        log.info("Initialized SNS");
    }

    @Override
    protected void processInteraction(Interaction interaction) throws InternalFederateException {
        try {
            if (interaction.getTypeId().equals(RsuRegistration.TYPE_ID)) {
                this.process((RsuRegistration) interaction);
            } else if (interaction.getTypeId().equals(TrafficLightRegistration.TYPE_ID)) {
                this.process((TrafficLightRegistration) interaction);
            } else if (interaction.getTypeId().equals(ChargingStationRegistration.TYPE_ID)) {
                this.process((ChargingStationRegistration) interaction);
            } else if (interaction.getTypeId().equals(VehicleUpdates.TYPE_ID)) {
                this.process((VehicleUpdates) interaction);
            } else if (interaction.getTypeId().equals(AdHocCommunicationConfiguration.TYPE_ID)) {
                this.process((AdHocCommunicationConfiguration) interaction);
            } else if (interaction.getTypeId().equals(V2xMessageTransmission.TYPE_ID)) {
                this.process((V2xMessageTransmission) interaction);
            } else {
                log.warn("Received unknown interaction={} @time={}", interaction.getTypeId(), TIME.format(interaction.getTime()));
            }
        } catch (Exception e) {
            throw new InternalFederateException(e);
        }
    }

    private void process(RsuRegistration interaction) {
        final RsuMapping applicationRsu = interaction.getMapping();
        if (applicationRsu.hasApplication()) {
            SimulationEntities.INSTANCE.createOrUpdateOfflineNode(applicationRsu.getName(), applicationRsu.getPosition().toCartesian());
            log.info("Added RSU id={} position={} @time={}", applicationRsu.getName(), applicationRsu.getPosition(), TIME.format(interaction.getTime()));
        }
    }

    private void process(TrafficLightRegistration interaction) {
        final TrafficLightMapping applicationTl = interaction.getMapping();
        if (applicationTl.hasApplication()) {
            SimulationEntities.INSTANCE.createOrUpdateOfflineNode(applicationTl.getName(), applicationTl.getPosition().toCartesian());
            log.info("Added TrafficLight id={} position={} @time={}", applicationTl.getName(), applicationTl.getPosition(), TIME.format(interaction.getTime()));
        }
    }

    private void process(ChargingStationRegistration interaction) {
        final ChargingStationMapping applicationCs = interaction.getMapping();
        if (applicationCs.hasApplication()) {
            SimulationEntities.INSTANCE.createOrUpdateOfflineNode(applicationCs.getName(), applicationCs.getPosition().toCartesian());
            log.info("Added ChargingStation id={} position={} @time={}", applicationCs.getName(), applicationCs.getPosition(), TIME.format(interaction.getTime()));
        }
    }

    private void process(VehicleUpdates interaction) {
        for (VehicleData added : interaction.getAdded()) {
            addOrUpdateVehicle(added);
        }
        for (VehicleData updated : interaction.getUpdated()) {
            if (addOrUpdateVehicle(updated) && log.isTraceEnabled()) {
                log.trace("Moved Vehicle id={} to position={} @time={}",
                        updated.getName(), updated.getPosition(), TIME.format(interaction.getTime()));
            }
        }
        latestVehicleUpdates = interaction;
    }

    private void process(AdHocCommunicationConfiguration interaction) {
        AdHocConfiguration configuration = interaction.getConfiguration();
        String nodeId = configuration.getNodeId();
        // Switch the communication modules for simulated nodes on or off
        // (SNS only supports configurations with one radio).
        switch (configuration.getRadioMode()) {
            case OFF -> {
                if (SimulationEntities.INSTANCE.isNodeSimulated(nodeId)) {
                    disableNode(interaction.getTime(), nodeId);
                } else if (registeredVehicles.remove(nodeId) != null) {
                    log.debug("Disabled Wifi of node, which was enabled before, but not yet moved.");
                }
            }
            case SINGLE -> {
                double communicationRadius = this.singlehopRadius;
                if (configuration.getConf0() != null && configuration.getConf0().getRadius() != null) {
                    communicationRadius = configuration.getConf0().getRadius();
                } else {
                    log.debug("Node {} is not configured with a distance value. Using global singlehop radius from SNS configuration.", nodeId);
                }
                if (SimulationEntities.INSTANCE.isNodeSimulated(nodeId)) {
                    SimulationEntities.INSTANCE.enableWifi(nodeId, communicationRadius);
                } else {
                    registeredVehicles.put(nodeId, communicationRadius);
                    if (latestVehicleUpdates != null) {
                        // progress the latest vehicle updates again to eventually activate the adhoc configuration
                        process(latestVehicleUpdates);
                    }
                }
                log.info("Radio configured in mode {} with communication radius {} for node id={} @time={}",
                        configuration.getRadioMode(), communicationRadius, nodeId, TIME.format(interaction.getTime()));
            }
            case DUAL ->log.warn("SNS only supports single radio configuration. Configure first, while ignoring second, radio for node {}.", nodeId);
        }
    }

    private void process(V2xMessageTransmission interaction) throws InternalFederateException {
        RoutingType type = interaction.getMessage().getRouting().getDestination().getRoutingType();
        if (type != RoutingType.AD_HOC_GEOCAST && type != RoutingType.AD_HOC_TOPOCAST) {
            return;
        }
        // Calculate transmission
        Map<String, TransmissionResult> transmissionResults = transmissionSimulator.preProcessInteraction(interaction);
        // send transmission results to rti
        prepareV2xMessageReceptions(transmissionResults, interaction);
    }

    /**
     * Adds or Updates vehicles positions (which is the same from the view of SNS - mainly new positions).
     *
     * @param vehicleData {@link VehicleData}
     * @return {@code true} if vehicle is online and able to send/receive messages {@code false}
     */
    private boolean addOrUpdateVehicle(VehicleData vehicleData) {
        final String vehicleName = vehicleData.getName();

        // During simulation, the regular case: just move vehicles
        if (SimulationEntities.INSTANCE.isNodeOnline(vehicleName)) {
            SimulationEntities.INSTANCE.updateOnlineNode(vehicleName, vehicleData.getProjectedPosition());
            return true;
        }

        if (SimulationEntities.INSTANCE.isNodeOffline(vehicleName)) {
            SimulationEntities.INSTANCE.createOrUpdateOfflineNode(vehicleName, vehicleData.getProjectedPosition());
            return false;
        }

        // In case the AdHocConfiguration arrived earlier than the first VehicleUpdates: create online vehicles
        Double communicationRadius = registeredVehicles.get(vehicleName);
        if (communicationRadius != null) {
            SimulationEntities.INSTANCE.createOnlineNode(vehicleName, vehicleData.getProjectedPosition(), communicationRadius);
            log.info("Added Vehicle id={} position={} @time={}", vehicleData.getName(), vehicleData.getPosition(), TIME.format(vehicleData.getTime()));
            return true;
        }

        return false;
    }

    /**
     * Removes a node from being handled in transmissions.
     *
     * @param nodeId the id of the node to be removed
     */
    private void disableNode(long time, String nodeId) {
        if (UnitNameGenerator.isVehicle(nodeId)) {
            SimulationEntities.INSTANCE.removeNode(nodeId);
            registeredVehicles.remove(nodeId);
            log.info("Removed Node id={} @time={}", nodeId, TIME.format(time));
        } else {
            SimulationEntities.INSTANCE.disableWifi(nodeId);
            log.info("Disabled Wifi for Node id={} @time={}", nodeId, TIME.format(time));
        }
    }

    /**
     * Create the {@link V2xMessageReception}s to be sent to the RTI, the Application Simulator and accordingly
     * applications of possible receivers.
     *
     * @param transmissionResults    Set of successful receivers
     * @param v2xMessageTransmission Originally sent message (for messageId and sending time)
     */
    private void prepareV2xMessageReceptions(Map<String, TransmissionResult> transmissionResults,
                                             V2xMessageTransmission v2xMessageTransmission) throws InternalFederateException {
        if (transmissionResults == null) {
            return;
        }
        for (Map.Entry<String, TransmissionResult> transmissionResultEntry : transmissionResults.entrySet()) {
            if (transmissionResultEntry.getValue().success) {
                long receiveTime = v2xMessageTransmission.getTime() + transmissionResultEntry.getValue().delay;
                if (log.isDebugEnabled()) {
                    log.debug("Receive v2xMessage.id={} on node={} @time={}",
                            v2xMessageTransmission.getMessageId(), transmissionResultEntry.getKey(), TIME.format(receiveTime)
                    );
                }

                final V2xMessageReception v2xMessageReception = new V2xMessageReception(
                        receiveTime,
                        transmissionResultEntry.getKey(),
                        v2xMessageTransmission.getMessageId(),
                        new V2xReceiverInformation(receiveTime).sendTime(v2xMessageTransmission.getTime())
                );
                try {
                    rti.triggerInteraction(v2xMessageReception);
                } catch (IllegalValueException | InternalFederateException e) {
                    throw new InternalFederateException(e);
                }

            }
        }

    }

    @Override
    public void finishSimulation() throws InternalFederateException {
        log.info("Finished simulation");
    }

    @Override
    public boolean isTimeConstrained() {
        return true;
    }

    @Override
    public boolean isTimeRegulating() {
        return true;
    }
}
