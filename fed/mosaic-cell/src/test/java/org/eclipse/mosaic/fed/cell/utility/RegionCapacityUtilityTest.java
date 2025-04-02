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
import org.eclipse.mosaic.fed.cell.junit.CellConfigurationRule;
import org.eclipse.mosaic.rti.DATA;
import org.eclipse.mosaic.rti.TIME;

import org.junit.Rule;
import org.junit.Test;

/*
 * Class that tests the bandwidth calculation of the cell
 *
 * Created by hea on 5/5/15.
 */
public class RegionCapacityUtilityTest {

    @Rule
    public CellConfigurationRule configRule = new CellConfigurationRule()
            .withNetworkConfig("configs/sample_network.json");


    /**
     * Makes sure the general formula matches the old cell.
     * <p>
     * Units: eDelay is given in nanoseconds, message size is in bit
     */
    @Test
    public void testCalculateBandwidthUsage() {
        final long messageSizeInBit = DATA.KILOBIT;
        final long delayInNs = 200 * TIME.MILLI_SECOND;
        long result = CapacityUtility.calculateNeededCapacity(messageSizeInBit, delayInNs);
        assertEquals(5 * DATA.KILOBIT, result);
    }

    /**
     * Makes sure a delay value of 0 is not permitted.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test(expected = IllegalArgumentException.class)
    public void testExceptionForZeroDelay() {
        final long messageSizeInBit = DATA.KILOBIT;
        final long delayInNs = 0;
        CapacityUtility.calculateNeededCapacity(messageSizeInBit, delayInNs);
    }

    /**
     * Tests the bandwidth calculation based on the global region.
     */
    @Test
    public void testRegionBandwidthCalculation() {
        final CNetwork networkConfig = configRule.getNetworkConfig();
        final CNetworkProperties defaultRegion = networkConfig.globalNetwork;

        final long capacityDown = defaultRegion.downlink.capacity;
        final long capacityUp = defaultRegion.uplink.capacity;

        // The normal, easy case. Enough bandwidth available.
        RegionCapacityUtility.consumeCapacityDown(defaultRegion, 2500 * DATA.BIT);
        RegionCapacityUtility.consumeCapacityUp(defaultRegion, 1500 * DATA.BIT);
        assertEquals((capacityDown - 2500) * DATA.BIT, defaultRegion.downlink.capacity.longValue());
        assertEquals((capacityUp - 1500) * DATA.BIT, defaultRegion.uplink.capacity.longValue());

        assertTrue(RegionCapacityUtility.isCapacitySufficientDown(defaultRegion, 1000 * DATA.BIT));
        assertTrue(RegionCapacityUtility.isCapacitySufficientUp(defaultRegion, 1000 * DATA.BIT));

        // Try to take away more bandwidth than available.

        // This can happen when a message has a packet loss and needs to consumeCapacity anyway.
        RegionCapacityUtility.consumeCapacityDown(defaultRegion, 40000 * DATA.BIT);
        RegionCapacityUtility.consumeCapacityUp(defaultRegion, 40000 * DATA.BIT);
        assertEquals((capacityDown - 2500 - 40000) * DATA.BIT, defaultRegion.downlink.capacity.longValue());
        assertEquals((capacityUp - 1500 - 40000) * DATA.BIT, defaultRegion.uplink.capacity.longValue());

        assertFalse(RegionCapacityUtility.isCapacitySufficientDown(defaultRegion, 1000 * DATA.BIT));
        assertFalse(RegionCapacityUtility.isCapacitySufficientUp(defaultRegion, 1000 * DATA.BIT));

        // Try to free some bandwidth
        RegionCapacityUtility.freeCapacityDown(defaultRegion, 1500 * DATA.BIT);
        RegionCapacityUtility.freeCapacityUp(defaultRegion, 2500 * DATA.BIT);
        assertEquals((capacityDown - 2500 - 40000 + 1500) * DATA.BIT, defaultRegion.downlink.capacity.longValue());
        assertEquals((capacityUp - 1500 - 40000 + 2500) * DATA.BIT, defaultRegion.uplink.capacity.longValue());
    }
}
