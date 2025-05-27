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
import org.eclipse.mosaic.lib.routing.graphhopper.util.WayTypeEncoder;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class GraphHopperWeightingTest {

    @Rule
    public TestGraphRule testGraph = new TestGraphRule();

    private VehicleEncoding vehicleEnc;
    private WayTypeEncoder wayTypeEnc;

    @Before
    public void setup() {
        vehicleEnc = testGraph.getProfileManager().getRoutingProfile("car").getVehicleEncoding();
        wayTypeEnc = testGraph.getProfileManager().getEncodingManager().getEncodedValue(WayTypeEncoder.KEY, WayTypeEncoder.class);
    }

    @Test
    public void fastest_noTurnCosts() {
        Weighting w = new GraphHopperWeighting(vehicleEnc, wayTypeEnc, new OptionalTurnCostProvider(vehicleEnc, testGraph.getGraph().getTurnCostStorage()), null)
                .setRoutingCostFunction(RoutingCostFunction.Fastest);

        EdgeExplorer expl = testGraph.getGraph().createEdgeExplorer();
        EdgeIterator it = expl.setBaseNode(0);

        double distance = it.getDistance();

        double weight = w.calcEdgeWeight(it, false);
        double turnWeight = w.calcTurnWeight(1, 0, 0);

        assertEquals(distance / vehicleEnc.speed().getMaxOrMaxStorableDecimal() * 3.6, weight, 0.1d);
        assertEquals(0, turnWeight, 0.1d);
    }

    @Test
    public void shortest_noTurnCosts() {
        Weighting w = new GraphHopperWeighting(vehicleEnc, wayTypeEnc, new OptionalTurnCostProvider(vehicleEnc, testGraph.getGraph().getTurnCostStorage()), null)
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
        testGraph.getGraph().getTurnCostStorage().set(vehicleEnc.turnCost(), 1, 0, 0, 10.0);

        Weighting w = new GraphHopperWeighting(vehicleEnc, wayTypeEnc, new OptionalTurnCostProvider(vehicleEnc, testGraph.getGraph().getTurnCostStorage()), null)
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
        testGraph.getGraph().getTurnCostStorage().set(vehicleEnc.turnCost(), 1, 0, 0, 10.0);

        Weighting w = new GraphHopperWeighting(vehicleEnc, wayTypeEnc, new OptionalTurnCostProvider(vehicleEnc, testGraph.getGraph().getTurnCostStorage()), null)
                .setRoutingCostFunction(RoutingCostFunction.Fastest);

        EdgeExplorer expl = testGraph.getGraph().createEdgeExplorer();
        EdgeIterator it = expl.setBaseNode(0);

        double distance = it.getDistance();

        double weight = w.calcEdgeWeight(it, false);
        double turnWeight = w.calcTurnWeight(1, 0, 0);

        assertEquals(distance / vehicleEnc.speed().getMaxOrMaxStorableDecimal() * 3.6, weight, 0.1d);
        assertEquals(10, turnWeight, 0.1d);
    }

    @Test
    public void shortest_turnRestriction() {
        testGraph.getGraph().getTurnCostStorage().set(vehicleEnc.turnRestriction(), 1, 0, 0, true);

        Weighting w = new GraphHopperWeighting(vehicleEnc, wayTypeEnc, new OptionalTurnCostProvider(vehicleEnc, testGraph.getGraph().getTurnCostStorage()), null)
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
        testGraph.getGraph().getTurnCostStorage().set(vehicleEnc.turnRestriction(), 1, 0, 0, true);

        Weighting w = new GraphHopperWeighting(vehicleEnc, wayTypeEnc, new OptionalTurnCostProvider(vehicleEnc, testGraph.getGraph().getTurnCostStorage()), null)
                .setRoutingCostFunction(RoutingCostFunction.Fastest);

        EdgeExplorer expl = testGraph.getGraph().createEdgeExplorer();
        EdgeIterator it = expl.setBaseNode(0);

        double distance = it.getDistance();

        double weight = w.calcEdgeWeight(it, false);
        double turnWeight = w.calcTurnWeight(1, 0, 0);

        assertEquals(distance / vehicleEnc.speed().getMaxOrMaxStorableDecimal() * 3.6, weight, 0.1d);
        assertEquals(Double.POSITIVE_INFINITY, turnWeight, 0.1d);
    }

}
