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

public class ReleaseSievekingplatzIT {

    @ClassRule
    public static MosaicSimulationRule simulationRule = new MosaicSimulationRule();

    private static MosaicSimulation.SimulationResult simulationResult;

    @BeforeClass
    public static void runSimulation() {
        simulationResult = simulationRule.executeReleaseScenario("Sievekingplatz");
    }

    @Test
    public void executionSuccessful() {
        assertNull(simulationResult.exception);
        assertTrue(simulationResult.success);
    }

    @Test
    public void deployAppForOnlyOneOfTwoBusses() throws Exception {
        assertEquals(2, LogAssert.count(simulationRule, "Mapping.log",
                ".*Mapping Scenario Vehicle.* type=Bus.*"
        ));
        assertEquals(1, LogAssert.count(simulationRule, "Mapping.log",
                ".*Mapping Scenario Vehicle.* type=Bus.*, apps=\\[org.*"
        ));
    }
}
