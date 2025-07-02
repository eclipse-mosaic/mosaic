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
    private final AgentUpdates personUpdates;
    private final TrafficDetectorUpdates trafficDetectorUpdates;
    private final TrafficLightUpdates trafficLightUpdates;

    public TraciSimulationStepResult(
            VehicleUpdates vehicleUpdates,
            AgentUpdates personUpdates,
            TrafficDetectorUpdates trafficDetectorUpdates,
            TrafficLightUpdates trafficLightUpdates
    ) {
        this.vehicleUpdates = vehicleUpdates;
        this.personUpdates = personUpdates;
        this.trafficDetectorUpdates = trafficDetectorUpdates;
        this.trafficLightUpdates = trafficLightUpdates;
    }

    public VehicleUpdates getVehicleUpdates() {
        return vehicleUpdates;
    }

    public AgentUpdates getPersonUpdates() {
        return personUpdates;
    }

    public TrafficDetectorUpdates getTrafficDetectorUpdates() {
        return trafficDetectorUpdates;
    }

    public TrafficLightUpdates getTrafficLightUpdates() {
        return trafficLightUpdates;
    }
}
