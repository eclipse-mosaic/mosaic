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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.mosaic.lib.database.Database;
import org.eclipse.mosaic.lib.routing.graphhopper.junit.TestGraphRule;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests, if the {@link DatabaseGraphLoader} correctly transfers the scenario database
 * into {@link BaseGraph}.
 */
public class DatabaseGraphLoaderTest {

    @Rule
    public TestGraphRule testGraph = new TestGraphRule(true);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void correctImport() throws Exception {
        //setup
        File testDb = folder.newFile();
        try (InputStream input = this.getClass().getResourceAsStream("/basicTest.db");
             OutputStream output = new FileOutputStream(testDb)) {
            IOUtils.copy(input, output);
        }

        Database database = Database.loadFromFile(testDb);

        DatabaseGraphLoader reader = new DatabaseGraphLoader(database);
        GraphhopperToDatabaseMapper mapper = new GraphhopperToDatabaseMapper();
        BaseGraph g = testGraph.getGraph();
        TurnCostStorage tcStorage = testGraph.getGraph().getTurnCostStorage();

        // pre-assert
        assertEquals("#nodes", 4, database.getNodes().size());
        assertEquals("#ways", 3, database.getWays().size());
        assertEquals("#connections", 5, database.getConnections().size());
        assertEquals("#restrictions", 1, database.getRestrictions().size());
        assertEquals("#routes", 1, database.getRoutes().size());

        //run
        reader.initialize(g, testGraph.getProfileManager(), mapper);
        reader.loadGraph();

        //assert

        int n1 = mapper.fromNode(database.getNode("1"));
        int n2 = mapper.fromNode(database.getNode("2"));
        int n3 = mapper.fromNode(database.getNode("3"));
        int n4 = mapper.fromNode(database.getNode("4"));
        int con1_1_2 = mapper.fromConnection(database.getConnection("1_1_2"));
        int con1_2_1 = mapper.fromConnection(database.getConnection("1_2_1"));
        int con2_4_2 = mapper.fromConnection(database.getConnection("2_4_2"));
        int con3_2_3 = mapper.fromConnection(database.getConnection("3_2_3"));
        int con3_3_2 = mapper.fromConnection(database.getConnection("3_3_2"));

        VehicleEncoding vehicleEncoding = testGraph.getProfileManager().getRoutingProfile("car").getVehicleEncoding();
        WayTypeEncoder wayTypeEnc = testGraph.getProfileManager().getEncodingManager().getEncodedValue(WayTypeEncoder.KEY, WayTypeEncoder.class);

        EdgeFilter outFilter = AccessFilter.outEdges(vehicleEncoding.access());

        assertEquals("#nodes of graph", 4, g.getNodes());

        //assert outgoing edges of node 1
        EdgeExplorer expl = g.createEdgeExplorer(outFilter);
        EdgeIterator it = expl.setBaseNode(n1);
        assertTrue(it.next());
        assertEquals(con1_1_2, it.getEdge());
        assertEquals(n2, it.getAdjNode());
        assertEquals(100, it.getDistance(), 0d);
        assertEquals(140, it.get(vehicleEncoding.speed()), 10d); //graphhopper has default max speed of 140 km/h
        assertFalse(WayTypeEncoder.isResidential(it.get(wayTypeEnc)));
        assertFalse(it.next());

        final Map<Integer, EdgeAssert> assertionMap = new HashMap<>();

        //assert outgoing edges of node 2
        it = expl.setBaseNode(n2);
        assertionMap.put(n1, edgeAssertion(n1, con1_2_1, 80, 140, false));
        assertionMap.put(n3, edgeAssertion(n3, con3_2_3, 50, 140, false));
        assertNextEdge(vehicleEncoding.speed(), wayTypeEnc, it, assertionMap);
        assertNextEdge(vehicleEncoding.speed(), wayTypeEnc, it, assertionMap);
        assertTrue(assertionMap.isEmpty());
        assertFalse(it.next());

        //assert outgoing edges of node 3
        it = expl.setBaseNode(n3);
        assertionMap.put(n2, edgeAssertion(n2, con3_3_2, 50, 140, false));
        assertNextEdge(vehicleEncoding.speed(), wayTypeEnc, it, assertionMap);
        assertTrue(assertionMap.isEmpty());
        assertFalse(it.next());

        //assert outgoing edges of node 4
        it = expl.setBaseNode(n4);
        assertionMap.put(n2, edgeAssertion(n2, con2_4_2, 50, 30 * 3.6 * 0.9, true));
        assertNextEdge(vehicleEncoding.speed(), wayTypeEnc, it, assertionMap);
        assertTrue(assertionMap.isEmpty());
        assertFalse(it.next());


        //U-Turns have high costs after the import process (u-turn penalty + turn costs)
        assertFalse(tcStorage.get(vehicleEncoding.turnRestriction(), con1_2_1, n1, con1_1_2));
        assertFalse(tcStorage.get(vehicleEncoding.turnRestriction(), con3_2_3, n3, con3_3_2));
        assertEquals(130, tcStorage.get(vehicleEncoding.turnCost(), con1_2_1, n1, con1_1_2), 0.1d);
        assertEquals(130, tcStorage.get(vehicleEncoding.turnCost(), con3_2_3, n3, con3_3_2), 0.1d);

        //Except for U-Turs in the middle of the road. We do not allow them here
        assertTrue(tcStorage.get(vehicleEncoding.turnRestriction(), con1_1_2, n2, con1_2_1));
        assertTrue(tcStorage.get(vehicleEncoding.turnRestriction(), con3_3_2, n2, con3_2_3));

        //Turn restriction in database has been taken over
        assertTrue(tcStorage.get(vehicleEncoding.turnRestriction(), con2_4_2, n2, con3_2_3));

        //all other turns are allowed
        assertFalse(tcStorage.get(vehicleEncoding.turnRestriction(), con1_1_2, n2, con3_2_3));
        assertFalse(tcStorage.get(vehicleEncoding.turnRestriction(), con2_4_2, n2, con1_2_1));

        //turn costs have been calculated (might be unrealistic due to unrealistic test data)
        assertEquals(40, tcStorage.get(vehicleEncoding.turnCost(), con1_1_2, n2, con3_2_3), 5.d); //left turn
        assertEquals(37, tcStorage.get(vehicleEncoding.turnCost(), con2_4_2, n2, con1_2_1), 5.d); //left turn
    }

    private void assertNextEdge(DecimalEncodedValue speedEnc, WayTypeEncoder wayTypeEnc, EdgeIterator it, Map<Integer, EdgeAssert> assertionMap) {
        assertTrue(it.next());
        EdgeAssert edgeAssert = assertionMap.remove(it.getAdjNode());
        assertNotNull(edgeAssert);
        edgeAssert.assertEdge(speedEnc, wayTypeEnc, it);
    }

    private EdgeAssert edgeAssertion(final int adjNode, final int edgeIt, final double distance, final double speed, final boolean isResidential) {
        return new EdgeAssert(adjNode) {
            @Override
            void assertEdge(DecimalEncodedValue speedEnc, WayTypeEncoder wayTypeEnc, EdgeIterator it) {
                assertEquals(adjNode, it.getAdjNode());
                assertEquals(edgeIt, it.getEdge());
                assertEquals(distance, it.getDistance(), 0d);
                assertEquals(speed, it.get(speedEnc), 10d); //graphhopper has default max speed of 140 km/h
                assertEquals(isResidential, WayTypeEncoder.isResidential(it.get(wayTypeEnc)));
            }
        };
    }

    static abstract class EdgeAssert {

        private int adjNode;

        public EdgeAssert(int adjNode) {
            this.adjNode = adjNode;
        }

        abstract void assertEdge(DecimalEncodedValue speedEnc, WayTypeEncoder wayTypeEnc, EdgeIterator it);

        public int getAdjNode() {
            return adjNode;
        }

    }


}
