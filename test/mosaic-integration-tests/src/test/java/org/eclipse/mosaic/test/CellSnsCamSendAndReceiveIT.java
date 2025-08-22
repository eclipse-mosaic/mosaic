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

import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.starter.MosaicSimulation;
import org.eclipse.mosaic.test.junit.LogAssert;
import org.eclipse.mosaic.test.junit.MosaicSimulationRule;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * This integration tests validates the core functionalities of the native MOSAIC communication simulators
 * <b>Cell</b> and <b>SNS</b>.
 * Functionalities tested:
 * <ul>
 *     <li/> Proper handling of different simulation entities in the simulators
 *     <li/> Communication using Cell and Adhoc modules
 *     <li/> Activation of Communication Modules
 *     <li/> CAM building and sending
 *     <li/> TCP acknowledgement test
 * </ul>
 * Units have the following roles:
 * <ul>
 *     <li/> veh0: Sending Cams
 *     <li/> veh1: Receiving and returning RoundTripMessage
 *     <li/> veh2: Sending Cams
 * </ul>
 */
public class CellSnsCamSendAndReceiveIT {

    @ClassRule
    public static MosaicSimulationRule simulationRule = new MosaicSimulationRule().logLevelOverride("DEBUG");

    private static MosaicSimulation.SimulationResult simulationResult;

    private final static String CELL_LOG = "Cell.log";
    private final static String SNS_LOG = "Communication.log";
    private final static String VISUALIZER_CSV = "visualizer.csv";
    private final static String VEH_0_SEND_CAM_APP_CELL_LOG = "apps/veh_0/SendCamAppCell.log";
    private final static String VEH_1_SEND_CAM_APP_CELL_LOG = "apps/veh_1/SendCamAppCell.log";
    private final static String RSU_0_RECEIVE_MSG_APP_CELL_LOG = "apps/rsu_0/ReceiveMsgAppCell.log";
    private final static String RSU_1_RECEIVE_MSG_APP_CELL_LOG = "apps/rsu_1/ReceiveMsgAppCell.log";
    private final static String RSU_2_RECEIVE_MSG_APP_CELL_LOG = "apps/rsu_2/ReceiveMsgAppCell.log";
    private final static String RSU_3_RECEIVE_MSG_APP_ADHOC_LOG = "apps/rsu_3/ReceiveMsgAppAdHoc.log";
    private final static String TMC_ROUND_TRIP = "apps/tmc_0/SendAndReceiveRoundTripMessage.log";
    private final static String VEH_ROUND_TRIP = "apps/veh_3/ReceiveAndReturnRoundTripMessage.log";
    private final static String SERVER_NACK_RECEIVER = "apps/server_0/NackReceivingServer.log";
    private final static String SERVER_NO_CELL = "apps/server_3/NoCellCommunicationServer.log";
    private final static String SERVER_NO_GROUP = "apps/server_4/NoCellCommunicationServer.log";

    @BeforeClass
    public static void runSimulation() {
        simulationResult = simulationRule.executeTestScenario("cell-sns-cam-send-and-receive");
    }

    @Test
    public void executionSuccessful() throws Exception {
        assertNull(simulationResult.exception);
        assertTrue(simulationResult.success);
        // proper finish of cell
        assertOccurrences(CELL_LOG, ".*CellAmbassador - FinishSimulation.*", 1);
    }

    @Test
    public void allLogsCreated() {
        LogAssert.exists(simulationRule, CELL_LOG);
        LogAssert.exists(simulationRule, SNS_LOG);
        LogAssert.exists(simulationRule, VISUALIZER_CSV);
        LogAssert.exists(simulationRule, VEH_0_SEND_CAM_APP_CELL_LOG);
        LogAssert.exists(simulationRule, VEH_1_SEND_CAM_APP_CELL_LOG);
        LogAssert.exists(simulationRule, RSU_0_RECEIVE_MSG_APP_CELL_LOG);
        LogAssert.exists(simulationRule, RSU_1_RECEIVE_MSG_APP_CELL_LOG);
        LogAssert.exists(simulationRule, RSU_2_RECEIVE_MSG_APP_CELL_LOG);
        LogAssert.exists(simulationRule, RSU_3_RECEIVE_MSG_APP_ADHOC_LOG);
        LogAssert.exists(simulationRule, TMC_ROUND_TRIP);
        LogAssert.exists(simulationRule, VEH_ROUND_TRIP);
        LogAssert.exists(simulationRule, SERVER_NACK_RECEIVER);
        LogAssert.exists(simulationRule, SERVER_NO_CELL);
        LogAssert.exists(simulationRule, SERVER_NO_GROUP);
    }

    @Test
    public void cellChainProperlySetup() throws Exception {
        LogAssert.contains(
                simulationRule,
                CELL_LOG,
                ".*ChainManager - Adding Module Upstream.*"
        );
        LogAssert.contains(
                simulationRule,
                CELL_LOG,
                ".*ChainManager - Adding Module Geocaster.*"
        );
        LogAssert.contains(
                simulationRule,
                CELL_LOG,
                ".*ChainManager - Adding Module Downstream.*"
        );
    }

    @Test
    public void cellAllUnitsAdded() throws Exception {
        // count all unit registrations
        assertOccurrences(CELL_LOG, ".*CellAmbassador - Added RSU \\(id=rsu_0.*", 1);
        assertOccurrences(CELL_LOG, ".*CellAmbassador - Added RSU \\(id=rsu_1.*", 1);
        assertOccurrences(CELL_LOG, ".*CellAmbassador - Added RSU \\(id=rsu_2.*", 1);
        assertOccurrences(CELL_LOG, ".*CellAmbassador - Added RSU \\(id=rsu_3.*", 1);
        assertOccurrences(CELL_LOG, ".*CellAmbassador - Added MOBILE NODE \\(id=veh_0.*", 1);
        assertOccurrences(CELL_LOG, ".*CellAmbassador - Added MOBILE NODE \\(id=veh_1.*", 1);
        assertOccurrences(CELL_LOG, ".*CellAmbassador - Added Server \\(TMC\\) \\(id=tmc_0.*", 1);
    }

    @Test
    public void cellModulesActivationsAndDeactivations() throws Exception {
        // activations
        assertOccurrences(CELL_LOG, ".*CellAmbassador - Enabled \\(Configured\\) Cell Communication for entity=rsu.*", 3);
        assertOccurrences(CELL_LOG, ".*CellAmbassador - Enabled \\(Configured\\) Cell Communication for entity=tmc.*", 1);
        assertOccurrences(CELL_LOG, ".*CellAmbassador - Enabled \\(Configured\\) Cell Communication for mobile node=veh.*", 3);
        // deactivations
        assertOccurrences(CELL_LOG, ".*CellAmbassador - Disabled Cell Communication for mobile node.*", 3);
    }

    @Test
    public void cellMessagesHandledInModules() throws Exception {
        // send 150 uplink messages from veh_0 and veh_1 + 2*RoundTripMessage + 2*Messages for nack + 1*Nack-test = 305
        LogAssert.contains(simulationRule, CELL_LOG, ".*ChainManager - \\[Upstream\\] Processed messages: 305.*");
        /* received (26 rsu_0 + 30 rsu_1 + 30 rsu_2 + 150 self) messages from veh_0 = 236
        and (150 broadcast + 2 region overlap) - (120 messages not sendable due to capacity limit) messages from veh_1 = 32
        and 2*RoundTripMessage = 2
        and 2*Messages for nack test = 2
        and 1*Nack-Test ==> total 267*/
        LogAssert.contains(simulationRule, CELL_LOG, ".*ChainManager - \\[Downstream\\] Processed messages: 273.*");
    }

    @Test
    public void applicationsVehicleCommunicationEnabledAndDisabled() throws Exception {
        // enable
        assertOccurrences(VEH_0_SEND_CAM_APP_CELL_LOG, ".*communicationState \\- adhocEnabled=false\\, cellEnabled=true.*", 1);
        assertOccurrences(VEH_1_SEND_CAM_APP_CELL_LOG, ".*communicationState \\- adhocEnabled=false\\, cellEnabled=true.*", 1);
        // disable
        assertOccurrences(VEH_0_SEND_CAM_APP_CELL_LOG, ".*communicationState \\- adhocEnabled=false\\, cellEnabled=false.*", 1);
        assertOccurrences(VEH_1_SEND_CAM_APP_CELL_LOG, ".*communicationState \\- adhocEnabled=false\\, cellEnabled=false.*", 1);
    }

    @Test
    public void applicationsVehicleSentMessages() throws Exception {
        assertOccurrences(VEH_0_SEND_CAM_APP_CELL_LOG, ".*Sent CAM.*", 150);
        assertOccurrences(VEH_1_SEND_CAM_APP_CELL_LOG, ".*Sent CAM.*", 150);
    }

    @Test
    public void applicationsCamReceptions() throws Exception {
        // geo unicast
        // start veh_0 inside cam-range of 150m
        assertOccurrences(RSU_0_RECEIVE_MSG_APP_CELL_LOG, ".*Received CAM from veh_0.*", 26);
        // start veh_0 outside cam-range of 150m (2*150m and 1msg/10m = 30 messages)
        assertOccurrences(RSU_1_RECEIVE_MSG_APP_CELL_LOG, ".*Received CAM from veh_0.*", 30, 1);
        // start veh_0 outside cam-range of 150m (2*150m and 1msg/10m = 30 messages)
        assertOccurrences(RSU_2_RECEIVE_MSG_APP_CELL_LOG, ".*Received CAM from veh_0.*", 30, 1);

        // geo broadcast
        // locate rsu_0 and rsu_1 in the same mbms broadcast region
        assertOccurrences(RSU_0_RECEIVE_MSG_APP_CELL_LOG, ".*Received CAM from veh_1.*", 8);
        // locate rsu_0 and rsu_1 in the same mbms broadcast region
        assertOccurrences(RSU_1_RECEIVE_MSG_APP_CELL_LOG, ".*Received CAM from veh_1.*", 8);
        // locate rsu_0 and rsu_2 in the same mbms broadcast region
        assertOccurrences(RSU_2_RECEIVE_MSG_APP_CELL_LOG, ".*Received CAM from veh_1.*", 15);

        // adhoc
        // test pattern no interference with ad hoc rsus
        assertOccurrences(RSU_3_RECEIVE_MSG_APP_ADHOC_LOG, ".*Received CAM from veh_0.*", 0);
        assertOccurrences(RSU_3_RECEIVE_MSG_APP_ADHOC_LOG, ".*Received CAM from veh_1.*", 0);
        assertOccurrences(RSU_3_RECEIVE_MSG_APP_ADHOC_LOG, ".*Received CAM from veh_2.*", 72);
        assertOccurrences(RSU_3_RECEIVE_MSG_APP_ADHOC_LOG, ".*Received CAM from veh_3.*", 0);
    }

    @Test
    public void visualizerHandoverAmount() throws Exception {
        assertOccurrences(VISUALIZER_CSV, "HANDOVER.*", 17);
        LogAssert.contains(simulationRule, VISUALIZER_CSV, "HANDOVER;2000000000;veh_0;null;erpost");
    }

    /**
     * This checks whether a cell message takes the expected amount to be send from tmc to vehicle
     * and from vehicle to tmc.
     * Should be 50ms for tmc uplink + 200ms for vehicle downlink + 200ms for vehicle uplink + 50ms for tmc downlink.
     */
    @Test
    public void roundTripMessageTakesRightAmountOfTime() throws Exception {
        long timeOfSending = 310 * TIME.SECOND;
        long delayTmcUpload = 50 * TIME.MILLI_SECOND;
        long delayTmcDownload = 50 * TIME.MILLI_SECOND;
        long delayVehUpload = 86 * TIME.MILLI_SECOND; //TODO check those values, are those random?
        long delayVehDownload = 67 * TIME.MILLI_SECOND; //TODO check those values, are those random? if so, than its not ideal for a test

        long timeFromTmcToVeh = delayTmcUpload + delayVehDownload;
        long timeFromVehToTmc = delayVehUpload + delayTmcDownload;

        // message receives
        LogAssert.contains(
                simulationRule,
                VEH_ROUND_TRIP,
                ".*Received round trip message #[0-9]+ at time " + (timeOfSending + timeFromTmcToVeh) + ".*"
        );
        LogAssert.contains(
                simulationRule,
                TMC_ROUND_TRIP,
                ".*Received round trip message #[0-9]+ at time "
                        + (timeOfSending + timeFromTmcToVeh + timeFromVehToTmc) + ".*"
        );
        // acknowledgements
        LogAssert.contains(
                simulationRule,
                TMC_ROUND_TRIP,
                ".*Received acknowledgement for round trip message #[0-9]+ and \\[acknowledged=true\\] \\(at simulation time "
                        + TIME.format(timeOfSending + timeFromTmcToVeh) + "\\).*"
        );
        LogAssert.contains(
                simulationRule,
                VEH_ROUND_TRIP,
                ".*Received acknowledgement for round trip message #[0-9]+ and \\[acknowledged=true\\] \\(at simulation time "
                        + TIME.format(timeOfSending + timeFromTmcToVeh + timeFromVehToTmc) + "\\).*"
        );
    }

    @Test
    public void tcpNacksAreSent() throws Exception {
        // packet loss nack
        LogAssert.contains(
                simulationRule,
                SERVER_NACK_RECEIVER,
                ".*Received acknowledgement=false for message=[0-9]+ "
                        + "from=NetworkAddress\\{address=/10\\.5\\.0\\.[0-9]+\\} with nackReasons=\\[PACKET_LOSS\\].*"
        );
        // node capacity exceeded nack
        LogAssert.contains(
                simulationRule,
                SERVER_NACK_RECEIVER,
                ".*Received acknowledgement=false for message=[0-9]+ "
                        + "from=NetworkAddress\\{address=/10\\.5\\.0\\.[0-9]+\\} with nackReasons=\\[NODE_CAPACITY_EXCEEDED\\].*"
        );
    }

    @Test
    public void noCellServerNotRegistered() throws Exception {
        LogAssert.contains(simulationRule, CELL_LOG,
                ".*No server properties for server group \"NoCellCommunication\" found in \"network\\.json\" config-file.*");
        LogAssert.contains(simulationRule, SERVER_NO_CELL, ".*NoCellCommunicationServer setup\\..*");
        LogAssert.contains(simulationRule, CELL_LOG,
                ".*Server \\(id\\=server_4\\) has NO application or group and is ignored in communication simulation.*");
        LogAssert.contains(simulationRule, SERVER_NO_GROUP, ".*NoCellCommunicationServer setup\\..*");
    }

    private void assertOccurrences(String logFile, String logPattern, int amount) throws Exception {
        assertOccurrences(logFile, logPattern, amount, 0);
    }

    private void assertOccurrences(String logFile, String logPattern, int amount, int delta) throws Exception {
        assertEquals(
                amount,
                LogAssert.count(
                        simulationRule,
                        logFile,
                        logPattern
                ),
                delta
        );
    }
}
