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

import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.geo.GeoArea;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;

import org.apache.commons.lang3.Validate;

import java.net.Inet4Address;

/**
 * Central API for obtaining {@link MessageRouting} for sending {@link org.eclipse.mosaic.lib.objects.v2x.V2xMessage}s via
 * ad hoc communication.
 */
public class AdHocMessageRoutingBuilder {

    /**
     * The maximum time to live (TTL).
     */
    private final static int MAXIMUM_TTL = 255;

    private final SourceAddressContainer sourceAddressContainer;

    private AdHocChannel channel = AdHocChannel.CCH;
    private NetworkAddress destination = null;
    private Integer hops = MAXIMUM_TTL;
    private RoutingType routing = null;
    private GeoArea targetArea = null;

    private boolean channelChanged = false;
    private boolean destinationChanged = false;
    private boolean routingChanged = false;
    private boolean hopsChanged = false;

    /**
     * The constructor for {@link AdHocMessageRoutingBuilder}.
     *
     * @param hostName       name of the sending entity
     * @param sourcePosition position of the sending entity
     */
    public AdHocMessageRoutingBuilder(String hostName, GeoPoint sourcePosition) {
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
     * Build a {@link MessageRouting} object based on the values configured through the AdHocMessageRoutingBuilder.
     * Needs at least the destination and the routing strategy to have been set.
     * @return {@link MessageRouting}
     */
    public MessageRouting build() {
        checkNecessaryValues();
        return new MessageRouting(new DestinationAddressContainer(
                routing, destination, channel, hops, targetArea, ProtocolType.UDP),
                sourceAddressContainer);
    }

    /**
     * Build a {@link MessageRouting} object based on the given {@link DestinationAddressContainer}.
     * @return {@link MessageRouting}
     */
    public MessageRouting build(DestinationAddressContainer dac) {
        return new MessageRouting(dac, sourceAddressContainer);
    }

    /**
     * Sets a specific {@link AdHocChannel} for the {@link MessageRouting}.
     *
     * @param adHocChannel specific ad hoc channel {@link AdHocChannel}
     * @return this builder
     */
    public AdHocMessageRoutingBuilder channel(AdHocChannel adHocChannel) {
        Validate.isTrue(!channelChanged, "Channel has already been set!");
        this.channel = adHocChannel;
        this.channelChanged = true;
        return this;
    }

    /**
     * Sets the destination of the message being built.
     * @param ipAddress The IP address of the target destination as an array of bytes.
     * @return this builder.
     */
    public AdHocMessageRoutingBuilder destination(byte[] ipAddress) {
        return destination(new NetworkAddress(ipAddress));
    }

    /**
     * Sets the destination of the message being built.
     * @param ipAddress The IP address of the target destination as an {@link Inet4Address}.
     * @return this builder.
     */
    public AdHocMessageRoutingBuilder destination(Inet4Address ipAddress) {
        return destination(new NetworkAddress(ipAddress));
    }

    /**
     * Sets the destination of the message being built.
     * @param receiverName The string name of the receiving entity.
     * @return this builder.
     */
    public AdHocMessageRoutingBuilder destination(String receiverName) {
        return destination(IpResolver.getSingleton().nameToIp(receiverName));
    }

    /**
     * Sets the destination of the message being built.
     * @param ipAddress The IP address of the target destination as a {@link NetworkAddress}.
     * @return this builder.
     */
    public AdHocMessageRoutingBuilder destination(NetworkAddress ipAddress) {
        Validate.isTrue(!destinationChanged, "Destination has already been set!");
        this.destination = ipAddress;
        this.destinationChanged = true;
        return this;
    }

    /**
     * A convenience method that sets the destination IP address to the broadcast address.
     * @return this builder.
     */
    public AdHocMessageRoutingBuilder broadcast() {
        return destination(new NetworkAddress(NetworkAddress.BROADCAST_ADDRESS));
    }

    /**
     * Configures the message to use a topologically-scoped routing strategy.
     * @return this builder.
     */
    public AdHocMessageRoutingBuilder topological() {
        Validate.isTrue(!routingChanged, "Routing strategy has already been set!");
        this.routing = RoutingType.AD_HOC_TOPOCAST;
        this.routingChanged = true;
        return this;
    }

    /**
     * Configures the message to use a geographically-scoped routing strategy.
     * @param area the area which the message will be transmitted to.
     * @return this builder.
     */
    public AdHocMessageRoutingBuilder geographical(GeoArea area) {
        Validate.isTrue(!routingChanged, "Routing strategy has already been set!");
        this.routing = RoutingType.AD_HOC_GEOCAST;
        this.targetArea = area;
        this.routingChanged = true;
        return this;
    }

    /**
     * Sets the maximum number of hops in the routing to the given number.
     * @param hops the maximum number of hops that should be possible in routing.
     * @return this builder.
     */
    public AdHocMessageRoutingBuilder hops(int hops) {
        Validate.isTrue(!hopsChanged, "Hops have already been set!");
        this.hops = require8BitTtl(hops);
        this.hopsChanged = true;
        return this;
    }

    /**
     * A convenience method that sets the maximum number of hops in the routing to one.
     * @return this builder.
     */
    public AdHocMessageRoutingBuilder singlehop() {
        hops(1);
        return this;
    }

    private void checkNecessaryValues() {
        checkDestination();
        checkRouting();
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

    private static int require8BitTtl(final int ttl) {
        if (ttl == 0) {
            throw new IllegalArgumentException("TTL can't be zero!");
        }
        if (ttl > MAXIMUM_TTL || ttl < 0) {
            throw new IllegalArgumentException("Passed time to live shouldn't exceed 8-bit limit!");
        }
        return ttl;
    }
}
