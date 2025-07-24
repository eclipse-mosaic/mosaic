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
import org.eclipse.mosaic.lib.enums.AddressType;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.geo.GeoArea;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A container for any type of destination network address (Topocast or Geocast). Since it has variables for both types,
 * you should be careful about using getters because it can lead to NullPointerExceptions.
 */
@Immutable
public class DestinationAddressContainer implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private final NetworkAddress destinationAddress;
    private final RoutingType type;

    /**
     * Time to live (TTL). Unitless.
     */
    private final Integer timeToLive;

    /**
     * The destination area can be either a circle or a rectangle.
     */
    private final GeoArea disseminationArea;

    /**
     * Maybe a channel id is set (currently only for AdHoc).
     */
    private final AdHocChannel adHocChannel;
    
    /**
     * The transport protocol (TCP or UDP).
     */
    private final ProtocolType protocolType;

    /**
     * Creates a new {@link DestinationAddressContainer}.
     *
     * @param routingType a destination type describing the addressing schema
     * @param destinationAddress  destination address in IpV4
     * @param adHocChannel a AdHoc channel on the 5.9 GHz Band {@link AdHocChannel}
     */
    public DestinationAddressContainer(
            RoutingType routingType,
            NetworkAddress destinationAddress,
            AdHocChannel adHocChannel,
            Integer timeToLive,
            GeoArea disseminationArea,
            ProtocolType protocolType
    ) {
        // Only use a TopocastDestinationAddress or a GeocastDestinationAddress.
        this.destinationAddress = Objects.requireNonNull(destinationAddress);
        this.type = Objects.requireNonNull(routingType);
        this.adHocChannel = adHocChannel;
        this.timeToLive = timeToLive;
        this.disseminationArea = disseminationArea;
        this.protocolType = protocolType;
    }

    @Nonnull
    public NetworkAddress getAddress() {
        return destinationAddress;
    }

    public AddressType getAddressType() {
        return AddressType.getEnum(destinationAddress);
    }

    @Nonnull
    public RoutingType getRoutingType() {
        return type;
    }
    
    @Nullable
    public AdHocChannel getAdhocChannelId() {
        return adHocChannel;
    }
    
    public ProtocolType getProtocolType() {
        return protocolType;
    }

    public boolean isGeocast() {
        return this.disseminationArea != null;
    }

    @Nullable
    public GeoArea getGeoArea() {
        return this.disseminationArea;
    }

    /**
     * Get the Time to live (TTL). Unitless.
     *
     * @return The Time to live (TTL). {@code -1} if no TTL given.
     */
    public int getTimeToLive() {
        return timeToLive != null ? timeToLive : -1;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 41)
                        .append(destinationAddress)
                        .append(type)
                        .append(adHocChannel)
                        .append(protocolType)
                        .append(disseminationArea)
                        .append(timeToLive)
                        .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false; 
        if (obj == this) return true;
        if (obj.getClass() != getClass()) return false;
        
        DestinationAddressContainer other = (DestinationAddressContainer) obj;
        return new EqualsBuilder()
                .append(this.destinationAddress, other.destinationAddress)
                .append(this.type, other.type)
                .append(this.adHocChannel, other.adHocChannel)
                .append(this.protocolType, other.protocolType)
                .append(this.disseminationArea, other.disseminationArea)
                .append(this.timeToLive, other.timeToLive)
                .isEquals();
    }

    @Override
    public String toString() {
        return "DestinationAddressContainer{" + "address=" + destinationAddress + ", type=" + type
                + ", channelId=" + adHocChannel + ", protocolType=" + protocolType
                + ", timeToLive=" + timeToLive + ", disseminationArea=" + disseminationArea + '}';
    }
    
}
