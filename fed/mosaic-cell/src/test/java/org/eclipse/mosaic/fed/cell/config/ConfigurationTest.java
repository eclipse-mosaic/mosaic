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

package org.eclipse.mosaic.fed.cell.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.mosaic.fed.cell.config.model.CMobileNetworkProperties;
import org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties;
import org.eclipse.mosaic.fed.cell.config.util.ConfigurationReader;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.UtmPoint;
import org.eclipse.mosaic.lib.geo.UtmZone;
import org.eclipse.mosaic.lib.junit.GeoProjectionRule;
import org.eclipse.mosaic.lib.model.delay.ConstantDelay;
import org.eclipse.mosaic.lib.model.delay.GammaRandomDelay;
import org.eclipse.mosaic.lib.model.delay.SimpleRandomDelay;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

/**
 * This test module checks the parsing of the configuration files.
 */
public class ConfigurationTest {

    private static final double EPSILON = 1e-5;

    private final static String NETWORK_CONF
            = "src" + File.separator + "test" + File.separator
            + "resources" + File.separator + "configs" + File.separator + "sample_network.json";
    private final static String REGION_CONF
            = "src" + File.separator + "test" + File.separator
            + "resources" + File.separator + "configs" + File.separator + "sample_regions.json";

    private CRegion regionConfig;
    private CNetwork networkConfig;

    private CRegion getRegionConfig() throws InternalFederateException {
        return ConfigurationReader.importRegionConfig(REGION_CONF);
    }

    @Rule
    public GeoProjectionRule geoProjectionRule = new GeoProjectionRule(
            UtmPoint.eastNorth(UtmZone.from(GeoPoint.lonLat(13.3856, 52.5415)), 388405.53, 5820063.64)
    );

    @Before
    public void setup() throws InternalFederateException {
        networkConfig = getNetworkConfig();
        regionConfig = getRegionConfig();
    }


    @Test
    public void checkNetworkConfigParsing() {
        assertNotEquals(networkConfig, null);
    }

    @Test
    public void checkRegionConfigParsing() {
        assertNotEquals(regionConfig, null);
    }

    private CNetwork getNetworkConfig() throws InternalFederateException {
        return ConfigurationReader.importNetworkConfig(NETWORK_CONF);
    }

    @Test
    public void checkRegionConfigAsExpected() {
        assertNotNull(regionConfig.regions);
        assertEquals(regionConfig.regions.size(), 3);
        final CMobileNetworkProperties region = regionConfig.regions.get(0);
        assertEquals(52.6, region.area.getA().getLatitude(), EPSILON);
        assertEquals(13.6, region.area.getA().getLongitude(), EPSILON);
        assertEquals(52.5, region.area.getB().getLatitude(), EPSILON);
        assertEquals(13.7, region.area.getB().getLongitude(), EPSILON);
        assertEquals(region.id, "Some region");
        final CMobileNetworkProperties region2 = regionConfig.regions.get(1);
        assertNotNull(region2);
        assertEquals(region2.id, "Another region");

        if (region.uplink.delay instanceof ConstantDelay) {
            final ConstantDelay uplinkDelay = (ConstantDelay) region.uplink.delay;

            assertNotNull(uplinkDelay);
            assertEquals(200 * TIME.MILLI_SECOND, uplinkDelay.delay, EPSILON);
        }

        assertEquals(0.5, region.uplink.transmission.lossProbability, EPSILON);
        assertEquals(2, region.uplink.transmission.maxRetries);

        assertNotNull(region.downlink.multicast.delay);
        assertTrue(region.downlink.multicast.delay instanceof GammaRandomDelay);
        final GammaRandomDelay downlinkMulticastDelay = (GammaRandomDelay) region.downlink.multicast.delay;
        assertEquals(100 * TIME.MILLI_SECOND, downlinkMulticastDelay.minDelay, EPSILON);
        assertEquals(200 * TIME.MILLI_SECOND, EPSILON, downlinkMulticastDelay.expDelay);

        assertEquals(0.5, region.downlink.multicast.transmission.lossProbability, EPSILON);

        assertEquals(2000, region.uplink.capacity.longValue());
        assertEquals(42000, region.downlink.capacity.longValue());
    }

    @Test
    public void checkNetworkConfigAsExpected() {

        assertTrue(networkConfig.globalNetwork.downlink.multicast.delay instanceof GammaRandomDelay);
        final GammaRandomDelay downlinkMulticastDelay = (GammaRandomDelay) networkConfig.globalNetwork.downlink.multicast.delay;
        assertEquals(100 * TIME.MILLI_SECOND, downlinkMulticastDelay.minDelay, EPSILON);

        assertEquals(networkConfig.globalNetwork.downlink.multicast.transmission.lossProbability, 0.5, EPSILON);

        assertTrue(networkConfig.globalNetwork.downlink.unicast.delay instanceof SimpleRandomDelay);
        final SimpleRandomDelay downlinkUnicastDelay = (SimpleRandomDelay) networkConfig.globalNetwork.downlink.unicast.delay;
        assertEquals(100 * TIME.MILLI_SECOND, downlinkUnicastDelay.minDelay, EPSILON);
        assertEquals(200 * TIME.MILLI_SECOND, downlinkUnicastDelay.maxDelay, EPSILON);
        assertEquals(0.1, networkConfig.globalNetwork.downlink.unicast.transmission.lossProbability, EPSILON);
        assertEquals(2, networkConfig.globalNetwork.downlink.unicast.transmission.maxRetries);

        // check server configuration
        assertNotNull(networkConfig.servers);
        assertFalse(networkConfig.servers.isEmpty());
        CNetworkProperties server = networkConfig.servers.get(0);
        assertEquals(server.id, "TestServer");

        assertTrue(server.downlink.unicast.delay instanceof SimpleRandomDelay);
        final SimpleRandomDelay downlinkUnicastDelayServer = (SimpleRandomDelay) networkConfig.globalNetwork.downlink.unicast.delay;
        assertEquals(100 * TIME.MILLI_SECOND, downlinkUnicastDelayServer.minDelay, EPSILON);
        assertEquals(200 * TIME.MILLI_SECOND, downlinkUnicastDelayServer.maxDelay, EPSILON);
        assertEquals(0.1, networkConfig.globalNetwork.downlink.unicast.transmission.lossProbability, EPSILON);
        assertEquals(2, networkConfig.globalNetwork.downlink.unicast.transmission.maxRetries);
    }

}
