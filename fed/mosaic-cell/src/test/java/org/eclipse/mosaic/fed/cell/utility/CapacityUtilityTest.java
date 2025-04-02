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

package org.eclipse.mosaic.fed.cell.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.mosaic.fed.cell.config.CNetwork;
import org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties;
import org.eclipse.mosaic.fed.cell.config.model.TransmissionMode;
import org.eclipse.mosaic.fed.cell.junit.CellConfigurationRule;
import org.eclipse.mosaic.lib.objects.communication.CellConfiguration;
import org.eclipse.mosaic.rti.DATA;
import org.eclipse.mosaic.rti.TIME;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Class that tests the bandwidth calculation of the cell.
 */
public class CapacityUtilityTest {
    private CellConfiguration nodeConfig;
    private CellConfiguration nodeConfig2;
    private CellConfiguration serverNodeConfig0;
    private CellConfiguration serverNodeConfig1;

    @Rule
    public CellConfigurationRule configRule = new CellConfigurationRule()
            .withNetworkConfig("configs/sample_network.json");

    @Before
    public void initializeCellConfiguration() {
        nodeConfig = new CellConfiguration("veh0", true, 1000L, 2000L);
        nodeConfig2 = new CellConfiguration("veh1", true, 44000L, 25000L);
        serverNodeConfig0 = new CellConfiguration("server0", true, 44000L, 25000L);
        serverNodeConfig1 = new CellConfiguration("server1", true, 44000L, 25000L);
    }

    @Test
    public void testCalculateNeededCapacity() {
        assertEquals(200 * DATA.BIT, CapacityUtility.calculateNeededCapacity(600, 3 * TIME.SECOND));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test(expected = IllegalArgumentException.class)
    public void testCalculateNeededDelay() {
        assertEquals(200 * TIME.SECOND, CapacityUtility.calculateNeededDelay(4000, 20));
        assertEquals(0L, CapacityUtility.calculateNeededCapacity(0, 20));

        CapacityUtility.calculateNeededCapacity(2, 0);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test(expected = IllegalArgumentException.class)
    public void testCalculateNeededDelayNegativeSize() {
        CapacityUtility.calculateNeededCapacity(-200, 300);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test(expected = IllegalArgumentException.class)
    public void testCalculateNeededDelayNegativeDelay() {
        CapacityUtility.calculateNeededCapacity(200, -300);
    }

    @Test
    public void testIsCapacitySufficient() {
        // public static boolean isCapacitySufficient(TransmissionMode mode,
        // CNetworkProperties region, CellConfiguration nodeCellConfiguration, long neededBandwidth)
        CNetwork networkConfig = configRule.getNetworkConfig();

        // Downlink Unicast
        // capacity is sufficient
        assertTrue(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.DownlinkUnicast,
                        networkConfig.globalNetwork, nodeConfig,
                        (long) 100 * DATA.BIT)
        );
        // needed bandwidth exceeds the capacity of the region
        assertFalse(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.DownlinkUnicast,
                        networkConfig.globalNetwork, nodeConfig,
                        (long) 46000 * DATA.BIT)
        );
        // needed bandwidth exceeds the capacity of the node is
        assertFalse(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.DownlinkUnicast,
                        networkConfig.globalNetwork, nodeConfig,
                        (long) 4000 * DATA.BIT)
        );
        // needed bandwidth exceeds the capacity of the region
        assertFalse(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.DownlinkUnicast,
                        networkConfig.globalNetwork,
                        nodeConfig2,
                        (long) 44000 * DATA.BIT)
        );

        // Uplink Unicast
        // capacity is sufficient
        assertTrue(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.UplinkUnicast,
                        networkConfig.globalNetwork,
                        nodeConfig,
                        (long) 100 * DATA.BIT)
        );
        // needed bandwidth exceeds the capacity of the region and the node
        assertFalse(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.UplinkUnicast,
                        networkConfig.globalNetwork,
                        nodeConfig,
                        (long) 30000 * DATA.BIT)
        );
        // needed bandwidth exceeds the capacity of the node
        assertFalse(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.UplinkUnicast,
                        networkConfig.globalNetwork,
                        nodeConfig,
                        (long) 4000 * DATA.BIT)
        );
        // needed bandwidth exceeds the capacity of the region
        assertFalse(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.UplinkUnicast,
                        networkConfig.globalNetwork,
                        nodeConfig2,
                        (long) 24000 * DATA.BIT)
        );

        // Downlink Multicast
        // capacity is sufficient
        assertTrue(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.DownlinkMulticast,
                        networkConfig.globalNetwork,
                        nodeConfig,
                        (long) 100 * DATA.BIT)
        );
        // needed bandwidth exceeds the multicast capacity of the region and the node
        assertFalse(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.DownlinkMulticast,
                        networkConfig.globalNetwork,
                        nodeConfig,
                        (long) 26000 * DATA.BIT)
        );
        // the case the needed bandwidth exceeds the multicast capacity is
        // not considered because the node capacity is not considered for the multicast mode

        // check null handling
        assertFalse(
                CapacityUtility.isCapacitySufficient(null,
                        networkConfig.globalNetwork,
                        nodeConfig,
                        (long) 100 * DATA.BIT)
        );
        assertFalse(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.DownlinkUnicast,
                        null, nodeConfig,
                        (long) 100 * DATA.BIT)
        );
        assertFalse(
                CapacityUtility.isCapacitySufficient(
                        TransmissionMode.DownlinkUnicast,
                        networkConfig.globalNetwork,
                        null,
                        (long) 44000 * DATA.BIT)
        );
    }

    @Test
    public void testConsumeCapacity() {
        CNetwork networkConfig = configRule.getNetworkConfig();

        CapacityUtility.consumeCapacity(TransmissionMode.UplinkUnicast, networkConfig.globalNetwork, nodeConfig, 200 * DATA.BIT);
        assertEquals(1800 * DATA.BIT, nodeConfig.getAvailableUplinkBitrate());
        assertEquals(22800 * DATA.BIT, networkConfig.globalNetwork.uplink.capacity.longValue());

        CapacityUtility.consumeCapacity(TransmissionMode.DownlinkUnicast, networkConfig.globalNetwork, nodeConfig, 300 * DATA.BIT);
        assertEquals(700 * DATA.BIT, nodeConfig.getAvailableDownlinkBitrate());
        assertEquals(41700 * DATA.BIT, networkConfig.globalNetwork.downlink.capacity.longValue());

        CapacityUtility.consumeCapacity(TransmissionMode.DownlinkMulticast, networkConfig.globalNetwork, nodeConfig, 400 * DATA.BIT);
        assertEquals(700 * DATA.BIT, nodeConfig.getAvailableDownlinkBitrate());
        assertEquals(41300 * DATA.BIT, networkConfig.globalNetwork.downlink.capacity.longValue());

        // consume negative capacity (should not change the actual capacity)
        CapacityUtility.consumeCapacity(TransmissionMode.UplinkUnicast, networkConfig.globalNetwork, nodeConfig, -200 * DATA.BIT);
        assertEquals(1800 * DATA.BIT, nodeConfig.getAvailableUplinkBitrate());
        assertEquals(22800 * DATA.BIT, networkConfig.globalNetwork.uplink.capacity.longValue());

        CapacityUtility.consumeCapacity(TransmissionMode.DownlinkUnicast, networkConfig.globalNetwork, nodeConfig, -200 * DATA.BIT);
        assertEquals(700 * DATA.BIT, nodeConfig.getAvailableDownlinkBitrate());
        assertEquals(41300 * DATA.BIT, networkConfig.globalNetwork.downlink.capacity.longValue());

        CapacityUtility.consumeCapacity(TransmissionMode.DownlinkMulticast, networkConfig.globalNetwork, nodeConfig, -200 * DATA.BIT);
        assertEquals(700 * DATA.BIT, nodeConfig.getAvailableDownlinkBitrate());
        assertEquals(41300 * DATA.BIT, networkConfig.globalNetwork.downlink.capacity.longValue());

        // check null
        CapacityUtility.consumeCapacity(null, networkConfig.globalNetwork, nodeConfig, 200 * DATA.BIT);
        assertEquals(700 * DATA.BIT, nodeConfig.getAvailableDownlinkBitrate());
        assertEquals(41300 * DATA.BIT, networkConfig.globalNetwork.downlink.capacity.longValue());

        CapacityUtility.consumeCapacity(TransmissionMode.DownlinkUnicast, null, nodeConfig, 200 * DATA.BIT);
        assertEquals(700 * DATA.BIT, nodeConfig.getAvailableDownlinkBitrate());
        assertEquals(41300 * DATA.BIT, networkConfig.globalNetwork.downlink.capacity.longValue());

        CapacityUtility.consumeCapacity(TransmissionMode.DownlinkUnicast, networkConfig.globalNetwork, null, 200 * DATA.BIT);
        assertEquals(700 * DATA.BIT, nodeConfig.getAvailableDownlinkBitrate());
        assertEquals(41300 * DATA.BIT, networkConfig.globalNetwork.downlink.capacity.longValue());

        // consume more capacity as available
        CapacityUtility.consumeCapacity(TransmissionMode.DownlinkUnicast, networkConfig.globalNetwork, nodeConfig, 800 * DATA.BIT);
        assertEquals(-100 * DATA.BIT, nodeConfig.getAvailableDownlinkBitrate());

        CapacityUtility.consumeCapacity(TransmissionMode.UplinkUnicast, networkConfig.globalNetwork, nodeConfig, 23000 * DATA.BIT);
        assertEquals(-200 * DATA.BIT, networkConfig.globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testFreeCapacityUp() {
        // public static void freeCapacityUp(CNetworkProperties region, CellConfiguration nodeCellConfiguration, long freed)
        CNetwork networkConfig = configRule.getNetworkConfig();
        CapacityUtility.consumeCapacity(TransmissionMode.UplinkUnicast, networkConfig.globalNetwork, nodeConfig, 800 * DATA.BIT);
        NodeCapacityUtility.freeCapacityUp(nodeConfig, 500 * DATA.BIT);
        RegionCapacityUtility.freeCapacityUp(networkConfig.globalNetwork, 500 * DATA.BIT);

        assertEquals(1700 * DATA.BIT, nodeConfig.getAvailableUplinkBitrate());
        assertEquals(22700 * DATA.BIT, networkConfig.globalNetwork.uplink.capacity.longValue());
        try {
            NodeCapacityUtility.freeCapacityUp(nodeConfig, 800 * DATA.BIT);
            //noinspection ConstantConditions
            RegionCapacityUtility.freeCapacityUp(null, 800 * DATA.BIT);
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
        NodeCapacityUtility.freeCapacityUp(null, 800 * DATA.BIT);
        RegionCapacityUtility.freeCapacityUp(networkConfig.globalNetwork, 800 * DATA.BIT);
        NodeCapacityUtility.freeCapacityUp(nodeConfig, 500 * DATA.BIT);
        RegionCapacityUtility.freeCapacityUp(networkConfig.globalNetwork, 500 * DATA.BIT);
        assertEquals(2000 * DATA.BIT, nodeConfig.getAvailableUplinkBitrate());
        assertEquals(23000 * DATA.BIT, networkConfig.globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testIsAvailableUplink() {
        // public static boolean isBusy(TransmissionMode mode, CNetworkProperties region, CellConfiguration nodeCellConfiguration)
        CNetwork networkConfig = configRule.getNetworkConfig();
        assertTrue(CapacityUtility.isAvailable(TransmissionMode.UplinkUnicast, networkConfig.globalNetwork, nodeConfig));
        NodeCapacityUtility.consumeCapacityUp(nodeConfig, 1950 * DATA.BIT);
        assertFalse(CapacityUtility.isAvailable(TransmissionMode.UplinkUnicast, networkConfig.globalNetwork, nodeConfig));
        NodeCapacityUtility.freeCapacityUp(nodeConfig, 1950 * DATA.BIT);
        RegionCapacityUtility.consumeCapacityUp(networkConfig.globalNetwork, 22500 * DATA.BIT);
        assertFalse(CapacityUtility.isAvailable(TransmissionMode.UplinkUnicast, networkConfig.globalNetwork, nodeConfig));
        assertFalse(CapacityUtility.isAvailable(null, networkConfig.globalNetwork, nodeConfig));
        assertFalse(CapacityUtility.isAvailable(TransmissionMode.UplinkUnicast, null, nodeConfig));
        assertFalse(CapacityUtility.isAvailable(TransmissionMode.UplinkUnicast, networkConfig.globalNetwork, null));
    }

    @Test
    public void testIsBusyDownlink() {
        CNetwork networkConfig = configRule.getNetworkConfig();
        assertTrue(CapacityUtility.isAvailable(TransmissionMode.DownlinkUnicast, networkConfig.globalNetwork, nodeConfig));
        NodeCapacityUtility.consumeCapacityDown(nodeConfig, 950 * DATA.BIT);
        // check when node is busy
        assertFalse(CapacityUtility.isAvailable(TransmissionMode.DownlinkUnicast, networkConfig.globalNetwork, nodeConfig));
        NodeCapacityUtility.freeCapacityDown(nodeConfig, 950 * DATA.BIT);
        // check when region is busy
        RegionCapacityUtility.consumeCapacityDown(networkConfig.globalNetwork, 41500 * DATA.BIT);
        assertFalse(CapacityUtility.isAvailable(TransmissionMode.DownlinkUnicast, networkConfig.globalNetwork, nodeConfig));
        // check multicast when region is busy
        assertFalse(CapacityUtility.isAvailable(TransmissionMode.DownlinkMulticast, networkConfig.globalNetwork, nodeConfig));
        // check multicast when neither region nor node are busy
        RegionCapacityUtility.freeCapacityDown(networkConfig.globalNetwork, 41500 * DATA.BIT);
        assertTrue(CapacityUtility.isAvailable(TransmissionMode.DownlinkMulticast, networkConfig.globalNetwork, nodeConfig));
        // check multicast when only the node is busy (should return true because the node is considered in the multicast cast)
        NodeCapacityUtility.consumeCapacityDown(nodeConfig, 950 * DATA.BIT);
        assertTrue(CapacityUtility.isAvailable(TransmissionMode.DownlinkMulticast, networkConfig.globalNetwork, nodeConfig));
    }

    @Test
    public void testIsAvailableCapacity() {
        // check null as arguments
        CNetwork networkConfig = configRule.getNetworkConfig();
        assertEquals(0L, CapacityUtility.availableCapacity(null, networkConfig.globalNetwork, nodeConfig));
        assertEquals(0L, CapacityUtility.availableCapacity(TransmissionMode.UplinkUnicast, null, nodeConfig));
        assertEquals(0L, CapacityUtility.availableCapacity(TransmissionMode.UplinkUnicast, networkConfig.globalNetwork, null));
        // case where the node has less available capacity compared to the capacity of the region
        assertEquals(
                1000 * DATA.BIT,
                CapacityUtility.availableCapacity(TransmissionMode.DownlinkUnicast, networkConfig.globalNetwork, nodeConfig)
        );
        assertEquals(
                2000 * DATA.BIT,
                CapacityUtility.availableCapacity(TransmissionMode.UplinkUnicast, networkConfig.globalNetwork, nodeConfig)
        );
        // case where the region has less available capacity compared to the capacity of the node
        RegionCapacityUtility.consumeCapacityUp(networkConfig.globalNetwork, 22500 * DATA.BIT);
        assertEquals(
                500 * DATA.BIT,
                CapacityUtility.availableCapacity(TransmissionMode.UplinkUnicast, networkConfig.globalNetwork, nodeConfig)
        );
        RegionCapacityUtility.consumeCapacityDown(networkConfig.globalNetwork, 41500 * DATA.BIT);
        assertEquals(
                500 * DATA.BIT,
                CapacityUtility.availableCapacity(TransmissionMode.DownlinkUnicast, networkConfig.globalNetwork, nodeConfig)
        );
    }

    @Test
    public void capacityHandlingForServers() {
        CNetwork networkConfig = configRule.getNetworkConfig();
        CNetworkProperties serverRegion = networkConfig.servers.get(0);
        assertEquals(serverRegion.downlink.capacity.longValue(), Long.MAX_VALUE);
        assertEquals(serverRegion.uplink.capacity.longValue(), Long.MAX_VALUE);
        assertEquals(
                CapacityUtility.availableCapacity(TransmissionMode.DownlinkUnicast, serverRegion,
                        serverNodeConfig0), serverNodeConfig0.getAvailableDownlinkBitrate()
        );
        assertEquals(
                CapacityUtility.availableCapacity(TransmissionMode.UplinkUnicast, serverRegion,
                        serverNodeConfig0), serverNodeConfig0.getAvailableUplinkBitrate()
        );
        assertEquals(
                CapacityUtility.availableCapacity(TransmissionMode.DownlinkUnicast, serverRegion,
                        serverNodeConfig1), serverNodeConfig1.getAvailableDownlinkBitrate()
        );
        assertEquals(
                CapacityUtility.availableCapacity(TransmissionMode.UplinkUnicast, serverRegion,
                        serverNodeConfig1), serverNodeConfig1.getAvailableUplinkBitrate()
        );
        // consume downlink capacity, only node capacity should be affected server region shouldn't change
        long consumedBandwidth = 100 * DATA.BIT;
        CapacityUtility.consumeCapacity(TransmissionMode.DownlinkUnicast, serverRegion, serverNodeConfig0, consumedBandwidth);
        assertEquals(
                CapacityUtility.availableCapacity(TransmissionMode.DownlinkUnicast, serverRegion,
                        serverNodeConfig0), serverNodeConfig1.getAvailableDownlinkBitrate() - consumedBandwidth
        ); // capacity should be consumed up
        assertEquals(serverRegion.downlink.capacity.longValue(), Long.MAX_VALUE);
        RegionCapacityUtility.freeCapacityDown(serverRegion, consumedBandwidth);
        NodeCapacityUtility.freeCapacityDown(serverNodeConfig0, consumedBandwidth);
        assertEquals(
                CapacityUtility.availableCapacity(TransmissionMode.DownlinkUnicast, serverRegion,
                        serverNodeConfig0), serverNodeConfig1.getAvailableDownlinkBitrate()
        ); // capacity should be freed up

        // consume uplink capacity, only node capacity should be affected server region shouldn't change
        CapacityUtility.consumeCapacity(TransmissionMode.UplinkUnicast, serverRegion, serverNodeConfig0, consumedBandwidth);
        assertEquals(
                CapacityUtility.availableCapacity(TransmissionMode.UplinkUnicast, serverRegion,
                        serverNodeConfig0), serverNodeConfig1.getAvailableUplinkBitrate() - consumedBandwidth
        ); // capacity should be consumed up
        assertEquals(serverRegion.uplink.capacity.longValue(), Long.MAX_VALUE);
        RegionCapacityUtility.freeCapacityUp(serverRegion, consumedBandwidth);
        NodeCapacityUtility.freeCapacityUp(serverNodeConfig0, consumedBandwidth);
        assertEquals(
                CapacityUtility.availableCapacity(TransmissionMode.UplinkUnicast, serverRegion,
                        serverNodeConfig0), serverNodeConfig1.getAvailableUplinkBitrate()
        ); // capacity should be freed up
    }
}
