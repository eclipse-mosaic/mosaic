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

package org.eclipse.mosaic.fed.cell.module;

import org.eclipse.mosaic.fed.cell.chain.ChainManager;
import org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties;
import org.eclipse.mosaic.fed.cell.config.model.TransmissionMode;
import org.eclipse.mosaic.fed.cell.data.SimulationData;
import org.eclipse.mosaic.fed.cell.message.CellModuleMessage;
import org.eclipse.mosaic.fed.cell.message.GeocasterResult;
import org.eclipse.mosaic.fed.cell.message.StreamResult;
import org.eclipse.mosaic.fed.cell.utility.RegionUtility;
import org.eclipse.mosaic.interactions.communication.V2xMessageAcknowledgement;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.enums.NegativeAckReason;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.geo.GeoArea;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.objects.addressing.DestinationAddressContainer;
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;
import org.eclipse.mosaic.lib.objects.addressing.NetworkAddress;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This module is the turning point between Up- and Downlink and is responsible for
 * message casting and routing according to Topocast, GeoUnicast and GeoBroadcast.
 */
public final class GeocasterModule extends CellModule {

    private static final Logger log = LoggerFactory.getLogger(GeocasterModule.class);

    private final RandomNumberGenerator rng;
    private long processedMessages = 0;

    /**
     * Creates a new {@link GeocasterModule} object.
     *
     * @param chainManager Object to handle the interaction between modules and MOSAIC.
     */
    public GeocasterModule(ChainManager chainManager) {
        super(CellModuleNames.GEOCASTER, chainManager);
        this.rng = chainManager.getRandomNumberGenerator();
    }

    @Override
    public void processEvent(Event event) {
        // The Geocaster gets messages from the Upstream- and prepares them for the DownstreamModule
        Object resource = event.getResource();
        if (resource == null) {
            throw new RuntimeException("No input message (event resource) for " + moduleName);
        } else if (resource instanceof CellModuleMessage cellModuleMessage) {
            processMessage(cellModuleMessage, event.getTime());
        } else {
            throw new RuntimeException("Unsupported input message (event resource) for " + moduleName);
        }
    }

    /**
     * Processes the message of the cell module depending on the transmission mode (CellTopocast, CellGeoUnicast, CellGeoBroadcast)
     * for cellular communication.
     *
     * @param message Internal message includes the result of the transmission.
     * @param time    Time when the module received the message.
     */
    private void processMessage(CellModuleMessage message, long time) {
        log.debug("t={}: Entering processMessage() of module {}",
                TIME.format(time), getModuleName());

        StreamResult streamResult = message.getResource();
        V2xMessage v2xMessage = streamResult.getV2xMessage();
        DestinationAddressContainer dac = v2xMessage.getRouting().getDestination();
        RoutingType type = dac.getRoutingType();
        NetworkAddress address = dac.getAddress();
        ProtocolType protocol = dac.getProtocolType();

        boolean isFullMessage = true;
        if (message.getEmittingModule().equals(CellModuleNames.UPSTREAM_MODULE)) {
            isFullMessage = false;
        }
        String nextModule = CellModuleNames.DOWNSTREAM_MODULE; // next module after Geocaster in basic configuration is Downstream
        // Cast according to the schemes of 1) Topocast, 2) GeoUnicast and 3) GeoBroadcast
        if (type.equals(RoutingType.CELL_TOPOCAST) && address.isUnicast()) {
            // Topocast only allows unicasts, but any protocols (tcp, udp)
            geocasterCellTopocast(time, nextModule, streamResult, isFullMessage);
        } else if (type.equals(RoutingType.CELL_GEOCAST) && address.isBroadcast() && !protocol.equals(ProtocolType.TCP)) {
            // Geocasts require broadcast, but don't allow tcp (as ack for broadcasts is denied)
            geocasterCellGeoUnicast(time, nextModule, streamResult, isFullMessage);
        } else if (type.equals(RoutingType.CELL_GEOCAST_MBMS) && address.isBroadcast() && !protocol.equals(ProtocolType.TCP)) {
            // Geocasts require broadcast, but don't allow tcp (as ack for broadcasts is denied)
            geocasterCellGeoBroadcast(time, nextModule, streamResult, isFullMessage);
        } else {
            unknownDestinationType(time, streamResult);
        }
        processedMessages++;
    }

    /**
     * This method allows to resolve the correct region in order to send message
     * while using the next modules as hop.
     *
     * @param time          Time when the module received the message.
     * @param nextModule    The next module to hop.
     * @param streamResult  StreamResult from previous module.
     * @param isFullMessage true if the message object contains the V2X message
     */
    private void geocasterCellTopocast(long time, String nextModule, StreamResult streamResult, boolean isFullMessage) {
        V2xMessage v2xMessage = streamResult.getV2xMessage();
        DestinationAddressContainer dac = v2xMessage.getRouting().getDestination();
        NetworkAddress address = dac.getAddress();

        // In Topocast ("normal" unicast), resolve the single receiver via IPResolver
        final String receiverId;
        receiverId = IpResolver.getSingleton().reverseLookup(address.getIPv4Address());

        // Check whether the receiving node is known...
        if (receiverId != null && SimulationData.INSTANCE.containsCellConfigurationOfNode(receiverId)) {
            // ...and match the correct region (for later delay/loss calculation)
            CNetworkProperties region = RegionUtility.getRegionForNode(receiverId);
            final Multimap<CNetworkProperties, String> receivers = ArrayListMultimap.create(1, 1);
            receivers.put(region, receiverId);
            GeocasterResult geocasterResult = new GeocasterResult(
                    receivers,
                    TransmissionMode.DownlinkUnicast,
                    v2xMessage,
                    isFullMessage
            );
            CellModuleMessage resultMessage = new CellModuleMessage.Builder(CellModuleNames.GEOCASTER, nextModule)
                    .startTime(time)
                    .endTime(time)
                    .resource(geocasterResult)
                    .build();
            logResult(resultMessage);
            if (log.isDebugEnabled()) {
                logResult(resultMessage);
            }
            chainManager.finishEvent(resultMessage);
        }
    }

    /**
     * This methods allows to resolve the correct geographical region in order to send message
     * to the specific receiver in this region.
     *
     * @param time          Time when the module received the message.
     * @param nextModule    The next module to hop.
     * @param streamResult  StreamResult from previous module.
     * @param isFullMessage true if the message object contains the V2X message
     */
    private void geocasterCellGeoUnicast(long time, String nextModule, StreamResult streamResult, boolean isFullMessage) {
        V2xMessage v2xMessage = streamResult.getV2xMessage();
        DestinationAddressContainer dac = v2xMessage.getRouting().getDestination();

        // In GeoUnicast (every node in the destination area is addressed individually),
        // First, get all nodes in this destination area
        GeoArea geoArea;
        if (dac.isGeocast()) {
            geoArea = dac.getGeoArea();
        } else {
            throw new IllegalArgumentException("Destination address container doesn't contain any geo area!");
        }
        List<String> receiverNodes = RegionUtility.getNodesForDestinationArea(Objects.requireNonNull(geoArea));
        log.debug("CellGeoUnicast receiverNodes={}", receiverNodes);
        // shuffle for fairness, when capacity is exceeded and certain destination nodes are unable to receive
        rng.shuffle(receiverNodes);
        log.debug("CellGeoUnicast receiverNodes(shuffled)={}", receiverNodes);

        // Second, match the regions, which can be multiple ones as there are also multiple receivers
        final Multimap<CNetworkProperties, String> receivers = ArrayListMultimap.create();
        for (String receiver : receiverNodes) {
            CNetworkProperties region = RegionUtility.getRegionForNode(receiver);
            receivers.put(region, receiver);
        }
        GeocasterResult geocasterResult = new GeocasterResult(receivers, TransmissionMode.DownlinkUnicast, v2xMessage, isFullMessage);
        CellModuleMessage resultMessage = new CellModuleMessage.Builder(CellModuleNames.GEOCASTER, nextModule)
                .startTime(time).endTime(time).resource(geocasterResult).build();
        if (log.isDebugEnabled()) {
            logResult(resultMessage);
        }
        chainManager.finishEvent(resultMessage);
    }

    /**
     * This methods allows to send a broadcast message to all nodes in a specific geographical area.
     *
     * @param time          Time when the module received the message.
     * @param nextModule    The next module to hop.
     * @param streamResult  StreamResult from previous module.
     * @param isFullMessage true if the message object contains the V2X message
     */
    private void geocasterCellGeoBroadcast(long time, String nextModule, StreamResult streamResult, boolean isFullMessage) {
        V2xMessage v2xMessage = streamResult.getV2xMessage();
        DestinationAddressContainer dac = v2xMessage.getRouting().getDestination();

        // In GeoBroadcast (which is basically MBMS - one broadcast to all nodes in the regions),
        // First, get all regions that are covered by the destination area
        GeoArea geoArea;
        if (dac.isGeocast()) {
            geoArea = dac.getGeoArea();
        } else {
            throw new IllegalArgumentException("Destination address container doesn't contain any geo area!");
        }
        List<CNetworkProperties> regions = RegionUtility.getRegionsForDestinationArea(geoArea);
        if (log.isDebugEnabled()) {
            List<String> regionIds = new ArrayList<>();
            for (CNetworkProperties region : regions) {
                regionIds.add(region.id);
            }
            log.debug("CellGeoBroadcast recRegions={}", regionIds);
        }

        // Second, match the receivers (which only used for multiplying the messages towards the application,
        // on the network, there will be only one message for all receiver in the region)
        final Multimap<CNetworkProperties, String> receivers = ArrayListMultimap.create();
        for (CNetworkProperties region : regions) {
            receivers.putAll(region, RegionUtility.getNodesForRegion(region));
        }

        GeocasterResult geocasterResult = new GeocasterResult(receivers, TransmissionMode.DownlinkMulticast, v2xMessage, isFullMessage);
        CellModuleMessage resultMessage = new CellModuleMessage.Builder(CellModuleNames.GEOCASTER, nextModule)
                .startTime(time)
                .endTime(time)
                .resource(geocasterResult)
                .build();
        if (log.isDebugEnabled()) {
            logResult(resultMessage);
        }
        chainManager.finishEvent(resultMessage);
    }

    /**
     * The message can not be routed to the destination.
     *
     * @param time         Time when the module received the message.
     * @param streamResult The next module to hop.
     */
    private void unknownDestinationType(long time, StreamResult streamResult) {
        V2xMessage v2xMessage = streamResult.getV2xMessage();
        DestinationAddressContainer dac = v2xMessage.getRouting().getDestination();
        RoutingType type = dac.getRoutingType();
        NetworkAddress address = dac.getAddress();

        // Default case with, up to now, unknown casting scheme
        log.debug(" msg-{} (from {} to {} as {}) IS NOT able to cast and route",
                v2xMessage.getId(),
                v2xMessage.getRouting().getSource().getSourceName(),
                address, type);

        // if tcp is used inform the sender
        if (v2xMessage.getRouting().getDestination().getProtocolType().equals(ProtocolType.TCP)) {
            List<NegativeAckReason> reason = new ArrayList<>();
            reason.add(NegativeAckReason.ADDRESS_ROUTING_ERROR);
            // Set all unable messages (include nackMessage) to not transmitForward
            // In case of TCP, informSender with Nack when message is not transmittedForward
            // (when message can be transmittedForward, just transmit it without information to the sender)
            chainManager.sendInteractionToRti(
                    new V2xMessageAcknowledgement(time, v2xMessage, reason)
            );
        }
    }

    @Override
    public long getProcessedMessages() {
        return processedMessages;
    }

    /**
     * Logs the result of the message from the cell module with all processed steps.
     *
     * @param resultMessage Internal message includes the result of the transmission.
     */
    private void logResult(CellModuleMessage resultMessage) {
        GeocasterResult geocasterMessage = resultMessage.getResource();
        V2xMessage v2xMessage = geocasterMessage.getV2xMessage();
        if (v2xMessage != null) {
            log.debug(" msg-{} (from {}) IS casted to:", v2xMessage.getId(),
                    v2xMessage.getRouting().getSource().getSourceName());
        }
        //the resource is not null because it was either set, or if it is not set shouldTransmitForward is set to false
        GeocasterResult geocasterResult = resultMessage.getResource();
        for (CNetworkProperties region : geocasterResult.getReceivers().keySet()) {
            log.debug(" receiver(s) {} in downstream region \"{}\"",
                    geocasterResult.getReceivers().get(region), region.id);
        }
        log.debug("Give the message message to the following module: {}", resultMessage.getNextModule());
    }
}
