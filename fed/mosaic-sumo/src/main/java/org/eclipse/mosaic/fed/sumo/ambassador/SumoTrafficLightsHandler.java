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

package org.eclipse.mosaic.fed.sumo.ambassador;

import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.TRAFFIC_LIGHTS_STATE_CHANGE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.TRAFFIC_LIGHT_SUBSCRIPTION;

import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioTrafficLightRegistration;
import org.eclipse.mosaic.interactions.traffic.TrafficLightStateChange;
import org.eclipse.mosaic.interactions.traffic.TrafficLightSubscription;
import org.eclipse.mosaic.interactions.traffic.TrafficLightUpdates;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroupInfo;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All the ambassador's logic which takes care of traffic lights.
 */
public class SumoTrafficLightsHandler extends AbstractHandler {


    /**
     * Fetches traffic light data, which contains program, position, controlled
     * roads.
     *
     * @param time Current time
     */
    void initializeTrafficLights(long time) throws InternalFederateException, IOException, IllegalValueException {
        List<String> trafficLightGroupIds = bridge.getSimulationControl().getTrafficLightGroupIds();

        List<TrafficLightGroup> trafficLightGroups = new ArrayList<>();
        Map<String, Collection<String>> trafficLightGroupLaneMap = new HashMap<>();
        for (String trafficLightGroupId : trafficLightGroupIds) {
            try {
                TrafficLightGroup trafficLightGroup = bridge.getTrafficLightControl().getTrafficLightGroup(trafficLightGroupId);
                if (trafficLightGroup == null) {  // Reading traffic light groups doesn't work for railway signals
                    continue;
                }
                trafficLightGroups.add(trafficLightGroup);
                Collection<String> ctrlLanes = bridge.getTrafficLightControl().getControlledLanes(trafficLightGroupId);
                trafficLightGroupLaneMap.put(trafficLightGroupId, ctrlLanes);
            } catch (InternalFederateException e) {
                log.warn("Could not add traffic light {} to simulation. Skipping.", trafficLightGroupId);
            }
        }
        Interaction stlRegistration = new ScenarioTrafficLightRegistration(time, trafficLightGroups, trafficLightGroupLaneMap);
        rti.triggerInteraction(stlRegistration);
    }

    /**
     * Extract data from received {@link TrafficLightStateChange} interaction and forward
     * to SUMO.
     *
     * @param trafficLightStateChange Interaction indicates the state of traffic lights.
     * @throws InternalFederateException Exception if a invalid value is used.
     */
    synchronized void handleStateChange(TrafficLightStateChange trafficLightStateChange) throws InternalFederateException {
        try {
            log.info(TRAFFIC_LIGHTS_STATE_CHANGE_REQ);

            String trafficLightGroupId = trafficLightStateChange.getTrafficLightGroupId();

            switch (trafficLightStateChange.getParameterType()) {

                case ChangePhase:
                    log.info(
                            "Changing the current phase of traffic light group '{}' to phase with index '{}'",
                            trafficLightGroupId, trafficLightStateChange.getPhaseIndex()
                    );
                    bridge.getTrafficLightControl().setPhaseIndex(trafficLightGroupId, trafficLightStateChange.getPhaseIndex());
                    break;

                case RemainingDuration:
                    double durationInSeconds = trafficLightStateChange.getPhaseRemainingDuration() / 1000; //ms -> s
                    log.info(
                            "Changing remaining phase duration of traffic light group='{}' to '{}' seconds",
                            trafficLightGroupId, durationInSeconds
                    );
                    bridge.getTrafficLightControl().setPhaseRemainingDuration(trafficLightGroupId, durationInSeconds);
                    break;

                case ProgramId:
                    log.info(
                            "Changing program of traffic light group '{}' to program id '{}'",
                            trafficLightGroupId, trafficLightStateChange.getProgramId()
                    );
                    bridge.getTrafficLightControl().setProgramById(trafficLightGroupId, trafficLightStateChange.getProgramId());
                    break;

                case ChangeProgramWithPhase:
                    log.info(
                            "Changing program of traffic light group '{}' to program id '{}' and setting the phase to '{}'",
                            trafficLightGroupId, trafficLightStateChange.getProgramId(), trafficLightStateChange.getPhaseIndex()
                    );
                    bridge.getTrafficLightControl().setProgramById(trafficLightGroupId, trafficLightStateChange.getProgramId());
                    bridge.getTrafficLightControl().setPhaseIndex(trafficLightGroupId, trafficLightStateChange.getPhaseIndex());
                    break;

                case ChangeToCustomState:
                    log.info("Changing to custom states for traffic light group '{}'.", trafficLightGroupId);
                    bridge.getTrafficLightControl().setPhase(trafficLightGroupId, trafficLightStateChange.getCustomStateList());
                    break;
                default:
                    log.warn("Discard this TrafficLightStateChange interaction (paramType={}).",
                            trafficLightStateChange.getParameterType());
                    return;
            }

            String programId = bridge.getTrafficLightControl().getCurrentProgram(trafficLightGroupId);
            int phaseIndex = bridge.getTrafficLightControl().getCurrentPhase(trafficLightGroupId);
            long assumedNextTimeSwitch = (long) (bridge.getTrafficLightControl().getNextSwitchTime(trafficLightGroupId) * TIME.SECOND);
            List<TrafficLightState> currentStates = bridge.getTrafficLightControl().getCurrentStates(trafficLightGroupId);

            Map<String, TrafficLightGroupInfo> changedTrafficLightGroupInfo = new HashMap<>();
            changedTrafficLightGroupInfo.put(trafficLightGroupId, new TrafficLightGroupInfo(
                    trafficLightStateChange.getTrafficLightGroupId(),
                    programId,
                    phaseIndex,
                    assumedNextTimeSwitch,
                    currentStates
            ));

            // now tell the RTI that an update happened so that the update can reach other federates
            this.rti.triggerInteraction(
                    new TrafficLightUpdates(trafficLightStateChange.getTime(), changedTrafficLightGroupInfo)
            );
        } catch (IllegalValueException e) {
            throw new InternalFederateException(e);
        }
    }

    /**
     * Extracts data from received TrafficLightSubscription message and forwards to SUMO.
     *
     * @param trafficLightSubscription Interaction that indicating to subscribe for a traffic light.
     * @throws InternalFederateException Exception if an error occurred while subscribe to the traffic light.
     */
    synchronized void handleSubscription(TrafficLightSubscription trafficLightSubscription) throws InternalFederateException {
        log.info("{} at simulation time {}: Subscribe to Traffic light group with ID={}",
                TRAFFIC_LIGHT_SUBSCRIPTION, TIME.format(trafficLightSubscription.getTime()),
                trafficLightSubscription.getTrafficLightGroupId()
        );

        bridge.getSimulationControl().subscribeForTrafficLight(
                trafficLightSubscription.getTrafficLightGroupId(),
                trafficLightSubscription.getTime(),
                ambassador.getEndTime()
        );
    }
}
