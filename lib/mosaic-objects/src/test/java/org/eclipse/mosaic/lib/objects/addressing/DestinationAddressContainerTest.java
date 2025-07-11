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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoRectangle;
import org.eclipse.mosaic.lib.junit.IpResolverRule;

import org.junit.Rule;
import org.junit.Test;

public class DestinationAddressContainerTest {

    @Rule
    public IpResolverRule ipResolverRule = new IpResolverRule();

    private final static GeoCircle GEO_CIRCLE = new GeoCircle(GeoPoint.lonLat(13.0, 52.0), 1000.0d);
    private final static GeoPoint pointA = GeoPoint.lonLat(10.0, 30.0);
    private final static GeoPoint pointB = GeoPoint.lonLat(30.0, 50.0);
    private final static GeoRectangle GEO_RECTANGLE = new GeoRectangle(pointA, pointB);
    private final static byte[] SOME_IP_ADDRESS = {(byte) 0, (byte) 0, (byte) 0, (byte) 1};
    NetworkAddress networkAddress = new NetworkAddress(SOME_IP_ADDRESS);

    @Test
    public void createTopocastDestinationAddress() {
        //SETUP + RUN
        DestinationAddressContainer dac = new DestinationAddressContainer(RoutingType.AD_HOC_TOPOCAST, networkAddress, AdHocChannel.CCH, 1, null, ProtocolType.UDP);

        //ASSERT
        assertEquals(RoutingType.AD_HOC_TOPOCAST, dac.getRoutingType());
        assertEquals(networkAddress, dac.getAddress());
        assertEquals(AdHocChannel.CCH, dac.getAdhocChannelId());
        assertEquals(1, dac.getTimeToLive());
        assertFalse(dac.isGeocast());
        assertNull(dac.getGeoArea());
    }

    @Test
    public void createGeocastDestinationAddressCircle() {
        //SETUP
        GeoPoint center = GeoPoint.lonLat(13.0, 52.0);

        //RUN
        DestinationAddressContainer dac = new DestinationAddressContainer(RoutingType.AD_HOC_GEOCAST, networkAddress, AdHocChannel.CCH, null, GEO_CIRCLE, ProtocolType.UDP);

        //ASSERT
        assertEquals(RoutingType.AD_HOC_GEOCAST, dac.getRoutingType());
        assertEquals(networkAddress, dac.getAddress());
        assertEquals(AdHocChannel.CCH, dac.getAdhocChannelId());
        assertEquals(-1, dac.getTimeToLive());
        assertTrue(dac.isGeocast());
        assertTrue(dac.getGeoArea() instanceof GeoCircle);
        assertEquals(GEO_CIRCLE, dac.getGeoArea());
    }

    @Test
    public void createGeocastDestinationAddressRectangle() {
        //RUN
        DestinationAddressContainer dac = new DestinationAddressContainer(RoutingType.AD_HOC_GEOCAST, networkAddress, AdHocChannel.CCH, null, GEO_RECTANGLE, ProtocolType.UDP);

        //ASSERT
        assertEquals(RoutingType.AD_HOC_GEOCAST, dac.getRoutingType());
        assertEquals(networkAddress, dac.getAddress());
        assertEquals(AdHocChannel.CCH, dac.getAdhocChannelId());
        assertEquals(-1, dac.getTimeToLive());
        assertTrue(dac.isGeocast());
        assertTrue(dac.getGeoArea() instanceof GeoRectangle);
        assertEquals(GEO_RECTANGLE, dac.getGeoArea());
    }

}