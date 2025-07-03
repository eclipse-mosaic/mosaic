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

package org.eclipse.mosaic.fed.sumo.bridge;

import static org.junit.Assert.assertEquals;

import org.eclipse.mosaic.fed.sumo.config.CSumo;
import org.eclipse.mosaic.fed.sumo.junit.SumoRunner;
import org.eclipse.mosaic.fed.sumo.junit.SumoTraciRule;
import org.eclipse.mosaic.interactions.traffic.TrafficDetectorUpdates;
import org.eclipse.mosaic.lib.objects.traffic.InductionLoopInfo;
import org.eclipse.mosaic.lib.objects.traffic.LaneAreaDetectorInfo;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(SumoRunner.class)
public class FlowDensityTest {

    private final File scenarioConfig = FileUtils.toFile(getClass().getResource("/density-flow-scenario/density_flow.sumocfg"));

    private static final CSumo sumoConfig = new CSumo();

    static {
        sumoConfig.trafficFlowMeasurementWindowInS = 10;
    }


    @Rule
    public final SumoTraciRule traciRule = new SumoTraciRule(scenarioConfig, sumoConfig);

    /**
     * This test calculates the expected density based on the flow measurements and checks
     * if the measured density equals those calculations.
     *
     * @throws InternalFederateException if traci command execution fails
     */
    @Test
    public void testDensityCalculation() throws InternalFederateException {
        final TraciClientBridge traci = traciRule.getTraciClient();
        traci.getSimulationControl().subscribeForInductionLoop("area_entry_0", 0, 1000 * TIME.SECOND);
        traci.getSimulationControl().subscribeForInductionLoop("area_entry_1", 0, 1000 * TIME.SECOND);
        traci.getSimulationControl().subscribeForInductionLoop("area_exit_0", 0, 1000 * TIME.SECOND);
        traci.getSimulationControl().subscribeForInductionLoop("area_exit_1", 0, 1000 * TIME.SECOND);
        traci.getSimulationControl().subscribeForLaneArea("area_0", 0, 1000 * TIME.SECOND);
        traci.getSimulationControl().subscribeForLaneArea("area_1", 0, 1000 * TIME.SECOND);

        double areaLengthKm = 0.5;

        double prevDensity = 0;
        for (int i = 0; i < 700; i++) {
            TrafficDetectorUpdates trafficDetectorUpdates =
                    traci.getSimulationControl().simulateUntil(i * TIME.SECOND).trafficDetectorUpdates();

            if (i % sumoConfig.trafficFlowMeasurementWindowInS == 0) {
                double currentFlowIn = 0;
                double currentFlowOut = 0;
                for (InductionLoopInfo inductionLoopInfo : trafficDetectorUpdates.getUpdatedInductionLoops()) {
                    if (inductionLoopInfo.getName().contains("entry")) {
                        currentFlowIn += inductionLoopInfo.getTrafficFlow();
                    } else if (inductionLoopInfo.getName().contains("exit")) {
                        currentFlowOut += inductionLoopInfo.getTrafficFlow();
                    }
                }

                double measuredDensity = 0;
                for (LaneAreaDetectorInfo laneArea : trafficDetectorUpdates.getUpdatedLaneAreaDetectors()) {
                    measuredDensity += laneArea.getTrafficDensity();
                }

                double expectedDensity = prevDensity
                        + (currentFlowIn - currentFlowOut)
                        * (sumoConfig.trafficFlowMeasurementWindowInS / 3600d / areaLengthKm);

                assertEquals(expectedDensity, measuredDensity, 4.0);

                prevDensity = expectedDensity;
            }
        }
    }
}
