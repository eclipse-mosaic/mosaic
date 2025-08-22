/*
 * Copyright (c) 2023 Fraunhofer FOKUS and others. All rights reserved.
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

/**
 * This test checks if messages can be sent during the shutdown method of applications via cell and SNS.
 * Furthermore, it is checked if both CELL and SNS remove all entities either on arrival or simulation end.
 */
public class CellSnsSendMessageOnShutdownIT {

    @ClassRule
    public static MosaicSimulationRule simulationRule = new MosaicSimulationRule().logLevelOverride("DEBUG");

    private static MosaicSimulation.SimulationResult simulationResult;

    private static final String CELL_LOG = "Cell.log";
    private static final String SNS_LOG = "Communication.log";
    private final static String SERVER_LOG = "apps/server_0/ServerReceiverApp.log";
    private final static String RSU_LOG = "apps/rsu_0/RsuReceiverApp.log";

    @BeforeClass
    public static void runSimulation() {
        simulationResult = simulationRule.executeTestScenario("cell-sns-send-message-on-shutdown");
    }

    @Test
    public void executionSuccessful() throws Exception {
        assertNull(simulationResult.exception);
        assertTrue(simulationResult.success);
    }

    @Test
    public void assertShutdownReceptionsRsu() throws Exception {
        LogAssert.contains(simulationRule, SERVER_LOG, ".*Received ShutdownMessage from veh_0.*");
        LogAssert.contains(simulationRule, SERVER_LOG, ".*Received ShutdownMessage from veh_1.*");
        LogAssert.contains(simulationRule, SERVER_LOG, ".*Received ShutdownMessage from veh_2.*");
        LogAssert.contains(simulationRule, SERVER_LOG, ".*Received ShutdownMessage from veh_3.*");
        LogAssert.contains(simulationRule, SERVER_LOG, ".*Received ShutdownMessage from veh_4.*");
    }

    @Test
    public void assertShutdownReceptionsServer() throws Exception {
        LogAssert.contains(simulationRule, RSU_LOG, ".*Received ShutdownMessage from veh_0.*");
        LogAssert.contains(simulationRule, RSU_LOG, ".*Received ShutdownMessage from veh_1.*");
        LogAssert.contains(simulationRule, RSU_LOG, ".*Received ShutdownMessage from veh_2.*");
        LogAssert.contains(simulationRule, RSU_LOG, ".*Received ShutdownMessage from veh_3.*");
        LogAssert.contains(simulationRule, RSU_LOG, ".*Received ShutdownMessage from veh_4.*");
    }
    @Test
    public void assertDisableAndRemoval() throws Exception {
        LogAssert.contains(simulationRule, CELL_LOG, ".*Disabled Cell Communication for mobile node=veh_4, t=206.*");
        LogAssert.contains(simulationRule, CELL_LOG, ".*Removed MOBILE NODE \\(id=veh_4\\) from simulation, t=206.*");
        LogAssert.contains(simulationRule, CELL_LOG, ".*Disabled Cell Communication for entity=server_0, t=220.*");

        LogAssert.contains(simulationRule, SNS_LOG, ".*Removed Node id=veh_4 @time=206.*");
        LogAssert.contains(simulationRule, SNS_LOG, ".*Disabled Wifi for Node id=rsu_0 @time=220.*");
    }
}
