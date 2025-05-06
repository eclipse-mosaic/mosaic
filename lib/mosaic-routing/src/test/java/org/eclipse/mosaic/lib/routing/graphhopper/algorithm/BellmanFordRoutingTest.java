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

package org.eclipse.mosaic.lib.routing.graphhopper.algorithm;

import static org.junit.Assert.assertEquals;

import org.eclipse.mosaic.lib.routing.RoutingCostFunction;
import org.eclipse.mosaic.lib.routing.graphhopper.GraphHopperWeighting;
import org.eclipse.mosaic.lib.routing.graphhopper.junit.TestGraphRule;
import org.eclipse.mosaic.lib.routing.graphhopper.util.GHListHelper;
import org.eclipse.mosaic.lib.routing.graphhopper.util.OptionalTurnCostProvider;
import org.eclipse.mosaic.lib.routing.graphhopper.util.VehicleEncoding;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.PMap;
import org.junit.Rule;
import org.junit.Test;

public class BellmanFordRoutingTest {


    @Rule
    public TestGraphRule testGraph = new TestGraphRule();

    @Test
    public void calculateFastestPath() {
        BaseGraph g = testGraph.getGraph();
        VehicleEncoding enc = testGraph.getProfileManager().getRoutingProfile("car").getVehicleEncoding();
        Weighting w = new GraphHopperWeighting(enc, null, TurnCostProvider.NO_TURN_COST_PROVIDER, null)
                .setRoutingCostFunction(RoutingCostFunction.Fastest);

        //run
        Path p = new BellmanFordRouting(g, w, new PMap()).calcPath(0, 10);

        //assert shortest
        assertEquals(GHListHelper.createTList(0, 1, 5, 10), p.calcNodes());
        assertEquals(5000, p.getDistance(), 0.1);
    }

    @Test
    public void calculateFastestPath_turnCosts() {
        BaseGraph g = testGraph.getGraph();
        VehicleEncoding enc = testGraph.getProfileManager().getRoutingProfile("car").getVehicleEncoding();
        Weighting w = new GraphHopperWeighting(enc, null, new OptionalTurnCostProvider(enc, g.getTurnCostStorage()), null)
                .setRoutingCostFunction(RoutingCostFunction.Fastest);

        //add expensive turn at (0-1)->(1,5)
        g.getTurnCostStorage().set(enc.turnCost(), 0, 1, 3, 124);

        //run
        Path p = new BellmanFordRouting(g, w, new PMap()).calcPath(0, 10);

        //assert shortest
        assertEquals(GHListHelper.createTList(0, 4, 7, 9, 10), p.calcNodes());
        assertEquals(5500, p.getDistance(), 0.1);
    }

    @Test
    public void calculateShortestPath() {
        BaseGraph g = testGraph.getGraph();
        VehicleEncoding enc = testGraph.getProfileManager().getRoutingProfile("car").getVehicleEncoding();
        Weighting w = new GraphHopperWeighting(enc, null, TurnCostProvider.NO_TURN_COST_PROVIDER, null)
                .setRoutingCostFunction(RoutingCostFunction.Shortest);

        //run
        Path p = new BellmanFordRouting(g, w, new PMap()).calcPath(0, 10);

        //assert shortest
        assertEquals(GHListHelper.createTList(0, 1, 5, 10), p.calcNodes());
        assertEquals(5000, p.getDistance(), 0.1);
    }


}