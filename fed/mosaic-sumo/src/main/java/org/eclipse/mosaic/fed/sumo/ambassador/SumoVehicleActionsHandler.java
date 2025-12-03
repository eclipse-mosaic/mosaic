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

import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_LANE_CHANGE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_PARAM_CHANGE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_RESUME_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_ROUTE_CHANGE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_SIGHT_DISTANCE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_SLOWDOWN_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_SPEED_CHANGE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_STOP_REQ;

import org.eclipse.mosaic.fed.sumo.bridge.api.complex.SumoLaneChangeMode;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.SumoSpeedMode;
import org.eclipse.mosaic.fed.sumo.config.CSumo;
import org.eclipse.mosaic.interactions.application.TaxiDispatch;
import org.eclipse.mosaic.interactions.application.SumoSurroundingObjectsSubscription;
import org.eclipse.mosaic.interactions.vehicle.VehicleLaneChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleParametersChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleResume;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleSensorActivation;
import org.eclipse.mosaic.interactions.vehicle.VehicleSlowDown;
import org.eclipse.mosaic.interactions.vehicle.VehicleSpeedChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleStop;
import org.eclipse.mosaic.lib.enums.VehicleStopMode;
import org.eclipse.mosaic.lib.objects.UnitNameGenerator;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleParameter;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.util.scheduling.EventProcessor;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.apache.commons.lang3.ArrayUtils;

import java.awt.Color;

/**
 * All the ambassador's logic which takes care of actions of vehicles, such as stopping or lane changing.
 */
public class SumoVehicleActionsHandler extends AbstractHandler implements EventProcessor {

    private final SumoVehiclesHandler vehiclesHandler;

    SumoVehicleActionsHandler(SumoVehiclesHandler vehiclesHandler) {
        this.vehiclesHandler = vehiclesHandler;
    }

    /**
     * Extract data from received {@link VehicleSlowDown} interaction and forward to SUMO.
     *
     * @param vehicleSlowDown interaction indicating that a vehicle has to slow down
     */
    synchronized void handleSlowDown(VehicleSlowDown vehicleSlowDown) throws InternalFederateException {
        if (vehiclesHandler.externalVehicles.containsKey(vehicleSlowDown.getVehicleId())) {
            return;
        }
        if (log.isInfoEnabled()) {
            log.info(
                    "{} at simulation time {}: vehicleId=\"{}\", targetSpeed={}m/s, interval={}ms",
                    VEHICLE_SLOWDOWN_REQ,
                    TIME.format(vehicleSlowDown.getTime()),
                    vehicleSlowDown.getVehicleId(),
                    vehicleSlowDown.getSpeed(),
                    vehicleSlowDown.getDuration()
            );
        }
        bridge.getVehicleControl()
                .slowDown(vehicleSlowDown.getVehicleId(), vehicleSlowDown.getSpeed(), vehicleSlowDown.getDuration());
    }

    /**
     * Extract data from received {@link VehicleStop} interaction and forward to SUMO.
     *
     * @param vehicleStop interaction indicating that a vehicle has to stop.
     */
    synchronized void handleStop(VehicleStop vehicleStop) {

        if (vehiclesHandler.externalVehicles.containsKey(vehicleStop.getVehicleId())) {
            return;
        }
        try {
            final IRoadPosition stopPos = vehicleStop.getStopPosition();
            if (log.isInfoEnabled()) {
                log.info(
                        "{} at simulation time {}: vehicleId=\"{}\", edgeId=\"{}\", position=\"{}\", laneIndex={}, duration={}, "
                                + "stopMode={}",
                        VEHICLE_STOP_REQ,
                        TIME.format(vehicleStop.getTime()),
                        vehicleStop.getVehicleId(),
                        stopPos.getConnectionId(),
                        stopPos.getOffset(),
                        stopPos.getLaneIndex(),
                        vehicleStop.getDuration(),
                        vehicleStop.getVehicleStopMode()
                );
            }
            if (vehicleStop.getVehicleStopMode() == VehicleStopMode.NOT_STOPPED) {
                log.warn("Stop mode {} is not supported", vehicleStop.getVehicleStopMode());
            }

            stopVehicleAt(vehicleStop.getVehicleId(), stopPos, vehicleStop.getVehicleStopMode(), vehicleStop.getDuration());
        } catch (InternalFederateException e) {
            log.warn("Vehicle {} could not be stopped", vehicleStop.getVehicleId());
        }
    }

    /**
     * Extract data from received {@link VehicleResume} interaction and forward to SUMO.
     *
     * @param vehicleResume interaction indicating that a stopped vehicle has to resume
     */
    synchronized void handleResume(VehicleResume vehicleResume) throws InternalFederateException {
        if (vehiclesHandler.externalVehicles.containsKey(vehicleResume.getVehicleId())) {
            return;
        }
        if (log.isInfoEnabled()) {
            log.info("{} at simulation time {}: " + "vehicleId=\"{}\"",
                    VEHICLE_RESUME_REQ, TIME.format(vehicleResume.getTime()), vehicleResume.getVehicleId());
        }

        bridge.getVehicleControl().resume(vehicleResume.getVehicleId());
    }

    /**
     * Extract data from received {@link VehicleSensorActivation} interaction and forward to SUMO.
     *
     * @param vehicleSensorActivation Interaction that indicating to enable of the distance sensors for vehicle.
     */
    void handleSensorActivation(VehicleSensorActivation vehicleSensorActivation) {
        log.info(
                "Enabling distance sensors for vehicle \"{}\" at simulation time {}",
                vehicleSensorActivation.getVehicleId(),
                TIME.format(vehicleSensorActivation.getTime())
        );

        if (ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), VehicleSensorActivation.SensorType.RADAR_LEFT)
                || ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), VehicleSensorActivation.SensorType.RADAR_RIGHT)) {
            log.warn("Left or right distance sensors for vehicles are not supported.");
            return;
        }

        if (ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), VehicleSensorActivation.SensorType.RADAR_FRONT)
                || ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), VehicleSensorActivation.SensorType.RADAR_REAR)) {
            if (!sumoConfig.subscriptions.contains(CSumo.SUBSCRIPTION_LEADER)) {
                log.warn("You tried to configure a front or rear sensor but no leader information is subscribed. "
                        + "Please add \"{}\" to the list of \"subscriptions\" in the sumo_config.json file.", CSumo.SUBSCRIPTION_LEADER);
                return;
            }

            bridge.getSimulationControl().configureDistanceSensors(
                    vehicleSensorActivation.getVehicleId(),
                    vehicleSensorActivation.getMaximumLookahead(),
                    ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), VehicleSensorActivation.SensorType.RADAR_FRONT),
                    ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), VehicleSensorActivation.SensorType.RADAR_REAR)
            );
        }
    }

    /**
     * Extract data from received {@link VehicleParametersChange} interaction and forward to SUMO.
     *
     * @param vehicleParametersChange Interaction that indicating to change of the vehicle parameters.
     * @throws InternalFederateException Exception is thrown if an error occurred while changing of the vehicle parameters.
     */
    void handleParametersChange(VehicleParametersChange vehicleParametersChange) throws InternalFederateException {
        if (vehiclesHandler.externalVehicles.containsKey(vehicleParametersChange.getVehicleId())) {
            vehiclesHandler.changeExternalParameters(vehicleParametersChange);
            return;
        }

        log.info("{} at simulation time {}", VEHICLE_PARAM_CHANGE_REQ, TIME.format(vehicleParametersChange.getTime()));

        final String veh_id = vehicleParametersChange.getVehicleId();
        for (final VehicleParameter param : vehicleParametersChange.getVehicleParameters()) {
            switch (param.getParameterType()) {
                case MAX_SPEED:
                    bridge.getVehicleControl().setMaxSpeed(veh_id, param.<Double>getValue());
                    break;
                case IMPERFECTION:
                    bridge.getVehicleControl().setImperfection(veh_id, param.<Double>getValue());
                    break;
                case MAX_ACCELERATION:
                    bridge.getVehicleControl().setMaxAcceleration(veh_id, param.<Double>getValue());
                    break;
                case MAX_DECELERATION:
                    bridge.getVehicleControl().setMaxDeceleration(veh_id, param.<Double>getValue());
                    break;
                case MIN_GAP:
                    bridge.getVehicleControl().setMinimumGap(veh_id, param.<Double>getValue());
                    break;
                case REACTION_TIME:
                    bridge.getVehicleControl().setReactionTime(veh_id, param.<Double>getValue() + sumoConfig.timeGapOffset);
                    break;
                case SPEED_FACTOR:
                    bridge.getVehicleControl().setSpeedFactor(veh_id, param.<Double>getValue());
                    break;
                case LANE_CHANGE_MODE:
                    bridge.getVehicleControl().setLaneChangeMode(veh_id, SumoLaneChangeMode.translateFromEnum(param.getValue()));
                    break;
                case SPEED_MODE:
                    bridge.getVehicleControl().setSpeedMode(veh_id, SumoSpeedMode.translateFromEnum(param.getValue()));
                    break;
                case COLOR:
                    final Color color = param.getValue();
                    bridge.getVehicleControl().setColor(veh_id, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                    break;
                default:
                    log.warn("Parameter type {} is not supported by SUMO Ambassador", param.getParameterType().name());
            }
        }
    }

    /**
     * Tries to stop the vehicle at the given edge and offset. However, if the offset is larger
     * than the edge's length, the stop command will fail. In such cases, the offset will decrease,
     * and the stop is requested again.
     */
    private void stopVehicleAt(final String vehicleId, final IRoadPosition stopPos, final VehicleStopMode stopMode, final long duration)
            throws InternalFederateException {
        double stopPosition = 0;
        if (stopMode != VehicleStopMode.PARK_IN_PARKING_AREA) {
            double lengthOfLane = bridge.getSimulationControl().getLengthOfLane(stopPos.getConnectionId() + "_" + stopPos.getLaneIndex());
            stopPosition = stopPos.getOffset() < 0 ? lengthOfLane + stopPos.getOffset() : stopPos.getOffset();
            stopPosition = Math.min(Math.max(0.1, stopPosition), lengthOfLane);
        }
        bridge.getVehicleControl().stop(vehicleId, stopPos.getConnectionId(), stopPosition, stopPos.getLaneIndex(), duration, stopMode);
    }

    /**
     * Extracts data from received {@link VehicleSpeedChange} interaction and forwards it to SUMO.<br>
     * If an interval is set in VehicleSpeedChange, at first a slowDown will be initiated
     * via TraCI. After the interval has passed, the change speed command
     * is executed via TraCI. If no interval is set, the speed change is
     * initiated immediately.
     *
     * @param vehicleSpeedChange interaction indicating that a vehicle has to change its speed.
     */
    synchronized void handleSpeedChange(VehicleSpeedChange vehicleSpeedChange) throws InternalFederateException {
        if (vehiclesHandler.externalVehicles.containsKey(vehicleSpeedChange.getVehicleId())) {
            return;
        }
        if (log.isInfoEnabled()) {
            log.info(
                    "{} at simulation time {}: " + "vehicleId=\"{}\", targetSpeed={}m/s, interval={}ms",
                    VEHICLE_SPEED_CHANGE_REQ, TIME.format(vehicleSpeedChange.getTime()), vehicleSpeedChange.getVehicleId(),
                    vehicleSpeedChange.getSpeed(), vehicleSpeedChange.getDuration()
            );
        }
        switch (vehicleSpeedChange.getType()) {
            case RESET:
                // reset speed to car-following rules
                bridge.getVehicleControl().setSpeed(vehicleSpeedChange.getVehicleId(), -1.0);
                break;
            case WITH_DURATION:
                if (vehicleSpeedChange.getDuration() > 0) {
                    // set speed smoothly with given interval
                    final long changeSpeedTimeStep = vehicleSpeedChange.getTime() + vehicleSpeedChange.getDuration();
                    log.debug("slow down vehicle {} and schedule change speed event for time step {} ns ",
                            vehicleSpeedChange.getVehicleId(), changeSpeedTimeStep);
                    bridge.getVehicleControl()
                            .slowDown(vehicleSpeedChange.getVehicleId(), vehicleSpeedChange.getSpeed(), vehicleSpeedChange.getDuration());

                    // set speed permanently after given interval (in the future) via the event scheduler
                    long adjustedTime = adjustToSumoTimeStep(changeSpeedTimeStep, sumoConfig.updateInterval * TIME.MILLI_SECOND);
                    eventScheduler.addEvent(new Event(adjustedTime, this, vehicleSpeedChange)
                    );
                } else {
                    // set speed immediately
                    bridge.getVehicleControl().setSpeed(vehicleSpeedChange.getVehicleId(), vehicleSpeedChange.getSpeed());
                }
                break;
            case WITH_FORCED_ACCELERATION:
                log.warn("ChangeSpeed with forced acceleration is not supported yet.");
                break;
            case WITH_PLEASANT_ACCELERATION:
                log.warn("ChangeSpeed with pleasant acceleration is not supported yet.");
                break;
            default:
                // unknown type
                log.warn("Unsupported VehicleSpeedChangeType: {}", vehicleSpeedChange.getType());
        }
    }

    synchronized void handleSurroundingVehiclesSubscription(SumoSurroundingObjectsSubscription subscription) throws InternalFederateException {
        if (!UnitNameGenerator.isVehicle(subscription.getUnitId())) {
            log.error("Currently supports object subscriptions for vehicles only. Ignoring.");
            return;
        }

        log.info("{} at simulation time {}: unitId=\"{}\", range={}, fieldOfView={}",
                VEHICLE_SIGHT_DISTANCE_REQ,
                TIME.format(subscription.getTime()),
                subscription.getUnitId(),
                subscription.getRange(),
                subscription.getFieldOfView()
        );

        bridge.getSimulationControl().subscribeForVehiclesWithinFieldOfVision(
                subscription.getUnitId(),
                subscription.getTime(), ambassador.getEndTime(),
                subscription.getRange(),
                subscription.getFieldOfView()
        );
    }

    /**
     * Extract data from received {@link VehicleRouteChange} interaction and forward to SUMO.
     *
     * @param vehicleRouteChange interaction indicating that a vehicle has to change its route
     */
    synchronized void handleRouteChange(VehicleRouteChange vehicleRouteChange) throws InternalFederateException {
        if (log.isInfoEnabled()) {
            VehicleData lastKnownVehicleData = bridge.getSimulationControl().getLastKnownVehicleData(vehicleRouteChange.getVehicleId());
            log.info(
                    "{} at simulation time {}: vehicleId=\"{}\", newRouteId={}, current edge: {}",
                    VEHICLE_ROUTE_CHANGE_REQ, TIME.format(vehicleRouteChange.getTime()),
                    vehicleRouteChange.getVehicleId(), vehicleRouteChange.getRouteId(),
                    lastKnownVehicleData != null ? lastKnownVehicleData.getRoadPosition().getConnectionId() : null
            );
        }

        bridge.getVehicleControl().setRouteById(vehicleRouteChange.getVehicleId(), vehicleRouteChange.getRouteId());

        if (sumoConfig.highlights.contains(CSumo.HIGHLIGHT_CHANGE_ROUTE)) {
            bridge.getVehicleControl().highlight(vehicleRouteChange.getVehicleId(), Color.BLUE);
        }
    }

    /**
     * Extract data from received {@link VehicleLaneChange} interaction and forward to SUMO.
     *
     * @param vehicleLaneChange interaction indicating that a vehicle has to change its lane
     * @throws InternalFederateException Exception is thrown if an error occurred while converting to number.
     */
    synchronized void handleLaneChange(VehicleLaneChange vehicleLaneChange) throws InternalFederateException {
        if (vehiclesHandler.externalVehicles.containsKey(vehicleLaneChange.getVehicleId())) {
            return;
        }
        try {
            VehicleLaneChange.VehicleLaneChangeMode mode = vehicleLaneChange.getVehicleLaneChangeMode();

            if (log.isInfoEnabled()) {
                log.info("{} at simulation time {}: vehicleId=\"{}\", mode={}, lane={}",
                        VEHICLE_LANE_CHANGE_REQ,
                        TIME.format(vehicleLaneChange.getTime()),
                        vehicleLaneChange.getVehicleId(),
                        mode + (mode == VehicleLaneChange.VehicleLaneChangeMode.BY_INDEX ? "(" + vehicleLaneChange.getTargetLaneIndex() + ")" : ""),
                        vehicleLaneChange.getCurrentLaneId()
                );
            }

            int targetLaneId;
            int laneId;

            switch (mode) {
                case BY_INDEX:
                    targetLaneId = vehicleLaneChange.getTargetLaneIndex();
                    break;
                case TO_LEFT:
                    laneId = vehicleLaneChange.getCurrentLaneId();
                    targetLaneId = laneId + 1;
                    break;
                case TO_RIGHT:
                    laneId = vehicleLaneChange.getCurrentLaneId();
                    targetLaneId = laneId - 1;
                    break;
                case TO_RIGHTMOST:
                    targetLaneId = 0;
                    break;
                case STAY:
                    log.info("This lane is in use already - change lane will not be performed ");
                    return;
                default:
                    log.warn("VehicleLaneChange failed: unsupported lane change mode.");
                    return;
            }
            bridge.getVehicleControl()
                    .changeLane(vehicleLaneChange.getVehicleId(), Math.max(0, targetLaneId), vehicleLaneChange.getDuration());

            if (sumoConfig.highlights.contains(CSumo.HIGHLIGHT_CHANGE_LANE)) {
                VehicleData vehicleData = bridge.getSimulationControl().getLastKnownVehicleData(vehicleLaneChange.getVehicleId());
                if (vehicleData.getRoadPosition().getLaneIndex() != targetLaneId) {
                    bridge.getVehicleControl().highlight(vehicleLaneChange.getVehicleId(), Color.RED);
                }
            }

        } catch (NumberFormatException e) {
            throw new InternalFederateException(e);
        }
    }

	void handleTaxiDispatch(TaxiDispatch taxiDispatch) throws InternalFederateException {
		bridge.getVehicleControl().dispatchTaxi(taxiDispatch.getTaxiId(), taxiDispatch.getReservations());
	}

    @Override
    public void processEvent(Event event) throws Exception {
        if (event.getResource() instanceof VehicleSpeedChange speedChange) {
            log.debug("Change the speed of vehicle {} at {} ns ", speedChange.getVehicleId(), event.getTime());
            bridge.getVehicleControl().setSpeed(speedChange.getVehicleId(), speedChange.getSpeed());
        }
    }


    /**
     * Adjusts the given value to a multiple of the configured sumo time step
     * in order to avoid bugs related to sumo timing.
     *
     * @param changeSpeedStep Requested time for change speed in nanoseconds
     * @param sumoIntervalNs  Configured sumo interval in nanoseconds
     * @return The adjusted value which is a multiple of sumo timestep
     */
    static long adjustToSumoTimeStep(long changeSpeedStep, long sumoIntervalNs) {
        final long mod = changeSpeedStep % sumoIntervalNs;
        final long adjustedTimeStep;

        if (mod <= sumoIntervalNs / 2) {
            adjustedTimeStep = changeSpeedStep - mod;
        } else {
            adjustedTimeStep = changeSpeedStep + (sumoIntervalNs - mod);
        }
        return Math.max(adjustedTimeStep, sumoIntervalNs);
    }
}
