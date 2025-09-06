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

import org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties;
import org.eclipse.mosaic.fed.cell.data.SimulationData;
import org.eclipse.mosaic.fed.cell.junit.CellConfigurationRule;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.UtmPoint;
import org.eclipse.mosaic.lib.geo.UtmZone;
import org.eclipse.mosaic.lib.junit.GeoProjectionRule;
import org.eclipse.mosaic.lib.transform.GeoProjection;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BerlinBrandenburgRegionsTest {

    private final GeoProjectionRule transformationRule = new GeoProjectionRule(
            UtmPoint.eastNorth(UtmZone.from(GeoPoint.lonLat(13, 52)), 249025.87, 5689442.67)
    );

    private final CellConfigurationRule configRule = new CellConfigurationRule()
            .withNetworkConfig("configs/sample_network.json")
            .withRegionConfig("berlinbrandenburg/regions.json");

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(transformationRule).around(configRule);

    @Test
    public void regionUtility_batchLookup100k() throws IOException {
        List<String> fails = new ArrayList<>();

        StopWatch sw = new StopWatch();
        int lookups = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                this.getClass().getResourceAsStream("/berlinbrandenburg/assert-100k.csv"), StandardCharsets.UTF_8))) {
            sw.start();

            String nodeId = "veh_dummy";
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = StringUtils.split(line, ';');

                GeoPoint geoPoint = GeoPoint.latLon(Double.parseDouble(row[1]), Double.parseDouble(row[2]));
                CartesianPoint cartesianPoint = GeoProjection.getInstance().geographicToCartesian(geoPoint);

                SimulationData.INSTANCE.setPositionOfNode(nodeId, cartesianPoint);
                SimulationData.INSTANCE.setNetworkPropertiesOfNode(nodeId, RegionUtility.getNetworkPropertiesForRegionAt(cartesianPoint));

                CNetworkProperties region = RegionUtility.getRegionForNode(nodeId);

                if (!region.id.equals(row[3])) {
                    fails.add(String.format(Locale.ENGLISH,
                            "%s:(%.6f,%.6f): Expected: %s, Actual: %s", row[0],
                            geoPoint.getLatitude(), geoPoint.getLongitude(),
                            row[3], region.id)
                    );
                }
                lookups++;
            }
            sw.stop();
        }

        for (String fail : fails) {
            System.err.println(fail);
        }

        System.out.format("Lookups: %d (%d wrong), Duration: %dms%n", lookups, fails.size(), sw.getTime());

        double successRate = 1 - (fails.size() / (double) lookups);
        Assert.assertTrue(successRate > 0.999);
    }

    @After
    public void remove() {
        SimulationData.INSTANCE.removeNode("veh_dummy");
    }
}
