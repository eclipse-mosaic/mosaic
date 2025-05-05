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

package org.eclipse.mosaic.lib.routing.graphhopper;

import static org.junit.Assert.assertEquals;

import org.eclipse.mosaic.lib.routing.RoutingCostFunction;
import org.eclipse.mosaic.lib.routing.graphhopper.junit.TestGraphRule;
import org.eclipse.mosaic.lib.routing.graphhopper.util.OptionalTurnCostProvider;
import org.eclipse.mosaic.lib.routing.graphhopper.util.VehicleEncoding;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import org.junit.Rule;
import org.junit.Test;

public class GraphHopperWeightingTest {

    @Rule
    public TestGraphRule testGraph = new TestGraphRule();

    @Test
    public void fastest_noTurnCosts() {
        VehicleEncoding enc = testGraph.getEncodingManager().getVehicleEncoding("car");

        Weighting w = new GraphHopperWeighting(enc, testGraph.getEncodingManager().wayType(), new OptionalTurnCostProvider(enc, testGraph.getGraph().getTurnCostStorage()), null)
                .setRoutingCostFunction(RoutingCostFunction.Fastest);

        EdgeExplorer expl = testGraph.getGraph().createEdgeExplorer();
        EdgeIterator it = expl.setBaseNode(0);

        double distance = it.getDistance();

        double weight = w.calcEdgeWeight(it, false);
        double turnWeight = w.calcTurnWeight(1, 0, 0);

        assertEquals(distance / enc.speed().getMaxOrMaxStorableDecimal() * 3.6, weight, 0.1d);
        assertEquals(0, turnWeight, 0.1d);
    }

    @Test
    public void shortest_noTurnCosts() {
        VehicleEncoding enc = testGraph.getEncodingManager().getVehicleEncoding("car");

        Weighting w = new GraphHopperWeighting(enc, testGraph.getEncodingManager().wayType(), new OptionalTurnCostProvider(enc, testGraph.getGraph().getTurnCostStorage()), null)
                .setRoutingCostFunction(RoutingCostFunction.Shortest);

        EdgeExplorer expl = testGraph.getGraph().createEdgeExplorer();
        EdgeIterator it = expl.setBaseNode(0);

        double distance = it.getDistance();

        double weight = w.calcEdgeWeight(it, false);
        double turnWeight = w.calcTurnWeight(1, 0, 0);

        assertEquals(distance, weight, 0.1d);
        assertEquals(0, turnWeight, 0.1d);
    }


    @Test
    public void shortest_turnCosts() {
        VehicleEncoding enc = testGraph.getEncodingManager().getVehicleEncoding("car");

        testGraph.getGraph().getTurnCostStorage().set(enc.turnCost(), 1, 0, 0, 10.0);

        Weighting w = new GraphHopperWeighting(enc, testGraph.getEncodingManager().wayType(), new OptionalTurnCostProvider(enc, testGraph.getGraph().getTurnCostStorage()), null)
                .setRoutingCostFunction(RoutingCostFunction.Shortest);

        EdgeExplorer expl = testGraph.getGraph().createEdgeExplorer();
        EdgeIterator it = expl.setBaseNode(0);

        double distance = it.getDistance();

        double weight = w.calcEdgeWeight(it, false);
        double turnWeight = w.calcTurnWeight(1, 0, 0);

        assertEquals(distance, weight, 0.1d);
        assertEquals(10, turnWeight, 0.1d);
    }

    @Test
    public void fastest_turnCosts() {
        VehicleEncoding enc = testGraph.getEncodingManager().getVehicleEncoding("car");

        testGraph.getGraph().getTurnCostStorage().set(enc.turnCost(), 1, 0, 0, 10.0);

        Weighting w = new GraphHopperWeighting(enc, testGraph.getEncodingManager().wayType(), new OptionalTurnCostProvider(enc, testGraph.getGraph().getTurnCostStorage()), null)
                .setRoutingCostFunction(RoutingCostFunction.Fastest);

        EdgeExplorer expl = testGraph.getGraph().createEdgeExplorer();
        EdgeIterator it = expl.setBaseNode(0);

        double distance = it.getDistance();

        double weight = w.calcEdgeWeight(it, false);
        double turnWeight = w.calcTurnWeight(1, 0, 0);

        assertEquals(distance / enc.speed().getMaxOrMaxStorableDecimal() * 3.6, weight, 0.1d);
        assertEquals(10, turnWeight, 0.1d);
    }

    @Test
    public void shortest_turnRestriction() {
        VehicleEncoding enc = testGraph.getEncodingManager().getVehicleEncoding("car");

        testGraph.getGraph().getTurnCostStorage().set(enc.turnRestriction(), 1, 0, 0, true);

        Weighting w = new GraphHopperWeighting(enc, testGraph.getEncodingManager().wayType(), new OptionalTurnCostProvider(enc, testGraph.getGraph().getTurnCostStorage()), null)
                .setRoutingCostFunction(RoutingCostFunction.Shortest);

        EdgeExplorer expl = testGraph.getGraph().createEdgeExplorer();
        EdgeIterator it = expl.setBaseNode(0);

        double distance = it.getDistance();

        double weight = w.calcEdgeWeight(it, false);
        double turnWeight = w.calcTurnWeight(1, 0, 0);

        assertEquals(distance, weight, 0.1d);
        assertEquals(Double.POSITIVE_INFINITY, turnWeight, 0.1d);
    }

    @Test
    public void fastest_turnRestriction() {
        VehicleEncoding enc = testGraph.getEncodingManager().getVehicleEncoding("car");

        testGraph.getGraph().getTurnCostStorage().set(enc.turnRestriction(), 1, 0, 0, true);

        Weighting w = new GraphHopperWeighting(enc, testGraph.getEncodingManager().wayType(), new OptionalTurnCostProvider(enc, testGraph.getGraph().getTurnCostStorage()), null)
                .setRoutingCostFunction(RoutingCostFunction.Fastest);

        EdgeExplorer expl = testGraph.getGraph().createEdgeExplorer();
        EdgeIterator it = expl.setBaseNode(0);

        double distance = it.getDistance();

        double weight = w.calcEdgeWeight(it, false);
        double turnWeight = w.calcTurnWeight(1, 0, 0);

        assertEquals(distance / enc.speed().getMaxOrMaxStorableDecimal() * 3.6, weight, 0.1d);
        assertEquals(Double.POSITIVE_INFINITY, turnWeight, 0.1d);
    }

}
