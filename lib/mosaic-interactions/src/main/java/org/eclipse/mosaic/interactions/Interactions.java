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

package org.eclipse.mosaic.interactions;

import org.eclipse.mosaic.interactions.agent.AgentRemoval;
import org.eclipse.mosaic.interactions.agent.AgentRouteChange;
import org.eclipse.mosaic.interactions.agent.AgentUpdates;
import org.eclipse.mosaic.interactions.application.ItefLogging;
import org.eclipse.mosaic.interactions.application.SumoTraciRequest;
import org.eclipse.mosaic.interactions.application.SumoTraciResponse;
import org.eclipse.mosaic.interactions.communication.AdHocCommunicationConfiguration;
import org.eclipse.mosaic.interactions.communication.CellularCommunicationConfiguration;
import org.eclipse.mosaic.interactions.communication.CellularHandoverUpdates;
import org.eclipse.mosaic.interactions.communication.V2xMessageAcknowledgement;
import org.eclipse.mosaic.interactions.communication.V2xMessageReception;
import org.eclipse.mosaic.interactions.communication.V2xMessageRemoval;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.interactions.electricity.BatteryChargingStart;
import org.eclipse.mosaic.interactions.electricity.BatteryChargingStop;
import org.eclipse.mosaic.interactions.electricity.ChargingStationUpdate;
import org.eclipse.mosaic.interactions.electricity.VehicleBatteryUpdates;
import org.eclipse.mosaic.interactions.electricity.VehicleChargingDenial;
import org.eclipse.mosaic.interactions.electricity.VehicleChargingStartRequest;
import org.eclipse.mosaic.interactions.electricity.VehicleChargingStopRequest;
import org.eclipse.mosaic.interactions.environment.EnvironmentSensorActivation;
import org.eclipse.mosaic.interactions.environment.EnvironmentSensorUpdates;
import org.eclipse.mosaic.interactions.environment.GlobalEnvironmentUpdates;
import org.eclipse.mosaic.interactions.environment.LidarUpdates;
import org.eclipse.mosaic.interactions.mapping.AgentRegistration;
import org.eclipse.mosaic.interactions.mapping.ChargingStationRegistration;
import org.eclipse.mosaic.interactions.mapping.RsuRegistration;
import org.eclipse.mosaic.interactions.mapping.ServerRegistration;
import org.eclipse.mosaic.interactions.mapping.TmcRegistration;
import org.eclipse.mosaic.interactions.mapping.TrafficLightRegistration;
import org.eclipse.mosaic.interactions.mapping.VehicleRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioTrafficLightRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioVehicleRegistration;
import org.eclipse.mosaic.interactions.traffic.InductionLoopDetectorSubscription;
import org.eclipse.mosaic.interactions.traffic.LaneAreaDetectorSubscription;
import org.eclipse.mosaic.interactions.traffic.LanePropertyChange;
import org.eclipse.mosaic.interactions.traffic.TrafficDetectorUpdates;
import org.eclipse.mosaic.interactions.traffic.TrafficLightStateChange;
import org.eclipse.mosaic.interactions.traffic.TrafficLightSubscription;
import org.eclipse.mosaic.interactions.traffic.TrafficLightUpdates;
import org.eclipse.mosaic.interactions.traffic.VehicleRoutesInitialization;
import org.eclipse.mosaic.interactions.traffic.VehicleTypesInitialization;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignLaneAssignmentChange;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignRegistration;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignSpeedLimitChange;
import org.eclipse.mosaic.interactions.trafficsigns.VehicleSeenTrafficSignsUpdate;
import org.eclipse.mosaic.interactions.vehicle.VehicleFederateAssignment;
import org.eclipse.mosaic.interactions.vehicle.VehicleLaneChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleParametersChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleResume;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteRegistration;
import org.eclipse.mosaic.interactions.vehicle.VehicleSensorActivation;
import org.eclipse.mosaic.interactions.vehicle.VehicleSightDistanceConfiguration;
import org.eclipse.mosaic.interactions.vehicle.VehicleSlowDown;
import org.eclipse.mosaic.interactions.vehicle.VehicleSpeedChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleStop;
import org.eclipse.mosaic.lib.util.InteractionUtils;

import com.google.common.collect.Lists;

public class Interactions {

    /**
     * Caches all known interaction classes in {@link InteractionUtils}
     * for quick access without the need to scan classpath for
     * {@link org.eclipse.mosaic.rti.api.Interaction} implementations.
     */
    public static void cacheDefaultInteractions() {
        for (Class<?> interactionClass: Lists.newArrayList(
                AdHocCommunicationConfiguration.class,
                AgentRegistration.class,
                AgentRemoval.class,
                AgentRouteChange.class,
                AgentUpdates.class,
                BatteryChargingStart.class,
                BatteryChargingStop.class,
                CellularCommunicationConfiguration.class,
                CellularHandoverUpdates.class,
                ChargingStationRegistration.class,
                ChargingStationUpdate.class,
                EnvironmentSensorActivation.class,
                EnvironmentSensorUpdates.class,
                GlobalEnvironmentUpdates.class,
                InductionLoopDetectorSubscription.class,
                ItefLogging.class,
                LaneAreaDetectorSubscription.class,
                LanePropertyChange.class,
                LidarUpdates.class,
                RsuRegistration.class,
                ScenarioTrafficLightRegistration.class,
                ScenarioVehicleRegistration.class,
                ServerRegistration.class,
                SumoTraciRequest.class,
                SumoTraciResponse.class,
                TmcRegistration.class,
                TrafficDetectorUpdates.class,
                TrafficLightRegistration.class,
                TrafficLightStateChange.class,
                TrafficLightSubscription.class,
                TrafficLightUpdates.class,
                TrafficSignLaneAssignmentChange.class,
                TrafficSignRegistration.class,
                TrafficSignSpeedLimitChange.class,
                V2xMessageAcknowledgement.class,
                V2xMessageReception.class,
                V2xMessageRemoval.class,
                V2xMessageTransmission.class,
                VehicleBatteryUpdates.class,
                VehicleChargingDenial.class,
                VehicleChargingStartRequest.class,
                VehicleChargingStopRequest.class,
                VehicleFederateAssignment.class,
                VehicleLaneChange.class,
                VehicleParametersChange.class,
                VehicleRegistration.class,
                VehicleResume.class,
                VehicleRouteChange.class,
                VehicleRouteRegistration.class,
                VehicleRoutesInitialization.class,
                VehicleSeenTrafficSignsUpdate.class,
                VehicleSensorActivation.class,
                VehicleSightDistanceConfiguration.class,
                VehicleSlowDown.class,
                VehicleSpeedChange.class,
                VehicleStop.class,
                VehicleTypesInitialization.class,
                VehicleUpdates.class
        )) {
            InteractionUtils.storeInteractionClass(interactionClass);
        }
    }

}
