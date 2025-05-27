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

package org.eclipse.mosaic.lib.routing.graphhopper.util;

import static org.junit.Assert.assertEquals;

import org.eclipse.mosaic.lib.routing.graphhopper.junit.TestGraphRule;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.TurnCostStorage;
import org.junit.Rule;
import org.junit.Test;

public class TurnCostAnalyzerTest {

    @Rule
    public TestGraphRule testGraph = new TestGraphRule();

    @Test
    public void turnCostsCalculation() {
        //run
        RoutingProfileManager profileManager = testGraph.getProfileManager();
        WayTypeEncoder wayTypeEncoder = profileManager.getEncodingManager().getEncodedValue(WayTypeEncoder.KEY, WayTypeEncoder.class);

        new TurnCostAnalyzer(testGraph.getGraph(), wayTypeEncoder)
                .createTurnCostsForVehicle(profileManager.getRoutingProfile("car").getVehicleEncoding());

        //assert
        assertEquals(4d, getTurnCosts(1, 0, 0), 0.4d); //4 seconds for 90deg right turn

        assertEquals(2d, getTurnCosts(2, 2, 6), 0.4d); //2 seconds for a slight 25deg right turn

        assertEquals(6d, getTurnCosts(0, 1, 4), 0.4d); //6 seconds for a hard 120deg right turn

        assertEquals(23d, getTurnCosts(0, 0, 1), 0.4d); //23 seconds for a 90deg left turn

    }

    private double getTurnCosts(int fromEdge, int viaNode, int toEdge) {
        TurnCostStorage tc = testGraph.getGraph().getTurnCostStorage();
        BooleanEncodedValue turnRestrictionsEnc = testGraph.getProfileManager().getRoutingProfile("car").getVehicleEncoding().turnRestriction();
        DecimalEncodedValue turnCostsEnc = testGraph.getProfileManager().getRoutingProfile("car").getVehicleEncoding().turnCost();

        boolean isRestricted = tc.get(turnRestrictionsEnc, fromEdge, viaNode, toEdge);
        if (isRestricted) {
            return Double.MAX_VALUE;
        }
        return tc.get(turnCostsEnc, fromEdge, viaNode, toEdge);
    }
}
