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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.mosaic.starter.MosaicSimulation;
import org.eclipse.mosaic.test.junit.LogAssert;
import org.eclipse.mosaic.test.junit.MosaicSimulationRule;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class SumoPersonsIT {

    @ClassRule
    public static MosaicSimulationRule simulationRule = new MosaicSimulationRule();

    private static MosaicSimulation.SimulationResult simulationResult;

    private final static String AGENT_LOG = "apps/agent_0/HelloWorldApp.log";
    private final static String SERVER_LOG = "apps/server_0/HelloWorldServerApp.log";

    @BeforeClass
    public static void runSimulation() {
        simulationResult = simulationRule.executeTestScenario("sumo-persons");
    }

    @Test
    public void executionSuccessful() {
        assertNull(simulationResult.exception);
        assertTrue(simulationResult.success);
    }

    @Test
    public void agentApplicationUpdated() throws Exception {
        LogAssert.contains(simulationRule, AGENT_LOG, ".*Hello World! \\(at simulation time 11.*");
        LogAssert.contains(simulationRule, AGENT_LOG, ".*I'm still here at GeoPoint.*");
        LogAssert.contains(simulationRule, AGENT_LOG, ".*Bye bye World \\(at simulation time 2.*");
    }

    @Test
    public void cellMessageExchange() throws Exception {
        LogAssert.contains(simulationRule, SERVER_LOG, ".*Received message from agent_0 with content ping.*");
        LogAssert.contains(simulationRule, AGENT_LOG, ".*Received message from server_0 with content pong.*");
    }
}
