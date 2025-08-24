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
import org.eclipse.mosaic.fed.sns.model.AdhocTransmissionModel;
import org.eclipse.mosaic.fed.sns.model.TransmissionParameter;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.geo.Area;
import org.eclipse.mosaic.lib.geo.CartesianCircle;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.GeoArea;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.model.transmission.TransmissionResult;
import org.eclipse.mosaic.lib.objects.addressing.DestinationAddressContainer;
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;
import org.eclipse.mosaic.lib.objects.addressing.NetworkAddress;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TransmissionSimulator {

    private final static Logger log = LoggerFactory.getLogger(SnsAmbassador.class);

    /**
     * TTL for 1 (ONE) hop.
     */
    private static final int SINGLE_HOP_TTL = 1;

    /**
     * Reference to config data.
     */
    private final CSns config;

    /**
     * An {@link AdhocTransmissionModel} defined in the configuration. If not defined
     * a {@link org.eclipse.mosaic.fed.sns.model.SimpleAdhocTransmissionModel} will be used.
     */
    private final AdhocTransmissionModel transmissionModel;

    /**
     * {@link RandomNumberGenerator} used for transmission calculation.
     */
    private final RandomNumberGenerator randomNumberGenerator;


    /**
     * Constructor for {@link TransmissionSimulator}, sets the configuration, transmission models & RNG.
     *
     * @param randomNumberGenerator the {@link RandomNumberGenerator} to be used for transmission calculations
     * @throws InternalFederateException if config couldn't be read or was malformed
     */
    @SuppressWarnings(value = "BC_IMPOSSIBLE_INSTANCEOF", justification = "delay field can be serialized with different delay types")
    public TransmissionSimulator(RandomNumberGenerator randomNumberGenerator, CSns config) throws InternalFederateException {
        this.config = config;
        if (config == null) {
            throw new InternalFederateException("Illegal state: TransmissionSimulator initialized before configuration was set.");
        }
        // setup rng
        this.randomNumberGenerator = randomNumberGenerator;
        // set transmission model from configuration
        this.transmissionModel = config.adhocTransmissionModel;
    }

    Map<String, TransmissionResult> preProcessInteraction(V2xMessageTransmission interaction) {
        final String senderName = interaction.getSourceName();
        if (!isValidSender(senderName)) {
            return null;
        }

        DestinationAddressContainer dac = interaction.getMessage().getRouting().getDestination();
        switch (dac.getRoutingType()) {
            case AD_HOC_TOPOCAST:
                if (log.isDebugEnabled()) {
                    log.debug("Send v2xMessage.id={} from node={} as Topocast (singlehop) @time={}",
                            interaction.getMessage().getId(), senderName, TIME.format(interaction.getTime())
                    );
                }
                return sendMessageAsTopocast(senderName, dac);
            case AD_HOC_GEOCAST:
                if (log.isDebugEnabled()) {
                    log.debug( "Send v2xMessage.id={} from={} as Geocast (geo routing) @time={}",
                            interaction.getMessage().getId(), senderName, TIME.format(interaction.getTime())
                    );
                }
                return sendMessageAsGeocast(senderName, dac);
            default:
                log.debug("V2XMessage is not an ad hoc message. Skip this message. V2XMessage.id={}",
                        interaction.getMessage().getId()
                );
                return null;
        }
    }

    /**
     * This method will check if a potential sender qualifies as one.
     *
     * @param senderName name of the sending unit
     * @return {@code true} if sender is valid, else {@code false}
     */
    private boolean isValidSender(String senderName) {
        SimulationNode sender = SimulationEntities.INSTANCE.getOnlineNode(senderName);

        if (sender == null) {
            log.warn("Unit {} is not known, skipping", senderName);
            return false;
        }
        if (sender.getPosition() == null) {
            log.warn("position of the unit is null");
            return false;
        }
        return true;
    }

    /**
     * Simulates topolocically-scoped Unicast (singlehop or multihop transmissions) or Broadcast (only singlehop).
     *
     * @param senderName The Sender of the message.
     * @param dac        {@link DestinationAddressContainer} containing information about the destination for the message.
     * @return a Map containing the summarized transmission results
     */
    protected Map<String, TransmissionResult> sendMessageAsTopocast(String senderName, DestinationAddressContainer dac) {
        final NetworkAddress destinationAddress = dac.getAddress();

        if (destinationAddress.isBroadcast() && dac.getTimeToLive() != SINGLE_HOP_TTL) {
            log.warn("SNS only supports single hop broadcasts. TTL {} will be dismissed and 1 will be used instead.", dac.getTimeToLive());
        }

        final TransmissionParameter transmissionParameter = new TransmissionParameter(
                randomNumberGenerator,
                config.singlehopDelay,
                config.singlehopTransmission,
                getTtl(dac)
        );
        // accumulate all potential receivers in direct communication range
        final SimulationNode sender = SimulationEntities.INSTANCE.getOnlineNode(senderName);

        if (destinationAddress.isBroadcast()) { // SingleHopBroadCast
            final var allPotentialReceivers = getPotentialBroadcastReceivers(getTopocastDestinationArea(sender));
            // remove sender as single radios could not transmit and receive at the same time
            allPotentialReceivers.remove(senderName);
            log.debug("Addressed nodes in destination area={}", allPotentialReceivers);
            // transmission via singlehop broadcast
            return transmissionModel.simulateTopologicalSinglehop(
                    senderName, allPotentialReceivers, transmissionParameter, SimulationEntities.INSTANCE.getAllOnlineNodes()
            );
        } else if (destinationAddress.isUnicast()) {
            final String destinationNodeId = IpResolver.getSingleton().reverseLookup(destinationAddress.getIPv4Address());
            final SimulationNode destination = SimulationEntities.INSTANCE.getOnlineNode(destinationNodeId);

            final boolean isInAreaOfSender = isNodeInArea(destination.getPosition(), getTopocastDestinationArea(sender));
            if (isInAreaOfSender) {
                return transmissionModel.simulateTopologicalSinglehop(
                        senderName, Map.of(destinationNodeId, destination), transmissionParameter, SimulationEntities.INSTANCE.getAllOnlineNodes()
                );
            } else {
                final TransmissionResult result = transmissionModel.simulateTopologicalUnicast(
                        senderName, destinationNodeId, destination, transmissionParameter, SimulationEntities.INSTANCE.getAllOnlineNodes()
                );
                return Map.of(destinationNodeId, result);
            }
        } else {
            log.warn("""
                The SNS only supports SingleHop BroadCasts or MultiHop UniCasts when using Topological routing."
                The given destination address {} is not valid. No message will be send.""", destinationAddress
            );
            return Map.of();
        }
    }

    /**
     * Simulates geocast routing transmission, either broadcast or unicast.
     *
     * @param senderName The Sender of the message.
     * @param dac        {@link DestinationAddressContainer} containing information about the destination for the message.
     * @return a Map containing the summarized transmission results
     */
    protected Map<String, TransmissionResult> sendMessageAsGeocast(String senderName, DestinationAddressContainer dac) {
        if (dac.getGeoArea() == null) {
            log.error("No target area given for Geographic routing. No message will be send.");
            return Map.of();
        }
        final NetworkAddress destinationAddress = dac.getAddress();
        final Area<CartesianPoint> destinationArea = dac.getGeoArea().toCartesian();

        final Map<String, SimulationNode> allReceivers;
        if (destinationAddress.isUnicast()) {
            final String destinationNodeId = IpResolver.getSingleton().reverseLookup(destinationAddress.getIPv4Address());
            if (getPotentialBroadcastReceivers(destinationArea).containsKey(destinationNodeId)) {
                allReceivers = Map.of(destinationNodeId, SimulationEntities.INSTANCE.getOnlineNode(destinationNodeId));
            } else {
                return Map.of();
            }
        } else if (destinationAddress.isBroadcast()){
            allReceivers = getPotentialBroadcastReceivers(destinationArea);
            log.debug("Addressed nodes in destination area={}", allReceivers);
        } else {
            log.warn("""
                The SNS only supports BroadCasts or UniCasts when using geograpical routing."
                The given destination address {} is not valid. No message will be send.""", destinationAddress
            );
            return Map.of();
        }

        // get ttl value, this will be ignored for the simple transmission model
        final TransmissionParameter transmissionParameter = new TransmissionParameter(
                randomNumberGenerator,
                config.singlehopDelay,
                config.singlehopTransmission,
                getTtl(dac)
        );
        return transmissionModel.simulateGeocast(
                senderName, allReceivers, transmissionParameter, SimulationEntities.INSTANCE.getAllOnlineNodes()
        );
    }

    private int getTtl(DestinationAddressContainer dac) {
        if (dac.getTimeToLive() == -1) {
            return config.maximumTtl;
        } else {
            return Math.min(dac.getTimeToLive(), config.maximumTtl);
        }
    }

    private Area<CartesianPoint> getTopocastDestinationArea(SimulationNode nodeData) {
        return new CartesianCircle(nodeData.getPosition(), nodeData.getRadius());
    }

    /**
     * Collects all nodes within the specified destination area.
     *
     * @param destinationArea destination area for transmission
     * @return a map containing the
     */
    private static Map<String, SimulationNode> getPotentialBroadcastReceivers(Area<CartesianPoint> destinationArea) {
        return getEntitiesInArea(SimulationEntities.INSTANCE.getAllOnlineNodes(), destinationArea);
    }

    /**
     * This method collects all entities, that are within the given {@link GeoArea}.
     * It is static because it is also required in some of the {@link AdhocTransmissionModel}s.
     *
     * @param relevantEntities a map of all entities and their names, which should be checked against the area
     * @param range            the {@link GeoArea} within which the entities should be.
     *                         It is called "range" because it reflects the communication range
     * @return A map of the given entities, which are in the destination area.
     */
    public static Map<String, SimulationNode> getEntitiesInArea(Map<String, SimulationNode> relevantEntities, Area<CartesianPoint> range) {
        final Map<String, SimulationNode> results = new HashMap<>();
        for (var entityEntry : relevantEntities.entrySet()) {
            if (range.contains(entityEntry.getValue().getPosition())) {
                results.put(entityEntry.getKey(), entityEntry.getValue());
            }
        }
        return results;
    }

    private boolean isNodeInArea(CartesianPoint nodePosition, Area<CartesianPoint> destinationArea) {
        if (nodePosition == null) {
            log.warn("position of the unit is null");
            return false;
        }
        return destinationArea.contains(nodePosition);
    }
}
