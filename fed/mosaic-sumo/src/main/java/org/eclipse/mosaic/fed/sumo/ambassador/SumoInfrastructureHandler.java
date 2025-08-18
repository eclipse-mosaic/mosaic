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

import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.INDUCTION_LOOP_DETECTOR_SUBSCRIPTION;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.LANE_AREA_DETECTOR_SUBSCRIPTION;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.LANE_PROPERTY_CHANGE;

import org.eclipse.mosaic.fed.sumo.util.SumoVehicleClassMapping;
import org.eclipse.mosaic.fed.sumo.util.TrafficSignManager;
import org.eclipse.mosaic.interactions.traffic.InductionLoopDetectorSubscription;
import org.eclipse.mosaic.interactions.traffic.LaneAreaDetectorSubscription;
import org.eclipse.mosaic.interactions.traffic.LanePropertyChange;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignLaneAssignmentChange;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignRegistration;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignSpeedLimitChange;
import org.eclipse.mosaic.lib.enums.VehicleClass;
import org.eclipse.mosaic.lib.objects.trafficsign.TrafficSignLaneAssignment;
import org.eclipse.mosaic.lib.objects.trafficsign.TrafficSignSpeed;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

/**
 * All the ambassador's logic which takes care of infrastructure elements (variable traffic signs, detectors).
 */
public class SumoInfrastructureHandler extends AbstractHandler {

    /**
     * Manages traffic signs to be added as POIs to SUMO (e.g. for visualization)
     */
    private TrafficSignManager trafficSignManager;

    synchronized void handleTrafficSignRegistration(TrafficSignRegistration trafficSignRegistration) throws InternalFederateException {
        if (trafficSignRegistration.getTrafficSign() instanceof TrafficSignSpeed trafficSign) {
            getTrafficSignManager().addSpeedSign(trafficSign);
        } else if (trafficSignRegistration.getTrafficSign() instanceof TrafficSignLaneAssignment trafficSign) {
            getTrafficSignManager().addLaneAssignmentSign(trafficSign);
        }
    }

    synchronized void handleSpeedLimitChange(TrafficSignSpeedLimitChange trafficSignSpeedLimitChange) throws InternalFederateException {
        getTrafficSignManager().changeVariableSpeedSign(
                trafficSignSpeedLimitChange.getTrafficSignId(),
                trafficSignSpeedLimitChange.getLane(),
                trafficSignSpeedLimitChange.getSpeedLimit()
        );
    }

    synchronized void handleLaneAssignmentChange(TrafficSignLaneAssignmentChange trafficSignLaneAssignmentChange) throws InternalFederateException {
        getTrafficSignManager().changeVariableLaneAssignmentSign(
                trafficSignLaneAssignmentChange.getTrafficSignId(),
                trafficSignLaneAssignmentChange.getLane(),
                trafficSignLaneAssignmentChange.getAllowedVehicleClasses()
        );
    }

    public TrafficSignManager getTrafficSignManager() {
        if (trafficSignManager == null) {
            trafficSignManager = new TrafficSignManager(sumoConfig.trafficSignLaneWidth);
            try {
                File sumoWorkingDir = new File(federateDescriptor.getHost().workingDirectory, federateDescriptor.getId());
                trafficSignManager.configure(bridge, sumoWorkingDir);
            } catch (Exception e) {
                log.error("Could not load TrafficSignManager. No traffic signs will be displayed.");
            }
        }
        return trafficSignManager;
    }

    /**
     * Extract data from received {@link LanePropertyChange} interaction and forward to SUMO.
     *
     * @param lanePropertyChange Interaction that indicating to change the lane.
     * @throws InternalFederateException Exception if an error occurred while changing the lane.
     */
    synchronized void handleLanePropertyChange(LanePropertyChange lanePropertyChange) throws InternalFederateException {

        log.info("{} at simulation time {}", LANE_PROPERTY_CHANGE, TIME.format(lanePropertyChange.getTime()));

        final String laneId = lanePropertyChange.getEdgeId() + "_" + lanePropertyChange.getLaneIndex();

        if (lanePropertyChange.getAllowedVehicleClasses() != null) {
            log.info("Change allowed vehicle classes of lane with ID={}", laneId);

            List<String> allowedVehicleClasses = lanePropertyChange.getAllowedVehicleClasses().stream()
                    .map(SumoVehicleClassMapping::toSumo).toList();
            bridge.getSimulationControl().setLaneAllowedVehicles(laneId, allowedVehicleClasses);
        }

        if (lanePropertyChange.getDisallowedVehicleClasses() != null) {
            log.info("Change disallowed vehicle classes of lane with ID={}", laneId);

            if (lanePropertyChange.getDisallowedVehicleClasses().containsAll(Lists.newArrayList(VehicleClass.values()))) {
                bridge.getSimulationControl().setLaneAllowedVehicles(laneId, Lists.newArrayList());
            } else {
                List<String> disallowedVehicleClasses = lanePropertyChange.getDisallowedVehicleClasses().stream()
                        .map(SumoVehicleClassMapping::toSumo).toList();
                bridge.getSimulationControl().setLaneDisallowedVehicles(laneId, disallowedVehicleClasses);
            }
        }

        if (lanePropertyChange.getMaxSpeed() != null) {
            log.info("Change max speed of lane with ID={}", laneId);
            bridge.getSimulationControl().setLaneMaxSpeed(laneId, lanePropertyChange.getMaxSpeed());
        }
    }

    /**
     * Extract data from received {@link InductionLoopDetectorSubscription} interaction and forward to SUMO.
     *
     * @param inductionLoopDetectorSubscription Interaction that is indicating to subscribe for induction loop.
     * @throws InternalFederateException Exception if an error occurred while subscribe to induction loop.
     */
    synchronized void handleDetectorSubscription(InductionLoopDetectorSubscription inductionLoopDetectorSubscription) throws InternalFederateException {
        log.info(
                INDUCTION_LOOP_DETECTOR_SUBSCRIPTION + " Subscribe to InductionLoop with ID={}",
                inductionLoopDetectorSubscription.getInductionLoopId()
        );

        bridge.getSimulationControl().subscribeForInductionLoop(
                inductionLoopDetectorSubscription.getInductionLoopId(),
                inductionLoopDetectorSubscription.getTime(),
                ambassador.getEndTime()
        );
    }

    /**
     * Extract data from received {@link LaneAreaDetectorSubscription} interaction and forward to SUMO.
     *
     * @param laneAreaDetectorSubscription Interaction that indicating to subscribe for Lane area detector.
     * @throws InternalFederateException Exception if an error occurred while subscribe to lane area detector.
     */
    synchronized void handleDetectorSubscription(LaneAreaDetectorSubscription laneAreaDetectorSubscription) throws InternalFederateException {
        log.info(
                LANE_AREA_DETECTOR_SUBSCRIPTION + " Subscribe to LaneArea with ID={}",
                laneAreaDetectorSubscription.getLaneAreaId()
        );

        bridge.getSimulationControl().subscribeForLaneArea(
                laneAreaDetectorSubscription.getLaneAreaId(),
                laneAreaDetectorSubscription.getTime(),
                ambassador.getEndTime()
        );
    }
}
