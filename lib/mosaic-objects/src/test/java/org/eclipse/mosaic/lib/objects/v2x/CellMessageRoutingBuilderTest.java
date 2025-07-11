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

import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoRectangle;
import org.eclipse.mosaic.lib.junit.IpResolverRule;
import org.eclipse.mosaic.lib.objects.addressing.CellMessageRoutingBuilder;
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CellMessageRoutingBuilderTest {

    @Rule
    public IpResolverRule ipResolverRule = new IpResolverRule();

    private CellMessageRoutingBuilder builder;

    private final GeoCircle geoCircle = new GeoCircle(GeoPoint.lonLat(1.0, 1.0), 1);
    private final GeoRectangle geoRectangle = new GeoRectangle(GeoPoint.lonLat(1.0, 1.0), GeoPoint.lonLat(2.0, 2.0));
    private final byte[] ipAddress = new byte[]{(byte) 127, (byte) 0, (byte) 0, (byte) 1};

    @Before
    public void setup() {
        IpResolver.getSingleton().registerHost("veh_0");

        builder = new CellMessageRoutingBuilder("veh_0", null);
    }

    @Test
    public void geoBroadcastCircle() {
        // run
        MessageRouting routing = builder.broadcast().geographical(geoCircle).build();

        // assert
        assertEquals(RoutingType.CELL_GEOCAST, routing.getDestination().getRoutingType());
        assertTrue(routing.getDestination().isGeocast());
        assertTrue(routing.getDestination().getGeoArea() instanceof GeoCircle);
        assertTrue(routing.getDestination().getAddress().isBroadcast());
    }

    @Test
    public void geoBroadcastRectangle() {
        // run
        MessageRouting routing = builder.broadcast().geographical(geoRectangle).build();

        // assert
        assertEquals(RoutingType.CELL_GEOCAST, routing.getDestination().getRoutingType());
        assertTrue(routing.getDestination().isGeocast());
        assertTrue(routing.getDestination().getGeoArea() instanceof GeoRectangle);
        assertTrue(routing.getDestination().getAddress().isBroadcast());
    }

    @Test
    public void geoBroadcastMbsCircle() {
        // run
        MessageRouting routing = builder.broadcast().mbs().geographical(geoCircle).build();

        // assert
        assertEquals(RoutingType.CELL_GEOCAST_MBMS, routing.getDestination().getRoutingType());
        assertTrue(routing.getDestination().isGeocast());
        assertTrue(routing.getDestination().getGeoArea() instanceof GeoCircle);
        assertTrue(routing.getDestination().getAddress().isBroadcast());
    }

    @Test
    public void geoBroadcastMbsRectangle() {
        // run
        MessageRouting routing = builder.broadcast().mbs().geographical(geoRectangle).build();

        // assert
        assertEquals(RoutingType.CELL_GEOCAST_MBMS, routing.getDestination().getRoutingType());
        assertTrue(routing.getDestination().isGeocast());
        assertTrue(routing.getDestination().getGeoArea() instanceof GeoRectangle);
        assertTrue(routing.getDestination().getAddress().isBroadcast());
    }

    @Test
    public void topocast() {
        // run
        MessageRouting routing = builder.destination(ipAddress).topological().build();

        // assert
        assertEquals(RoutingType.CELL_TOPOCAST, routing.getDestination().getRoutingType());
        assertFalse(routing.getDestination().isGeocast());
        //Because there are no hops by cell communication
        assertEquals(-1, routing.getDestination().getTimeToLive());
        assertTrue(routing.getDestination().getAddress().isUnicast());
    }
}