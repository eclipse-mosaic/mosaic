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

package org.eclipse.mosaic.app.examples.emergencybrake;

import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.EventCause;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.environment.Sensor;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Denm;
import org.eclipse.mosaic.lib.objects.v2x.etsi.DenmContent;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class implements an application for vehicles.
 * In case the vehicle sensors detect an obstacle the vehicle will perform an emergency brake.
 * If the emergency brake endures a specified minimum time duration a DENMessage is sent out.
 */
public class EmergencyBrakeApp extends ConfigurableApplication<CEmergencyBrakeApp, VehicleOperatingSystem> implements VehicleApplication {

    // Keep track of the vehicle movement for the emergency brake detection
    private float lastSpeed = 0;
    private float lastTime = 0;
    private long startedBrakingAt = Long.MIN_VALUE;

    // Keep status of the emergency brake performed on obstacle
    private boolean emergencyBrake = false;
    private long stoppedAt = Long.MIN_VALUE;

    /**
     * Initializes an instance of the {@link EmergencyBrakeApp}.
     */
    public EmergencyBrakeApp() { super(CEmergencyBrakeApp.class); }

    @Override
    public void onStartup() {
        getOs().getAdHocModule().enable();
        getOs().getBasicSensorModule().enable();
    }

    /**
     * Checks for an obstacle detected by the vehicle sensors.
     * If an obstacle is detected an emergency brake will be performed.
     *
     * @param previousVehicleData the previous state of the vehicle
     * @param updatedVehicleData  the updated state of the vehicle
     */
    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {

        EventCause eventCause = getOs().getBasicSensorModule().getSensorValue(Sensor.EVENT).orElse(null);

        // Initiate emergency brake if obstacle is detected
        if (eventCause == EventCause.OBSTACLE_ON_ROAD && !emergencyBrake) {
            stoppedAt = getOs().getSimulationTime();
            getOs().changeSpeedWithForcedAcceleration(getConfiguration().targetSpeed, getConfiguration().deceleration);
            emergencyBrake = true;
            getLog().infoSimTime(this, "Performing emergency brake caused by detected obstacle");
        }

        // Continue driving normal as soon emergency brake is done and no obstacle is detectable
        if (emergencyBrake && eventCause != EventCause.OBSTACLE_ON_ROAD && idlePeriodOver(stoppedAt) && reachedSpeed(getConfiguration().targetSpeed)) {
            getOs().resetSpeed();
            stoppedAt = Long.MIN_VALUE;
            emergencyBrake = false;
            getLog().infoSimTime(this, "Passed obstacle");
        }

        // Call emergency brake detection
        detectEmergencyBrake();

    }

    private boolean idlePeriodOver(long stoppedAt) {
        return getOs().getSimulationTime() > stoppedAt + getConfiguration().idlePeriod;
    }

    private boolean reachedSpeed(double speed) {
        return Math.abs(getOs().getVehicleData().getSpeed() - speed) < 0.1d;
    }

    /**
     * Evaluates the vehicle's speed and acceleration data to decide whether an emergency break is happening.
     * In case of an emergency break a DENMessage is sent out.
     */
    private void detectEmergencyBrake() {

        float curSpeed = (float) getOs().getNavigationModule().getVehicleData().getSpeed();

        // Calculate brake deceleration
        float timeDifference = ((getOs().getSimulationTime() - lastTime) / TIME.SECOND);
        float speedDifference = curSpeed - lastSpeed;
        float curDeceleration = (speedDifference / timeDifference) / 9.81f;

        // If brake deceleration is low enough to qualify for an emergency brake, then keep track of the brake duration
        if (curDeceleration < -(getConfiguration().emergencyBrakeThresh)) {
            if (startedBrakingAt < 0) {
                startedBrakingAt = getOs().getSimulationTime();
            }
        } else {
            startedBrakingAt = Long.MIN_VALUE;
        }

        long actualBrakeDuration = Long.MIN_VALUE;
        if (startedBrakingAt > 0) {
            actualBrakeDuration = getOs().getSimulationTime() - startedBrakingAt;
        }

        // If brake duration is high enough, then send a DENMessage
        if (actualBrakeDuration >= getConfiguration().minimalBrakeDuration) {
            getLog().info("Detected emergency brake in progress");

            // Vehicle info
            GeoPoint vehicleLongLat = getOs().getPosition();
            String roadId = getOs().getNavigationModule().getRoadPosition().getConnection().getId();

            // Prepare the DENMessage
            MessageRouting routing =
                    getOs().getAdHocModule().createMessageRouting()
                            .channel(AdHocChannel.CCH)
                            .singlehop()
                            .broadcast()
                            .topological()
                            .build();
            Denm denm = new Denm(routing, new DenmContent(getOs().getSimulationTime(), vehicleLongLat, roadId,
                    EventCause.DANGEROUS_SITUATION , curSpeed, curDeceleration * 9.81f, null,
                    null, null), 200);
            getLog().debug("Sender position: " + denm.getSenderPosition());
            getLog().debug("Sender speed at event time: " + denm.getCausedSpeed() + "m/s");
            getLog().debug("Sender deceleration at event time: " + denm.getSenderDeceleration() + "m/s2");
            getLog().debug("RoadId on which the event take place: " + denm.getEventRoadId());
            getLog().debug("DENMessage successfully filled");

            // Send the DENMessage
            getOs().getAdHocModule().sendV2xMessage(denm);
            getLog().info("Sent DENMessage");
        }

        // Update time and speed buffer for calculation of deceleration
        lastTime = getOs().getSimulationTime();
        lastSpeed = curSpeed;

        getLog().debug("t=" + getOs().getSimulationTime()
                + "\t SpeedDifference: " + speedDifference
                + "\t timeDifference: " + timeDifference
                + "\t curDeceleration: " + curDeceleration);
    }

    @Override
    public void onShutdown() {

    }

    @Override
    public void processEvent(Event event) throws Exception {

    }
}
