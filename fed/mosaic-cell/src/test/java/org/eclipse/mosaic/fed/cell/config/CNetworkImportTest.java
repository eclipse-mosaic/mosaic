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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties;
import org.eclipse.mosaic.fed.cell.config.util.ConfigurationReader;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.UtmPoint;
import org.eclipse.mosaic.lib.geo.UtmZone;
import org.eclipse.mosaic.lib.junit.GeoProjectionRule;
import org.eclipse.mosaic.lib.model.delay.ConstantDelay;
import org.eclipse.mosaic.lib.model.delay.GammaRandomDelay;
import org.eclipse.mosaic.lib.model.delay.SimpleRandomDelay;
import org.eclipse.mosaic.rti.DATA;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;

/**
 * Test suite to validate proper reading of network config.
 */
public class CNetworkImportTest {
    private final static String NETWORK_CONF_PATH =
            "src" + File.separator + "test" + File.separator + "resources"
                    + File.separator + "configs" + File.separator + "sample_network.json";
    private final static String NETWORK_CONF_PATH_INVALID =
            "src" + File.separator + "test" + File.separator + "resources"
                    + File.separator + "configs" + File.separator + "sample_network_invalid.json";

    @Rule
    public GeoProjectionRule transformationRule = new GeoProjectionRule(
            UtmPoint.eastNorth(UtmZone.from(GeoPoint.lonLat(13.3856, 52.5415)), 388405.53, 5820063.64)
    );

    private static final double EPSILON = 1e-5;

    private CNetwork getNetworkConfig() throws InternalFederateException {
        // Read the region configuration file
        return ConfigurationReader.importNetworkConfig(NETWORK_CONF_PATH);
    }

    @Test(expected = InternalFederateException.class)
    public void checkInvalidNetworkConfig() throws InternalFederateException {
        ConfigurationReader.importNetworkConfig(NETWORK_CONF_PATH_INVALID);
    }

    @Test
    public void checkNetworkConfigAsExpected() throws InternalFederateException {
        CNetwork networkConfig = getNetworkConfig();

        assertEquals(networkConfig.defaultDownlinkCapacity, 100 * DATA.GIGABIT);
        assertEquals(networkConfig.defaultUplinkCapacity, 100 * DATA.GIGABIT);

        assertNotNull(networkConfig.globalNetwork);
        final CNetworkProperties globalNetwork = networkConfig.globalNetwork;

        if (globalNetwork.uplink.delay instanceof ConstantDelay) {
            final ConstantDelay uplinkDelay = (ConstantDelay) globalNetwork.uplink.delay;

            assertNotNull(uplinkDelay);
            assertEquals(200 * TIME.MILLI_SECOND, uplinkDelay.delay, EPSILON);
        }
        assertEquals(0.5, globalNetwork.uplink.transmission.lossProbability, EPSILON);
        assertEquals(2, globalNetwork.uplink.transmission.maxRetries, EPSILON);

        assertNotNull(globalNetwork.downlink.multicast.delay);
        assertTrue(globalNetwork.downlink.multicast.delay instanceof GammaRandomDelay);
        final GammaRandomDelay downlinkMulticastDelay = (GammaRandomDelay) globalNetwork.downlink.multicast.delay;
        assertEquals(100 * TIME.MILLI_SECOND, downlinkMulticastDelay.minDelay, EPSILON);
        assertEquals(200 * TIME.MILLI_SECOND, downlinkMulticastDelay.expDelay, EPSILON);

        assertEquals(0.5, globalNetwork.downlink.multicast.transmission.lossProbability, EPSILON);

        assertNotNull(globalNetwork.downlink.unicast.delay);
        assertTrue(globalNetwork.downlink.unicast.delay instanceof SimpleRandomDelay);
        final SimpleRandomDelay downlinkUnicastDelay = (SimpleRandomDelay) globalNetwork.downlink.unicast.delay;
        assertEquals(5, downlinkUnicastDelay.steps, EPSILON);
        assertEquals(100 * TIME.MILLI_SECOND, downlinkUnicastDelay.minDelay, EPSILON);
        assertEquals(200 * TIME.MILLI_SECOND, downlinkUnicastDelay.maxDelay, EPSILON);
        assertEquals(2, globalNetwork.downlink.unicast.transmission.maxRetries, EPSILON);

        assertEquals(0.1, globalNetwork.downlink.unicast.transmission.lossProbability, EPSILON);

        // ASSERT SERVER CONFIG
        CNetworkProperties server = networkConfig.servers.get(0);
        assertNotNull(server);
        if (server.uplink.delay instanceof ConstantDelay) {
            final ConstantDelay uplinkDelay = (ConstantDelay) globalNetwork.uplink.delay;

            assertNotNull(uplinkDelay);
            assertEquals(200 * TIME.MILLI_SECOND, uplinkDelay.delay, EPSILON);
        }
        assertEquals(0.5, server.uplink.transmission.lossProbability, EPSILON);
        assertEquals(2, server.uplink.transmission.maxRetries, EPSILON);

        assertNotNull(server.downlink.unicast.delay);
        assertTrue(server.downlink.unicast.delay instanceof SimpleRandomDelay);
        final SimpleRandomDelay downlinkUnicastDelayServer = (SimpleRandomDelay) server.downlink.unicast.delay;
        assertEquals(5, downlinkUnicastDelayServer.steps, EPSILON);
        assertEquals(100 * TIME.MILLI_SECOND, downlinkUnicastDelayServer.minDelay, EPSILON);
        assertEquals(200 * TIME.MILLI_SECOND, downlinkUnicastDelayServer.maxDelay, EPSILON);
        assertEquals(2, server.downlink.unicast.transmission.maxRetries, EPSILON);

        assertEquals(0.1, server.downlink.unicast.transmission.lossProbability, EPSILON);

        assertEquals(Long.MAX_VALUE, server.uplink.capacity.longValue());
        assertEquals(Long.MAX_VALUE, server.uplink.maxCapacity);
        assertEquals(Long.MAX_VALUE, server.downlink.capacity.longValue());
        assertEquals(Long.MAX_VALUE, server.downlink.maxCapacity);
    }
}
