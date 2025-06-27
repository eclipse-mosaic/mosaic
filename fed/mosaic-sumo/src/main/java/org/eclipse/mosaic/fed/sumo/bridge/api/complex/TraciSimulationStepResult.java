/*
 * Copyright (c) 2021 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.sumo.bridge.api.complex;

import org.eclipse.mosaic.interactions.agent.AgentUpdates;
import org.eclipse.mosaic.interactions.traffic.TrafficDetectorUpdates;
import org.eclipse.mosaic.interactions.traffic.TrafficLightUpdates;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;

public class TraciSimulationStepResult {

    private final VehicleUpdates vehicleUpdates;
    private final AgentUpdates pedestrianUpdates;
    private final TrafficDetectorUpdates trafficDetectorUpdates;
    private final TrafficLightUpdates trafficLightUpdates;

    public TraciSimulationStepResult(
            VehicleUpdates vehicleUpdates,
            AgentUpdates pedestrianUpdates,
            TrafficDetectorUpdates trafficDetectorUpdates,
            TrafficLightUpdates trafficLightUpdates
    ) {
        this.vehicleUpdates = vehicleUpdates;
        this.pedestrianUpdates = pedestrianUpdates;
        this.trafficDetectorUpdates = trafficDetectorUpdates;
        this.trafficLightUpdates = trafficLightUpdates;
    }

    public VehicleUpdates getVehicleUpdates() {
        return vehicleUpdates;
    }

    public AgentUpdates getPedestrianUpdates() {
        return pedestrianUpdates;
    }

    public TrafficDetectorUpdates getTrafficDetectorUpdates() {
        return trafficDetectorUpdates;
    }

    public TrafficLightUpdates getTrafficLightUpdates() {
        return trafficLightUpdates;
    }
}
