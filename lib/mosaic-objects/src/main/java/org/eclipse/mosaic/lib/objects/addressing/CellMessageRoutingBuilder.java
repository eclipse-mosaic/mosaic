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

package org.eclipse.mosaic.lib.objects.addressing;

import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.geo.GeoArea;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.MessageStreamRouting;

import org.apache.commons.lang3.Validate;

import java.net.Inet4Address;

/**
 * Central API for obtaining {@link MessageRouting} for sending {@link org.eclipse.mosaic.lib.objects.v2x.V2xMessage}s via
 * cellular communication.
 */
public class CellMessageRoutingBuilder {

    private final SourceAddressContainer sourceAddressContainer;

    private long streamDuration = -1;
    private long streamBandwidthInBitPs = -1;

    private NetworkAddress destination = null;
    private RoutingType routing = null;
    private GeoArea targetArea = null;

    private boolean destinationChanged = false;
    private boolean routingChanged = false;
    private boolean mbsChanged = false;

    /**
     * The {@link ProtocolType} for the {@link MessageRouting}, on default this will be
     * {@link ProtocolType#UDP}.
     */
    private ProtocolType protocolType = ProtocolType.UDP;
    private boolean protocolChanged = false;

    /**
     * Constructor for {@link CellMessageRoutingBuilder} to set required fields.
     *
     * @param hostName       name of host (source)
     * @param sourcePosition position of source
     */
    public CellMessageRoutingBuilder(String hostName, GeoPoint sourcePosition) {
        Inet4Address address = IpResolver.getSingleton().lookup(hostName);
        if (address == null) {
            throw new IllegalArgumentException("Given hostname " + hostName + " has no registered IP address");
        }

        this.sourceAddressContainer = new SourceAddressContainer(
                new NetworkAddress(address),
                hostName,
                sourcePosition
        );
    }

    /**
     * Creates the MessageRouting based on the methods called on this builder before.
     * @return MessageRouting - The desired routing for a message.
     */
    public MessageRouting build() {
        checkNecessaryValues();
        return this.build(new DestinationAddressContainer(
                routing, destination, null, null, targetArea, protocolType)
        );
    }

    /**
     * Defines stream properties for the message to send.
     *
     * @param streamDuration         The duration of the stream in ns.
     * @param streamBandwidthInBitPs The bandwidth of the stream in bits per second.
     */
    public CellMessageRoutingBuilder streaming(long streamDuration, long streamBandwidthInBitPs) {
        this.streamDuration = streamDuration;
        this.streamBandwidthInBitPs = streamBandwidthInBitPs;
        return this;
    }

    /**
     * Sets the {@link ProtocolType} for the routing.
     *
     * @param type the {@link ProtocolType} to be used
     * @return the {@link CellMessageRoutingBuilder}
     */
    public CellMessageRoutingBuilder protocol(ProtocolType type) {
        Validate.isTrue(!protocolChanged, "Protocol was already set!");
        protocolType = type;
        protocolChanged = true;
        return this;
    }

    /**
     * Sets the {@link ProtocolType} for the routing to {@link ProtocolType#TCP}.
     *
     * @return the {@link CellMessageRoutingBuilder}
     */
    public CellMessageRoutingBuilder tcp() {
        return protocol(ProtocolType.TCP);
    }


    /**
     * Sets the {@link ProtocolType} for the routing to {@link ProtocolType#UDP}.
     *
     * @return the {@link CellMessageRoutingBuilder}
     */
    public CellMessageRoutingBuilder udp() {
        return protocol(ProtocolType.UDP);
    }

    public CellMessageRoutingBuilder destination(NetworkAddress networkAddress) {
        Validate.isTrue(!destinationChanged, "Destination was already set!");
        this.destination = networkAddress;
        this.destinationChanged = true;
        return this;
    }

    /**
     * Sets the destination of the message being built.
     * @param receiverName The string name of the receiving entity.
     * @return this builder.
     */
    public CellMessageRoutingBuilder destination(String receiverName) {
        return destination(IpResolver.getSingleton().nameToIp(receiverName).getAddress());
    }

    /**
     * Sets the destination of the message being built.
     * @param ipAddress The IP address of the target destination as an {@link Inet4Address}.
     * @return this builder.
     */
    public CellMessageRoutingBuilder destination(Inet4Address ipAddress) {
        return destination(new NetworkAddress(ipAddress));
    }

    /**
     * Sets the destination of the message being built.
     * @param ipAddress The IP address of the target destination as an array of bytes.
     * @return this builder.
     */
    public CellMessageRoutingBuilder destination(byte[] ipAddress) {
        return destination(new NetworkAddress(ipAddress));
    }

    /**
     * A convenience method that sets the destination IP address to the broadcast address.
     * @return this builder.
     */
    public CellMessageRoutingBuilder broadcast() {
        return destination(new NetworkAddress(NetworkAddress.BROADCAST_ADDRESS));
    }

    /**
     * Configures the message to use a Multicast/Broadcast Service for transmission.
     * @return this builder.
     */
    public CellMessageRoutingBuilder mbs() {
        Validate.isTrue(!mbsChanged, "MBS was already chosen!");
        Validate.isTrue(!(routing == RoutingType.CELL_TOPOCAST), "MBS can not be enabled for topological routing!");
        routing = RoutingType.CELL_GEOCAST_MBMS;
        mbsChanged = true;
        return this;
    }

    /**
     * Configures the message to use a topologically-scoped routing strategy.
     * @return this builder.
     */
    public CellMessageRoutingBuilder topological() {
        Validate.isTrue(!routingChanged, "Routing was already set!");
        Validate.isTrue(!mbsChanged, "MBS can not be enabled for topological routing!");
        routing = RoutingType.CELL_TOPOCAST;
        routingChanged = true;
        return this;
    }

    /**
     * Configures the message to use a geographically-scoped routing strategy.
     * @param area the area which the message will be transmitted to.
     * @return this builder.
     */
    public CellMessageRoutingBuilder geographical(GeoArea area) {
        Validate.isTrue(!routingChanged, "Routing was already set!");
        if (!mbsChanged) {
            routing = RoutingType.CELL_GEOCAST;
        }
        targetArea = area;
        routingChanged = true;
        return this;
    }

    private void checkNecessaryValues() {
        checkDestination();
        checkRouting();
        checkArea();
    }

    private void checkDestination() {
        if (destination == null) {
            throw new IllegalArgumentException("No destination address was given! Aborting.");
        }
    }

    private void checkRouting() {
        if (routing == null) {
            throw new IllegalArgumentException("No routing protocol was given! Aborting.");
        }
    }

    private void checkArea() {
        if (routing == RoutingType.CELL_GEOCAST_MBMS && targetArea == null) {
            throw new IllegalArgumentException("No target area was given for geographical routing using mbs!"
                    + "Have you called .geographical(GeoArea)? Aborting.");
        }
    }
    private MessageRouting build(DestinationAddressContainer dac) {
        if (streamDuration < 0) {
            return new MessageRouting(dac, sourceAddressContainer);
        } else {
            return new MessageStreamRouting(dac, sourceAddressContainer, streamDuration, streamBandwidthInBitPs);
        }
    }
}
