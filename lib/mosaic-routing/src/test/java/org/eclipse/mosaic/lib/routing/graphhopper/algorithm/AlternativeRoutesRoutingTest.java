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

import org.eclipse.mosaic.lib.routing.graphhopper.GraphHopperWeighting;
import org.eclipse.mosaic.lib.routing.graphhopper.junit.TestGraphRule;
import org.eclipse.mosaic.lib.routing.graphhopper.util.GHListHelper;
import org.eclipse.mosaic.lib.routing.graphhopper.util.OptionalTurnCostProvider;
import org.eclipse.mosaic.lib.routing.graphhopper.util.VehicleEncoding;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class AlternativeRoutesRoutingTest {

    @Rule
    public TestGraphRule testGraph = new TestGraphRule();

    /**
     * Calculates two alternatives paths using the test graph
     */
    @Test
    public void calculateAlternativePaths_withTurnCost() {
        BaseGraph g = testGraph.getGraph();
        VehicleEncoding enc = testGraph.getEncodingManager().getVehicleEncoding("car");
        Weighting w = new GraphHopperWeighting(enc, null, new OptionalTurnCostProvider(enc, g.getTurnCostStorage()), null);

        //100 seconds turn costs for turn  (0-1)->(1-2)
        g.getTurnCostStorage().set(enc.turnCost(), 0, 1, 2, 100);

        RoutingAlgorithm algo = RoutingAlgorithmFactory.DEFAULT.createAlgorithm(g, w,
                new PMap().putObject(Parameters.Algorithms.AltRoute.MAX_PATHS, 3)
        );

        // RUN
        List<Path> paths = algo.calcPaths(0, 10);

        // ASSERT
        assertEquals(3, paths.size());

        Path p = paths.get(0);
        assertEquals(GHListHelper.createTList(0, 1, 5, 10), p.calcNodes());
        assertEquals(5000, p.getDistance(), 0.1);

        p = paths.get(1);
        assertEquals(GHListHelper.createTList(0, 4, 7, 9, 10), p.calcNodes());
        assertEquals(5500, p.getDistance(), 0.1);

        p = paths.get(2);
        assertEquals(GHListHelper.createTList(0, 1, 5, 6, 10), p.calcNodes());
        assertEquals(6000, p.getDistance(), 0.1);
    }

    /**
     * Calculates two alternatives paths using the test graph
     * considering turn costs
     */
    @Test
    public void calculateAlternativePaths() {
        BaseGraph g = testGraph.getGraph();
        VehicleEncoding enc = testGraph.getEncodingManager().getVehicleEncoding("car");
        Weighting w = new GraphHopperWeighting(enc, null, new OptionalTurnCostProvider(enc, g.getTurnCostStorage()), null);

        RoutingAlgorithm algo = RoutingAlgorithmFactory.DEFAULT.createAlgorithm(g, w,
                new PMap().putObject(Parameters.Algorithms.AltRoute.MAX_PATHS, 3)
        );

        // RUN
        List<Path> paths = algo.calcPaths(0, 10);

        // ASSERT
        assertEquals(3, paths.size());
        //assert shortest
        Path p = paths.get(0);
        assertEquals(GHListHelper.createTList(0, 1, 5, 10), p.calcNodes());
        assertEquals(5000, p.getDistance(), 0.1);

        p = paths.get(1);
        assertEquals(GHListHelper.createTList(0, 4, 7, 9, 10), p.calcNodes());
        assertEquals(5500, p.getDistance(), 0.1);

        p = paths.get(2);
        assertEquals(GHListHelper.createTList(0, 1, 2, 6, 10), p.calcNodes());
        assertEquals(5600, p.getDistance(), 0.1);
    }

    /**
     * Calculates only the best path.
     */
    @Test
    public void calculateBestPath() {
        BaseGraph g = testGraph.getGraph();
        VehicleEncoding enc = testGraph.getEncodingManager().getVehicleEncoding("car");
        Weighting w = new GraphHopperWeighting(enc, null, new OptionalTurnCostProvider(enc, g.getTurnCostStorage()), null);

        RoutingAlgorithm algo = RoutingAlgorithmFactory.DEFAULT.createAlgorithm(g, w, new PMap());

        Path p = algo.calcPath(0, 10);

        //assert shortest
        assertEquals(GHListHelper.createTList(0, 1, 5, 10), p.calcNodes());
        assertEquals(5000, p.getDistance(), 0.1);
    }

}
