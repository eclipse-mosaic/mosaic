/*
 * Copyright (c) 2022 Fraunhofer FOKUS and others. All rights reserved.
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


package org.eclipse.mosaic.test.app.perceptionmodule;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.perception.PerceptionConfiguration;
import org.eclipse.mosaic.lib.perception.objects.TrafficLightObject;
import org.eclipse.mosaic.lib.perception.objects.VehicleObject;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SimplePerceptionApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication {

    private static final String EVENT_RESOURCE = "PERCEPTION";
    private static final long queryInterval = 2 * TIME.SECOND;

    private static final double VIEWING_ANGLE = 160d;
    private static final double VIEWING_RANGE = 100d;

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Started {} on {}.", this.getClass().getSimpleName(), getOs().getId());

        enablePerceptionModule();
        schedulePerception();
    }

    private void enablePerceptionModule() {
        PerceptionConfiguration perceptionModuleConfiguration =
                new PerceptionConfiguration.Builder(VIEWING_ANGLE, VIEWING_RANGE).build();
        getOs().getPerceptionModule().enable(perceptionModuleConfiguration);
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Traffic Light switched {} times.", greenCount);
    }

    @Override
    public void processEvent(Event event) throws Exception {
        if (event.getResource() != null && event.getResource() instanceof String) {
            if (event.getResource().equals(EVENT_RESOURCE)) {
                perceiveVehicles();
                perceiveTrafficLights();
                schedulePerception();
            }
        }
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {

    }

    private void schedulePerception() {
        getOs().getEventManager().newEvent(getOs().getSimulationTime() + queryInterval, this)
                .withResource(EVENT_RESOURCE)
                .schedule();
    }

    private boolean perceivedNoVehicles = true;

    private void perceiveVehicles() {
        List<VehicleObject> perceivedVehicles = getOs().getPerceptionModule().getPerceivedVehicles();
        if (perceivedVehicles.isEmpty()) {
            perceivedNoVehicles = true;
        }
        if (perceivedNoVehicles && perceivedVehicles.size() == 4) {
            long vehiclesWithInvalidDimensions = perceivedVehicles.stream()
                    .filter(v -> v.getLength() == 0 && v.getWidth() == 0 && v.getHeight() == 0).count();
            getLog().infoSimTime(this, "Perceived all vehicles: {}, {} without dimensions.",
                    perceivedVehicles.stream().map(VehicleObject::getId).toList(), vehiclesWithInvalidDimensions);
            perceivedNoVehicles = false;
        }
    }

    private boolean isGreen = false;
    private int greenCount = 0;

    private void perceiveTrafficLights() {
        // get all traffic lights in range
        List<TrafficLightObject> perceivedTrafficLights = getOs().getPerceptionModule().getPerceivedTrafficLights();
        // get traffic light controlling lane of ego vehicle
        List<TrafficLightObject> trafficLightsOnLane = perceivedTrafficLights.stream()
                .filter(trafficLightObject -> trafficLightObject.getIncomingLane().equals(getLaneId())).toList();
        if (trafficLightsOnLane.size() != 1) {
            return;
        }
        TrafficLightObject trafficLightOnLane = trafficLightsOnLane.get(0);
        if (trafficLightOnLane.getTrafficLightState().isGreen() && !isGreen) { // check if traffic light changed to green
            greenCount++;
            isGreen = true;
        }
        if (trafficLightOnLane.getTrafficLightState().isRed() && isGreen) {
            isGreen = false;
        }
    }

    private String getLaneId() {
        return getOs().getRoadPosition().getConnectionId() + "_" + getOs().getRoadPosition().getLaneIndex();
    }
}
