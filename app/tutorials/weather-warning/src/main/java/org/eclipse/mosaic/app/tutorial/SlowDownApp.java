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

package org.eclipse.mosaic.app.tutorial;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.enums.TractionHazard;
import org.eclipse.mosaic.lib.objects.environment.Sensor;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This application shall induce vehicles to slow down in hazardous environments.
 * In onVehicleUpdated() the application requests new data from the vehicle's
 * traction hazard sensor and analyzes the result with every movement update.
 * Once the sensor indicates that a certain vehicle has entered a potentially
 * hazardous area, the application will reduce the speed of the respective vehicle
 * within a specified time frame. After the respective vehicle has left the dangerous
 * zone, its speed will no longer be reduced.
 */
public class SlowDownApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication {

    private final static float SPEED = 25 / 3.6f;

    private boolean hazardousArea = false;

    @Override
    public void onStartup() {
        getOs().getBasicSensorModule().enable();
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {

        /*
         * The current strength of the traction hazard sensor is examined here.
         * If a traction hazard event is detected, we initiate a slowdown of the vehicle.
         */
        TractionHazard tractionHazard = getOs().getBasicSensorModule().getSensorValue(Sensor.TRACTION_HAZARD).orElse(null);

        if (tractionHazard != null && !hazardousArea) {
            // Reduce speed when entering potentially hazardous area
            getOs().changeSpeedWithInterval(SPEED, 5 * TIME.SECOND);
            hazardousArea = true;
        }

        if (tractionHazard == null && hazardousArea) {
            // Reset speed when leaving potentially hazardous area
            getOs().resetSpeed();
            hazardousArea = false;
        }
    }

    @Override
    public void processEvent(Event event) throws Exception {

    }

    @Override
    public void onShutdown() {

    }

}
