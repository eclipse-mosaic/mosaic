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

package org.eclipse.mosaic.lib.objects.v2x;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoRectangle;
import org.eclipse.mosaic.lib.junit.IpResolverRule;
import org.eclipse.mosaic.lib.objects.addressing.AdHocMessageRoutingBuilder;
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;
import org.eclipse.mosaic.lib.objects.addressing.NetworkAddress;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AdHocMessageRoutingBuilderTest {

    @Rule
    public IpResolverRule ipResolverRule = new IpResolverRule();

    private AdHocMessageRoutingBuilder builder;

    private final GeoCircle geoCircle = new GeoCircle(GeoPoint.lonLat(1.0, 1.0), 1);
    private final GeoRectangle geoRectangle = new GeoRectangle(GeoPoint.lonLat(1.0, 1.0), GeoPoint.lonLat(2.0, 2.0));
    private final AdHocChannel adHocChannel = AdHocChannel.CCH;
    private final byte[] ipAddress = new byte[]{(byte) 127, (byte) 0, (byte) 0, (byte) 1};
    private final NetworkAddress destinationAddress = new NetworkAddress(ipAddress);
    private int hops = 3;
    //This variable is package-private in the class we are testing, so we just repeat it here
    final static int MAXIMUM_TTL = 255;

    @Before
    public void setup() {
        IpResolver.getSingleton().registerHost("veh_0");

        builder = new AdHocMessageRoutingBuilder("veh_0", null);
    }

    /**
     * Test all possible ttl values.
     */
    @Test
    public void testAllTTLValues() {
        for (int i = 1; i <= MAXIMUM_TTL; ++i) {
            builder = new AdHocMessageRoutingBuilder("veh_0", null);
            MessageRouting routing = builder.channel(adHocChannel).destination(ipAddress).topological().hops(i).build();
            assertEquals(i, routing.getDestination().getTimeToLive());
        }
    }

    /**
     * Test to set a ttl under the limit.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testLowerTTL() {
        builder.channel(adHocChannel).destination(ipAddress).topological().hops(-1);
    }

    /**
     * Test to set a ttl over the limit.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testHigherTTL() {
        builder.channel(adHocChannel).destination(ipAddress).topological().hops(256);
    }

    @Test
    public void geoBroadcastCircle() {
        // run
        MessageRouting routing = builder.broadcast().geographical(geoCircle).build();

        // assert
        assertEquals(RoutingType.AD_HOC_GEOCAST, routing.getDestination().getRoutingType());
        assertTrue(routing.getDestination().isGeocast());
        assertTrue(routing.getDestination().getGeoArea() instanceof GeoCircle);
        assertTrue(routing.getDestination().getAddress().isBroadcast());
        assertEquals(routing.getDestination().getAdhocChannelId(), adHocChannel);
    }

    @Test
    public void geoBroadcastRectangle() {
        // run
        MessageRouting routing = builder.broadcast().geographical(geoRectangle).build();

        // assert
        assertEquals(RoutingType.AD_HOC_GEOCAST, routing.getDestination().getRoutingType());
        assertTrue(routing.getDestination().isGeocast());
        assertTrue(routing.getDestination().getGeoArea() instanceof GeoRectangle);
        assertTrue(routing.getDestination().getAddress().isBroadcast());
        assertEquals(routing.getDestination().getAdhocChannelId(), adHocChannel);
    }

    @Test
    public void geoBroadcastCircleAdHocChannel() {
        // run
        MessageRouting routing = builder.channel(adHocChannel).broadcast().geographical(geoCircle).build();

        // assert
        assertEquals(RoutingType.AD_HOC_GEOCAST, routing.getDestination().getRoutingType());
        assertTrue(routing.getDestination().isGeocast());
        assertTrue(routing.getDestination().getGeoArea() instanceof GeoCircle);
        assertTrue(routing.getDestination().getAddress().isBroadcast());
        assertEquals(routing.getDestination().getAdhocChannelId(), adHocChannel);
    }

    @Test
    public void geoBroadcastrectangleAdHocChannel() {
        // run
        MessageRouting routing = builder.channel(adHocChannel).broadcast().geographical(geoRectangle).build();

        // assert
        assertEquals(RoutingType.AD_HOC_GEOCAST, routing.getDestination().getRoutingType());
        assertTrue(routing.getDestination().isGeocast());
        assertTrue(routing.getDestination().getGeoArea() instanceof GeoRectangle);
        assertTrue(routing.getDestination().getAddress().isBroadcast());
        assertEquals(routing.getDestination().getAdhocChannelId(), adHocChannel);
    }

    @Test
    public void geocast() {
        // run
        MessageRouting routing = builder.channel(adHocChannel).destination(destinationAddress).geographical(geoCircle).build();

        // assert
        assertEquals(RoutingType.AD_HOC_GEOCAST, routing.getDestination().getRoutingType());
        assertTrue(routing.getDestination().isGeocast());
        assertTrue(routing.getDestination().getGeoArea() instanceof GeoCircle);
        assertTrue(routing.getDestination().getAddress().isUnicast());
        assertEquals(routing.getDestination().getAdhocChannelId(), adHocChannel);
    }

    @Test
    public void topoBroadcastAdHocChannel() {
        // run
        MessageRouting routing = builder.channel(adHocChannel).singlehop().broadcast().topological().build();

        // assert
        assertEquals(RoutingType.AD_HOC_TOPOCAST, routing.getDestination().getRoutingType());
        assertFalse(routing.getDestination().isGeocast());
        assertTrue(routing.getDestination().getAddress().isBroadcast());
        assertEquals(1, routing.getDestination().getTimeToLive());
        assertTrue(routing.getDestination().getAddress().isBroadcast());
        assertEquals(routing.getDestination().getAdhocChannelId(), adHocChannel);
    }

    @Test
    public void topoBroadcastAdHocChannelHops() {
        // run
        MessageRouting routing = builder.channel(adHocChannel).broadcast().topological().hops(hops).build();

        // assert
        assertEquals(RoutingType.AD_HOC_TOPOCAST, routing.getDestination().getRoutingType());
        assertFalse(routing.getDestination().isGeocast());
        assertEquals(hops, routing.getDestination().getTimeToLive());
        assertTrue(routing.getDestination().getAddress().isBroadcast());
        assertEquals(routing.getDestination().getAdhocChannelId(), adHocChannel);
    }

}