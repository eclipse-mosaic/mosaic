/*
 * Copyright (c) 2025 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.mosaic.starter.MosaicSimulation;
import org.eclipse.mosaic.test.junit.LogAssert;
import org.eclipse.mosaic.test.junit.MosaicSimulationRule;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class SumoTaxiDispatchIT {

    @ClassRule
    public static MosaicSimulationRule simulationRule = new MosaicSimulationRule();

    private static MosaicSimulation.SimulationResult simulationResult;

    private final static String DISPATCH_APP_LOG = "apps/server_0/TaxiDispatchTestApp.log";

    private final static String TRAFFIC_LOG = "Traffic.log";

    @BeforeClass
    public static void runSimulation() {
        simulationResult = simulationRule.executeTestScenario("sumo-taxi-dispatch");
    }

    @Test
    public void executionSuccessful() {
        assertNull(simulationResult.exception);
        assertTrue(simulationResult.success);
    }

    @Test
    public void dispatchApplicationCalled() throws Exception {
        assertEquals(1, LogAssert.count(simulationRule, DISPATCH_APP_LOG,
                ".*Assigned reservation '0' of person 'agent_0' to vehicle 'veh_0'.*"
        ));

        // the following line should _not_ occur in Traffic.log
        assertEquals(0, LogAssert.count(simulationRule, TRAFFIC_LOG, ".*Could not dispatch taxi.*"));
    }

    @Test
    public void taxiPicksUpAndDropsOffPerson() throws Exception {
        // park at initial position
        assertEquals(1, LogAssert.count(simulationRule, TRAFFIC_LOG,
                ".*Vehicle veh_0 has parked at Position.*edge: -1349332095.*"
        ));

        // park to pick up
        assertEquals(1, LogAssert.count(simulationRule, TRAFFIC_LOG,
                ".*Vehicle veh_0 has parked at Position.*edge: 57371455#1.*"
        ));

        // park to drop off
        assertEquals(1, LogAssert.count(simulationRule, TRAFFIC_LOG,
                ".*Vehicle veh_0 has parked at Position.*edge: 1349332095.*"
        ));

    }
}
