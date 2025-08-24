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

package org.eclipse.mosaic.lib.objects.communication;

import org.eclipse.mosaic.lib.enums.AdHocChannel;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * Represents the configuration for an interface on the vehicle
 * If this is not null, the interface exists but may still be configured to only
 * participate in routing and not receive actual messages via the turnedOn = false flag.
 */
@Immutable
public class InterfaceConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The IPv4 address to assign.
     */
    private final Inet4Address ip;
    /**
     * The subnet belonging to the IP address.
     */
    private final Inet4Address subnet;
    /**
     * The transmission power of the radio belonging to the interface.
     * Positive integer - power in mW
     */
    private final Double power;

    /**
     * The transmission radius of the radio belonging to the interface.
     */
    private final Double radius;

    /**
     * The list of channel (max size = 2).
     */
    private final List<AdHocChannel> channels = new ArrayList<>(2);

    /**
     * Create a configuration for a single channel interface.
     *
     * @param ip        the Ip address to assign to the interface
     * @param subnet    the subnet specification for the interface address
     * @param power     the transmission power which the radio belonging to this interface should send (in mW)
     */
    private InterfaceConfiguration(@Nonnull Inet4Address ip, @Nonnull Inet4Address subnet,
                                   Double power, Double radius, List<AdHocChannel> channels) {
        Validate.isTrue(channels.size() >= 1 && channels.size() <= 2, "Either single or dual channel");
        this.ip = Objects.requireNonNull(ip);
        this.subnet = Objects.requireNonNull(subnet);
        this.power = power;
        this.radius = radius;
        this.channels.addAll(channels);
        Validate.isTrue(power != null || radius != null, "Either power or radius must not be null");
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 97)
                .append(ip)
                .append(subnet)
                .append(power)
                .append(channels)
                .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        InterfaceConfiguration other = (InterfaceConfiguration) obj;
        return new EqualsBuilder()
                .append(this.ip, other.ip)
                .append(this.subnet, other.subnet)
                .append(this.power, other.power)
                .append(this.channels, other.channels)
                .isEquals();
    }

    public Inet4Address getIp() {
        return ip;
    }

    public Inet4Address getSubnet() {
        return subnet;
    }

    public double getPower() {
        return power;
    }

    public Double getRadius() {
        return radius;
    }

    public MultiChannelMode getMode() {
        if (channels.size() == 1) {
            return MultiChannelMode.SINGLE;
        }
        return MultiChannelMode.ALTERNATING;
    }

    public AdHocChannel getChannel0() {
        return Iterables.getFirst(channels, null);
    }

    public AdHocChannel getChannel1() {

        return Iterables.get(channels, 1, null);
    }

    /**
     * This is not actively used yet, since we always have single channel radios.
     * Nevertheless the enum is respected in the WLANModule and OmnetppProxy and may be moved outside
     * the InterfaceConfiguration once a multichannel implementation exists.
     */
    public enum MultiChannelMode {
        SINGLE(1), ALTERNATING(2);
        private final int id;

        MultiChannelMode(int id) {
            this.id = id;
        }

        public int getValue() {
            return id;
        }
    }

    public static class Builder {

        private Inet4Address ip;
        private Inet4Address subnet;
        private Double power;
        private Double radius;
        private AdHocChannel channel0;
        private AdHocChannel channel1;

        public Builder(AdHocChannel channel0) {
            this.channel0 = Validate.notNull(channel0);
        }

        public Builder ip(Inet4Address ip) {
            this.ip = ip;
            return this;
        }

        public Builder subnet(Inet4Address subnet) {
            this.subnet = subnet;
            return this;
        }

        public Builder power(Double power) {
            this.power = power;
            return this;
        }

        public Builder radius(Double radius) {
            this.radius = radius;
            return this;
        }

        public Builder secondChannel(AdHocChannel channel) {
            this.channel1 = channel;
            return this;
        }

        public InterfaceConfiguration create() {
            List<AdHocChannel> channels = Lists.newArrayList(channel0);
            if (channel1 != null) {
                channels.add(channel1);
            }
            return new InterfaceConfiguration(ip, subnet, power, radius, channels);
        }
    }
}