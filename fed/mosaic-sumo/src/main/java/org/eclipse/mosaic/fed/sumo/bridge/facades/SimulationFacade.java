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

package org.eclipse.mosaic.fed.sumo.bridge.facades;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.fed.sumo.bridge.SumoVersion;
import org.eclipse.mosaic.fed.sumo.bridge.api.InductionLoopSubscribe;
import org.eclipse.mosaic.fed.sumo.bridge.api.LaneAreaSubscribe;
import org.eclipse.mosaic.fed.sumo.bridge.api.LaneGetLength;
import org.eclipse.mosaic.fed.sumo.bridge.api.LaneGetShape;
import org.eclipse.mosaic.fed.sumo.bridge.api.LaneSetAllow;
import org.eclipse.mosaic.fed.sumo.bridge.api.LaneSetDisallow;
import org.eclipse.mosaic.fed.sumo.bridge.api.LaneSetMaxSpeed;
import org.eclipse.mosaic.fed.sumo.bridge.api.PersonSubscribe;
import org.eclipse.mosaic.fed.sumo.bridge.api.SimulationGetArrivedPersonIds;
import org.eclipse.mosaic.fed.sumo.bridge.api.SimulationGetDepartedPersonIds;
import org.eclipse.mosaic.fed.sumo.bridge.api.SimulationGetDepartedVehicleIds;
import org.eclipse.mosaic.fed.sumo.bridge.api.SimulationGetTrafficLightIds;
import org.eclipse.mosaic.fed.sumo.bridge.api.SimulationSetOrder;
import org.eclipse.mosaic.fed.sumo.bridge.api.SimulationSimulateStep;
import org.eclipse.mosaic.fed.sumo.bridge.api.TrafficLightSubscribe;
import org.eclipse.mosaic.fed.sumo.bridge.api.VehicleAdd;
import org.eclipse.mosaic.fed.sumo.bridge.api.VehicleGetTeleportingList;
import org.eclipse.mosaic.fed.sumo.bridge.api.VehicleSetRemove;
import org.eclipse.mosaic.fed.sumo.bridge.api.VehicleSetUpdateBestLanes;
import org.eclipse.mosaic.fed.sumo.bridge.api.VehicleSubscribe;
import org.eclipse.mosaic.fed.sumo.bridge.api.VehicleSubscribeSurroundingVehicle;
import org.eclipse.mosaic.fed.sumo.bridge.api.VehicleSubscriptionSetFieldOfVision;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.AbstractSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.InductionLoopSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.LaneAreaSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.LeadFollowVehicle;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.PersonSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.TraciSimulationStepResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.TrafficLightSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.VehicleContextSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.VehicleSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.config.CSumo;
import org.eclipse.mosaic.fed.sumo.util.InductionLoop;
import org.eclipse.mosaic.fed.sumo.util.TrafficLightStateDecoder;
import org.eclipse.mosaic.interactions.agent.AgentUpdates;
import org.eclipse.mosaic.interactions.traffic.TrafficDetectorUpdates;
import org.eclipse.mosaic.interactions.traffic.TrafficLightUpdates;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.lib.enums.DriveDirection;
import org.eclipse.mosaic.lib.enums.VehicleStopMode;
import org.eclipse.mosaic.lib.objects.agent.AgentData;
import org.eclipse.mosaic.lib.objects.pt.PtVehicleData;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.objects.road.SimpleRoadPosition;
import org.eclipse.mosaic.lib.objects.traffic.InductionLoopInfo;
import org.eclipse.mosaic.lib.objects.traffic.LaneAreaDetectorInfo;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroupInfo;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;
import org.eclipse.mosaic.lib.objects.vehicle.Consumptions;
import org.eclipse.mosaic.lib.objects.vehicle.Emissions;
import org.eclipse.mosaic.lib.objects.vehicle.SurroundingVehicle;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleConsumptions;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleEmissions;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleSensors;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleSignals;
import org.eclipse.mosaic.lib.objects.vehicle.sensor.DistanceSensor;
import org.eclipse.mosaic.lib.objects.vehicle.sensor.RadarSensor;
import org.eclipse.mosaic.lib.util.objects.Position;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SimulationFacade {

    /**
     * Density of vehicle gasoline in g/m^3. Since 1.14.0 SUMO returns
     * fuel consumptions in mg, thus we convert it back to ml for compatibility.
     */
    private final static double FUEL_DENSITY = 0.74; // g/m^3

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final Bridge bridge;
    private final CSumo sumoConfiguration;

    private final SimulationSimulateStep simulateStep;
    private final SimulationGetDepartedVehicleIds getDepartedVehicleIds;
    private final SimulationGetDepartedPersonIds getDepartedPersonIds;
    private final SimulationGetArrivedPersonIds getArrivedPersonIds;
    private final SimulationGetTrafficLightIds getTrafficLightIds;
    private final VehicleAdd vehicleAdd;
    private final VehicleSetRemove remove;
    private final VehicleGetTeleportingList getTeleportingList;

    private final VehicleSubscribe vehicleSubscribe;
    private final VehicleSubscribeSurroundingVehicle vehicleSubscribeSurrounding;
    private final VehicleSubscriptionSetFieldOfVision vehicleSubscriptionFilterFieldOfVision;
    private final PersonSubscribe personSubscribe;
    private final InductionLoopSubscribe inductionloopSubscribe;
    private final LaneAreaSubscribe laneAreaSubscribe;
    private final TrafficLightSubscribe trafficLightSubscribe;

    private final LaneSetAllow laneSetAllow;
    private final LaneSetDisallow laneSetDisallow;
    private final LaneSetMaxSpeed laneSetMaxSpeed;

    private final LaneGetLength laneGetLength;
    private final LaneGetShape laneGetShape;

    private final Map<String, InductionLoop> inductionLoops = new HashMap<>();
    private final Map<String, SumoVehicleState> sumoVehicles = new HashMap<>();

    /**
     * This list is used to cache teleporting vehicles. It is only filled if at least one vehicle is potentially teleporting
     * and reset to {@code null} after each time step.
     */
    private List<String> currentTeleportingList;

    private static class SumoVehicleState {
        private final String id;
        private VehicleData currentVehicleData = null;
        private VehicleData lastVehicleData = null;
        private Double frontSensorDistance = null;
        private Double rearSensorDistance = null;

        private SumoVehicleState(String id) {
            this.id = id;
        }

        private boolean isAdded() {
            return lastVehicleData == null && currentVehicleData != null;
        }

        private boolean isUpdated() {
            return lastVehicleData != currentVehicleData && currentVehicleData != null;
        }

        private boolean isNotUpdated() {
            return lastVehicleData != null && currentVehicleData == lastVehicleData;
        }
    }


    /**
     * Whenever lane assignments are changed, the vehicles need to recalculate
     * their "best lanes" for strategic lane-change decisions. If this variable
     * is set to true, the "best lanes" are recalculated for each vehicle right
     * before the next simulation step.
     */
    private boolean updateBestLanesBeforeNextSimulationStep = false;

    /**
     * Creates a new {@link SimulationFacade} object.
     *
     * @param bridge            Connection to Traci.
     * @param sumoConfiguration The SUMO configuration file.
     */
    public SimulationFacade(final Bridge bridge, final CSumo sumoConfiguration) {
        this.bridge = bridge;
        this.sumoConfiguration = sumoConfiguration;

        try {
            this.simulateStep = bridge.getCommandRegister().getOrCreate(SimulationSimulateStep.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.getDepartedVehicleIds = bridge.getCommandRegister().getOrCreate(SimulationGetDepartedVehicleIds.class);
        this.getDepartedPersonIds = bridge.getCommandRegister().getOrCreate(SimulationGetDepartedPersonIds.class);
        this.getArrivedPersonIds = bridge.getCommandRegister().getOrCreate(SimulationGetArrivedPersonIds.class);
        this.getTrafficLightIds = bridge.getCommandRegister().getOrCreate(SimulationGetTrafficLightIds.class);
        this.vehicleAdd = bridge.getCommandRegister().getOrCreate(VehicleAdd.class);
        this.remove = bridge.getCommandRegister().getOrCreate(VehicleSetRemove.class);
        this.getTeleportingList = bridge.getCommandRegister().getOrCreate(VehicleGetTeleportingList.class);

        this.inductionloopSubscribe = bridge.getCommandRegister().getOrCreate(InductionLoopSubscribe.class);
        this.laneAreaSubscribe = bridge.getCommandRegister().getOrCreate(LaneAreaSubscribe.class);
        this.vehicleSubscribe = bridge.getCommandRegister().getOrCreate(VehicleSubscribe.class);
        this.personSubscribe = bridge.getCommandRegister().getOrCreate(PersonSubscribe.class);
        this.trafficLightSubscribe = bridge.getCommandRegister().getOrCreate(TrafficLightSubscribe.class);

        this.laneSetAllow = bridge.getCommandRegister().getOrCreate(LaneSetAllow.class);
        this.laneSetDisallow = bridge.getCommandRegister().getOrCreate(LaneSetDisallow.class);
        this.laneSetMaxSpeed = bridge.getCommandRegister().getOrCreate(LaneSetMaxSpeed.class);

        this.laneGetLength = bridge.getCommandRegister().getOrCreate(LaneGetLength.class);
        this.laneGetShape = bridge.getCommandRegister().getOrCreate(LaneGetShape.class);

        this.vehicleSubscribeSurrounding = bridge.getCommandRegister().getOrCreate(VehicleSubscribeSurroundingVehicle.class);
        this.vehicleSubscriptionFilterFieldOfVision = bridge.getCommandRegister().getOrCreate(VehicleSubscriptionSetFieldOfVision.class);
    }

    /**
     * Returns a list of all vehicle ids which departed in the previous time step.
     *
     * @return a list of vehicle ids.
     * @throws InternalFederateException if departed vehicles couldn't be retrieved
     */
    public final List<String> getDepartedVehicles() throws InternalFederateException {
        try {
            return getDepartedVehicleIds.execute(bridge);
        } catch (CommandException e) {
            throw new InternalFederateException("Could not retrieve departed vehicles", e);
        }
    }

    /**
     * Returns a list of all person ids which departed in the previous time step.
     *
     * @return a list of person ids.
     * @throws InternalFederateException if departed person couldn't be retrieved
     */
    public final List<String> getDepartedPersons() throws InternalFederateException {
        try {
            return getDepartedPersonIds.execute(bridge);
        } catch (CommandException e) {
            throw new InternalFederateException("Could not retrieve departed persons", e);
        }
    }

    /**
     * Returns a list of all person ids which arrived in the previous time step.
     *
     * @return a list of person ids.
     * @throws InternalFederateException if arrived person couldn't be retrieved
     */
    public final List<String> getArrivedPersons() throws InternalFederateException {
        try {
            return getArrivedPersonIds.execute(bridge);
        } catch (CommandException e) {
            throw new InternalFederateException("Could not retrieve arrived persons", e);
        }
    }

    /**
     * Returns a list of ids of all traffic light groups existing in the simulation scenario.
     *
     * @return a list of ids of all traffic light groups.
     * @throws InternalFederateException if traffic light groups couldn't be read
     */
    public List<String> getTrafficLightGroupIds() throws InternalFederateException {
        try {
            return getTrafficLightIds.execute(bridge);
        } catch (CommandException e) {
            throw new InternalFederateException("Could not read traffic light groups");
        }
    }

    /**
     * Adds an vehicle to the simulation.
     *
     * @param vehicleId   the id of the vehicle. Must not be assigned to another vehicle
     * @param vehicleType the vehicle type. Must be existing
     * @param routeId     the id of the route for the vehicle. Must be existing
     * @param laneId      the lane the vehicle should be inserted on
     * @param departPos   the position along the starting edge the vehicle should be inserted on
     * @param departSpeed the speed at which the vehicle should depart.
     * @throws InternalFederateException if the vehicle couldn't be added
     */
    public void addVehicle(String vehicleId, String routeId, String vehicleType,
                           String laneId, String departPos, String departSpeed) throws InternalFederateException {
        try {
            vehicleAdd.execute(bridge, vehicleId, routeId, vehicleType, laneId, departPos, departSpeed);
        } catch (CommandException e) {
            throw new InternalFederateException(String.format("Could not add vehicle %s", vehicleId), e);
        }
    }

    /**
     * Subscribes for the given vehicle. It will then be included in the VehicleUpdates result of {@link #simulateStep}.
     *
     * @param vehicleId the id of the vehicle. Must be known to the simulation
     * @param start     the time [ns] the subscription should start
     * @param end       the time [ns] the subscription should end
     * @throws InternalFederateException if it wasn't possible to subscribe for the wanted vehicle
     */
    public void subscribeForVehicle(String vehicleId, long start, long end) throws InternalFederateException {
        try {
            vehicleSubscribe.execute(bridge, vehicleId, start, end);
        } catch (CommandException e) {
            throw new InternalFederateException(String.format("Could not subscribe for vehicle %s", vehicleId), e);
        }
    }

    /**
     * Creates a contextual subscription which returns all vehicles surrounding the specified vehicle filtered by a field of vision
     * (sight distance and opening angle).
     *
     * @param vehicleId    the id of the vehicle to subscribe the data of surrounding vehicles
     * @param start        the time [ns] the subscription should start
     * @param end          the time [ns] the subscription should end
     * @param range        the range of the field of vision [m]
     * @param openingAngle the opening angle of the field of vision [deg]
     * @throws InternalFederateException if it was not possible to create a context subscription
     */
    public void subscribeForVehiclesWithinFieldOfVision(String vehicleId, long start, long end, double range, double openingAngle) throws InternalFederateException {
        try {
            vehicleSubscribeSurrounding.execute(bridge, vehicleId, start, end, range);
            vehicleSubscriptionFilterFieldOfVision.execute(bridge, openingAngle);
        } catch (CommandException e) {
            throw new InternalFederateException(String.format("Could not subscribe for vehicle %s", vehicleId), e);
        }
    }

    /**
     * Subscribes for the given person. It will then be included in the AgentUpdates result of {@link #simulateStep}.
     *
     * @param personId the id of the person. Must be known to the simulation
     * @param start    the time [ns] the subscription should start
     * @param end      the time [ns] the subscription should end
     * @throws InternalFederateException if it wasn't possible to subscribe for the wanted person
     */
    public void subscribeForPerson(String personId, long start, long end) throws InternalFederateException {
        try {
            personSubscribe.execute(bridge, personId, start, end);
        } catch (CommandException e) {
            throw new InternalFederateException(String.format("Could not subscribe for person %s", personId), e);
        }
    }

    /**
     * Subscribes to the given induction loop. It will then be included in the InductionLoopData result of {@link #simulateStep}.
     *
     * @param inductionLoopId the id of the induction loop. Must be known to the simulation
     * @param start           the time [ns] the subscription should start
     * @param end             the time [ns] the subscription should end
     * @throws InternalFederateException if it wasn't possible to subscribe for the wanted induction loop
     */
    public final void subscribeForInductionLoop(String inductionLoopId, long start, long end) throws InternalFederateException {
        try {
            inductionloopSubscribe.execute(bridge, inductionLoopId, start, end);
        } catch (CommandException e) {
            throw new InternalFederateException(String.format("Could not subscribe for induction loop %s", inductionLoopId), e);
        }
    }

    /**
     * Subscribes to the given lane area. It will then be included in the LANEAREAData result of {@link #simulateStep}.
     *
     * @param laneAreaId the laneId of the lane area. Must be known to the simulation
     * @param start      the time [ns] the subscription should start
     * @param end        the time [ns] the subscription should end
     * @throws InternalFederateException if it wasn't possible to subscribe for the wanted lane area
     */
    public final void subscribeForLaneArea(String laneAreaId, long start, long end) throws InternalFederateException {
        try {
            laneAreaSubscribe.execute(bridge, laneAreaId, start, end);
        } catch (CommandException e) {
            throw new InternalFederateException(String.format("Could not subscribe for lane area %s", laneAreaId), e);
        }
    }

    /**
     * Subscribes for the given traffic light group. It will then be included in the list
     * of TrafficLightUpdate result of {@link #simulateStep}.
     *
     * @param trafficLightGroupId the id of a traffic light group. Must be known to the simulation
     * @param start               the time [ns] the subscription should start
     * @param end                 the time [ns] the subscription should end
     * @throws InternalFederateException if it wasn't possible to subscribe for the wanted traffic light group
     */
    public void subscribeForTrafficLight(String trafficLightGroupId, long start, long end) throws InternalFederateException {
        try {
            trafficLightSubscribe.execute(bridge, trafficLightGroupId, start, end);
        } catch (CommandException e) {
            throw new InternalFederateException(String.format("Could not subscribe for traffic light group %s", trafficLightGroupId), e);
        }
    }

    /**
     * Changes the set of allowed vehicles of the chosen Lane.
     *
     * @param laneId          the id of the lane. Must be known to the simulation
     * @param allowedVClasses classes for which the lane should be opened
     * @throws InternalFederateException if changing the set of allowed vehicles for the chosen lane couldn't be done
     */
    public final void setLaneAllowedVehicles(String laneId, List<String> allowedVClasses) throws InternalFederateException {
        try {
            laneSetAllow.execute(bridge, laneId, allowedVClasses);
            updateBestLanesBeforeNextSimulationStep = true;
        } catch (CommandException e) {
            throw new InternalFederateException(String.format("Could not change the set of allowed vehicle classes of lane %s", laneId), e);
        }
    }

    /**
     * Changes the set of disallowed vehicles of the chosen Lane.
     *
     * @param laneId             the id of the lane. Must be known to the simulation
     * @param disallowedVClasses classes for which the lane should be closed
     * @throws InternalFederateException if changing the set of disallowed vehicles for the chosen lane couldn't be done
     */
    public final void setLaneDisallowedVehicles(String laneId, List<String> disallowedVClasses) throws InternalFederateException {
        try {
            laneSetDisallow.execute(bridge, laneId, disallowedVClasses);
            updateBestLanesBeforeNextSimulationStep = true;
        } catch (CommandException exception) {
            throw new InternalFederateException(
                    String.format("Could not change the set of disallowed vehicle classes of lane %s", laneId),
                    exception
            );
        }
    }

    /**
     * Changes the allowed maximum speed for the chosen Lane.
     *
     * @param laneId   the id of the lane. Must be known to the simulation
     * @param maxSpeed the maximum speed to set in m/s
     * @throws InternalFederateException if changing the allowed maximum speed for the wanted lane wasn't possible
     */
    public final void setLaneMaxSpeed(String laneId, double maxSpeed) throws InternalFederateException {
        try {
            laneSetMaxSpeed.execute(bridge, laneId, maxSpeed);
        } catch (CommandException e) {
            throw new InternalFederateException(String.format("Could not change the allowed maximum speed of lane %s", laneId), e);
        }
    }

    /**
     * Gets length of a lane on an edge.
     *
     * @param laneId The id of the lane. Must be known to the simulation.
     * @throws InternalFederateException if the length of the wanted lane on the wanted edge couldn't be retrieved
     */
    public final double getLengthOfLane(String laneId) throws InternalFederateException {
        try {
            return laneGetLength.execute(bridge, laneId);
        } catch (CommandException e) {
            throw new InternalFederateException(String.format("Could not retrieve length of lane %s", laneId), e);
        }
    }

    /**
     * Gets the shape of a lane, which is used for extracting the stop-lines of lanes.
     *
     * @param laneId The id of the lane. Must be known to the simulation.
     * @throws InternalFederateException if the length of the wanted lane on the wanted edge couldn't be retrieved
     */
    public final List<Position> getShapeOfLane(String laneId) throws InternalFederateException {
        try {
            return laneGetShape.execute(bridge, laneId);
        } catch (CommandException e) {
            throw new InternalFederateException(String.format("Could not retrieve shape of lane %s", laneId), e);
        }
    }

    /**
     * Enables the calculation of distance sensor values for the given vehicle.
     */
    public void configureDistanceSensors(String vehicleId, double maximumLookahead, boolean front, boolean rear) {
        SumoVehicleState vehicleState = getOrCreateVehicleState(vehicleId);
        if (front) {
            vehicleState.frontSensorDistance = maximumLookahead > 0 ? maximumLookahead : null;
        }
        if (rear) {
            vehicleState.rearSensorDistance = maximumLookahead > 0 ? maximumLookahead : null;
        }
    }

    /**
     * Simulates until the given time and returns the movements of all vehicles
     * within this simulation step.
     *
     * @param time Time step.
     * @return result of the simulation step.
     * @throws InternalFederateException if could not properly simulate step and read subscriptions
     */
    public TraciSimulationStepResult simulateUntil(long time) throws InternalFederateException {
        try {
            // PRE-SIM STEPS
            updateBestLanesIfNecessary();

            // SIMULATE STEP
            final List<AbstractSubscriptionResult> subscriptions = simulateStep.execute(bridge, time);

            // POST-PROCESSING
            sumoVehicles.values().forEach(v -> v.lastVehicleData = v.currentVehicleData);

            final Map<String, String> vehicleSegmentInfo = calculateVehicleSegmentInfo(subscriptions);

            final List<VehicleData> addedVehicles = new LinkedList<>();
            final List<VehicleData> updatedVehicles = new LinkedList<>();

            final List<AgentData> updatedPersons = new LinkedList<>();

            final List<InductionLoopInfo> updatedInductionLoops = new ArrayList<>();
            final List<LaneAreaDetectorInfo> updatedLaneAreas = new ArrayList<>();
            final Map<String, TrafficLightGroupInfo> trafficLightGroupInfos = new HashMap<>();

            for (AbstractSubscriptionResult subscriptionResult : subscriptions) {
                if (subscriptionResult instanceof VehicleSubscriptionResult result) {
                    final SumoVehicleState sumoVehicle = processVehicleSubscriptionResult(
                            time, result, vehicleSegmentInfo
                    );
                    if (sumoVehicle == null) {
                        continue;
                    }
                    if (sumoVehicle.isAdded()) {
                        addedVehicles.add(sumoVehicle.currentVehicleData);
                    } else if (sumoVehicle.isUpdated()) {
                        updatedVehicles.add(sumoVehicle.currentVehicleData);
                    }
                } else if (subscriptionResult instanceof PersonSubscriptionResult result) {
                    final AgentData personData = processPersonSubscriptionResult(time, result);
                    updatedPersons.add(personData);
                } else if (subscriptionResult instanceof InductionLoopSubscriptionResult result) {
                    final InductionLoopInfo inductionLoopInfo = processInductionLoopSubscriptionResult(time, result);
                    updatedInductionLoops.add(inductionLoopInfo);
                } else if (subscriptionResult instanceof LaneAreaSubscriptionResult result) {
                    final LaneAreaDetectorInfo laneAreaDetectorInfo = processLaneAreaSubscriptionResult(time, result);
                    updatedLaneAreas.add(laneAreaDetectorInfo);
                } else if (subscriptionResult instanceof TrafficLightSubscriptionResult result) {
                    final TrafficLightGroupInfo trafficLightGroupInfo = processTrafficLightSubscriptionResult(result);
                    trafficLightGroupInfos.put(trafficLightGroupInfo.getGroupId(), trafficLightGroupInfo);
                }
            }

            for (AbstractSubscriptionResult subscriptionResult : subscriptions) {
                if (subscriptionResult instanceof VehicleContextSubscriptionResult result) {
                    // needs to be done in a subsequent loop, as previously created VehicleData is updated here
                    processVehicleContextSubscriptionResult(result);
                }
            }

            final List<String> removedVehicles = findRemovedVehicles(time);
            final List<String> removedPersons = bridge.getSimulationControl().getArrivedPersons();

            final VehicleUpdates vehicleUpdates = new VehicleUpdates(time, addedVehicles, updatedVehicles, removedVehicles);
            final AgentUpdates personUpdates = new AgentUpdates(time, updatedPersons, removedPersons);
            final TrafficDetectorUpdates trafficDetectorUpdates = new TrafficDetectorUpdates(time, updatedLaneAreas, updatedInductionLoops);
            final TrafficLightUpdates trafficLightUpdates = new TrafficLightUpdates(time, trafficLightGroupInfos);

            currentTeleportingList = null; // reset cached teleporting list for this time step

            return new TraciSimulationStepResult(vehicleUpdates, personUpdates, trafficDetectorUpdates, trafficLightUpdates);
        } catch (CommandException e) {
            throw new InternalFederateException("Could not properly simulate step and read subscriptions", e);
        }
    }

    private SumoVehicleState processVehicleSubscriptionResult(final long time,
                                                              final VehicleSubscriptionResult veh,
                                                              final Map<String, String> vehicleSegmentInfo
    ) throws CommandException, InternalFederateException {

        final SumoVehicleState sumoVehicle = getOrCreateVehicleState(veh.id);
        final VehicleStopMode vehicleStopMode = VehicleStopMode.fromSumoInt(veh.stoppedStateEncoded);
        final boolean isParking = vehicleStopMode.isParking();
        final boolean hasInvalidPosition = veh.position == null || !veh.position.isValid();
        final boolean isNewVehicle = sumoVehicle.lastVehicleData == null;
        /* When a vehicle is trying to leave a parking area but the lane it's trying to enter is occupied, vehicles are put in a similar
         * state as when spawned. The position will be invalid until the lane is free. Therefore, we need to handle this case separately. */
        final boolean isWaitingToLeaveParking = !isNewVehicle && hasInvalidPosition // check for "waiting" state
                && sumoVehicle.lastVehicleData.getVehicleStopMode().isParking(); // check if was parked

        if (hasInvalidPosition && !isWaitingToLeaveParking) {
            if (isNewVehicle) {
                /* If a vehicle has not yet been simulated but loaded by SUMO, the vehicle's position will be invalid.
                 * We ignore this vehicle until it's added to the simulation. */
                log.debug("Skip vehicle {} which is loaded but not yet simulated.", veh.id);
                return null;
            }
            if (currentTeleportingList == null) {  // we call this only once per simulation step if at least one vehicle is teleporting
                currentTeleportingList = getTeleportingList.execute(bridge);
            }
            boolean isTeleporting = currentTeleportingList.contains(veh.id);
            if (isTeleporting) {
                /* If the vehicle is teleporting we don't stop the subscription. However, the state of the vehicle won't
                be updated until the teleport is finished, as we cannot be sure of the behaviour during teleporting. */
                sumoVehicle.currentVehicleData = new VehicleData.Builder(time, veh.id).copyFrom(sumoVehicle.lastVehicleData)
                        .stopped(VehicleStopMode.NOT_STOPPED) // teleporting vehicles are not stopped to differentiate from stopped vehicles
                        .create();
                return sumoVehicle;
            }
            log.warn("Vehicle {} has no valid position and will be removed.", veh.id);
            return sumoVehicle;
        }

        if (isParking && isNewVehicle) {
            log.warn("Skip vehicle {} which is inserted into simulation in PARKED state.", veh.id);
            return null;
        }

        final VehicleData.Builder vehicleDataBuilder = new VehicleData.Builder(time, veh.id);

        if (isWaitingToLeaveParking) {
            log.debug("Vehicle {} is currently waiting to leave parking area on edge {}.",
                    veh.id, sumoVehicle.lastVehicleData.getRoadPosition().getConnectionId());
            // if the vehicle is waiting to leave a parking area, we assume it's still parked and copy previous vehicle data
            vehicleDataBuilder.copyFrom(sumoVehicle.lastVehicleData);
        } else {
            vehicleDataBuilder
                    .position(veh.position.getGeographicPosition(), veh.position.getProjectedPosition())
                    .movement(veh.speed, veh.acceleration, fixDistanceDriven(veh.distanceDriven, sumoVehicle.lastVehicleData))
                    .orientation(DriveDirection.UNAVAILABLE, veh.heading, veh.slope)
                    .route(veh.routeId)
                    .signals(decodeVehicleSignals(veh.signalsEncoded))
                    .stopped(vehicleStopMode)
                    .sensors(createSensorData(sumoVehicle, veh.leadingVehicle, veh.followerVehicle, veh.minGap))
                    .laneArea(vehicleSegmentInfo.get(veh.id));
            if (sumoConfiguration.subscriptions != null && sumoConfiguration.subscriptions.contains(CSumo.SUBSCRIPTION_TRAINS)) {
                vehicleDataBuilder.additional(extractTrainData(veh));
            }
            if (isParking) {
                if (!sumoVehicle.lastVehicleData.isStopped()) {
                    log.info("Vehicle {} has parked at {} (edge: {})", veh.id, veh.position, veh.edgeId);
                }
                vehicleDataBuilder
                        // use the last known road position, otherwise we can not retrieve a valid one
                        .road(sumoVehicle.lastVehicleData.getRoadPosition())
                        // for parking vehicles, there are no consumptions and emissions to measure
                        .consumptions(new VehicleConsumptions(
                                new Consumptions(0d), sumoVehicle.lastVehicleData.getVehicleConsumptions().getAllConsumptions())
                        ).emissions(new VehicleEmissions(
                                new Emissions(0d, 0d, 0d, 0d, 0d), sumoVehicle.lastVehicleData.getVehicleEmissions().getAllEmissions()));
            } else {
                vehicleDataBuilder
                        .road(getRoadPosition(veh, sumoVehicle.lastVehicleData))
                        .consumptions(calculateConsumptions(veh, sumoVehicle.lastVehicleData))
                        .emissions(calculateEmissions(veh, sumoVehicle.lastVehicleData));
            }
        }

        sumoVehicle.currentVehicleData = vehicleDataBuilder.create();
        return sumoVehicle;
    }

    private PtVehicleData extractTrainData(VehicleSubscriptionResult veh) {
        return new PtVehicleData.Builder().withLineId(veh.line).nextStops(veh.nextStops).build();
    }

    private List<String> findRemovedVehicles(long time) throws CommandException {
        final List<String> removedVehicles = new LinkedList<>();
        for (Iterator<SumoVehicleState> vehicleIt = sumoVehicles.values().iterator(); vehicleIt.hasNext(); ) {
            SumoVehicleState vehicle = vehicleIt.next();
            if (vehicle.isNotUpdated()) {
                removedVehicles.add(vehicle.id);
                vehicleIt.remove();
                log.info("Removed vehicle \"{}\" at simulation time {}ns", vehicle.id, time);
            }
        }
        return removedVehicles;
    }


    private void processVehicleContextSubscriptionResult(VehicleContextSubscriptionResult contextSubscriptionResult) {
        SumoVehicleState sumoVehicleState = getOrCreateVehicleState(contextSubscriptionResult.id);
        for (VehicleSubscriptionResult vehInSight : contextSubscriptionResult.contextSubscriptions) {
            if (contextSubscriptionResult.id.equals(vehInSight.id)) {
                continue;
            }
            sumoVehicleState.currentVehicleData.getVehiclesInSight().add(
                    new SurroundingVehicle(vehInSight.id, vehInSight.position, vehInSight.speed,
                            vehInSight.heading, vehInSight.edgeId, vehInSight.laneIndex,
                            vehInSight.length, vehInSight.width, vehInSight.height)
            );
        }
    }

    private InductionLoopInfo processInductionLoopSubscriptionResult(long time, InductionLoopSubscriptionResult inductionLoop) {
        int count = (int) inductionLoop.vehiclesOnInductionLoop.stream().filter((s) -> s.leaveTime >= 0).count();
        return new InductionLoopInfo.Builder(time, inductionLoop.id)
                .vehicleData(inductionLoop.meanSpeed, inductionLoop.meanVehicleLength)
                .traffic(count, calculateFlow(time, inductionLoop.id, count))
                .create();
    }

    private AgentData processPersonSubscriptionResult(long time, PersonSubscriptionResult result) {
        return new AgentData(time, result.id, result.position.getGeographicPosition(), null, null, AgentData.TripStatus.WALKING);
    }

    /**
     * Calculates the flow in an introduction loop.
     *
     * @param time            Time step.
     * @param inductionLoopId The Id of the induction loop.
     * @param passedVehicles  Number of passed vehicles.
     * @return The vehicle flow.
     */
    private double calculateFlow(long time, String inductionLoopId, int passedVehicles) {
        InductionLoop inductionLoop = inductionLoops.get(inductionLoopId);
        if (inductionLoop == null) {
            inductionLoop = new InductionLoop(inductionLoopId, sumoConfiguration.trafficFlowMeasurementWindowInS * TIME.SECOND);
            inductionLoops.put(inductionLoopId, inductionLoop);
        }
        if (passedVehicles > 0) {
            inductionLoop.update(time, passedVehicles);
        }
        return inductionLoop.getTrafficFlowVehPerHour(time);
    }

    /**
     * Updates the lanes before next simulation step.
     *
     * @throws CommandException          if the status code of the response is ERROR. The TraCI connection is still available.
     * @throws InternalFederateException if some serious error occurs during writing or reading. The TraCI connection is shut down.
     */
    private void updateBestLanesIfNecessary() throws CommandException, InternalFederateException {
        if (updateBestLanesBeforeNextSimulationStep) {
            VehicleSetUpdateBestLanes updateBestLanes = bridge.getCommandRegister().getOrCreate(VehicleSetUpdateBestLanes.class);
            for (SumoVehicleState vehicle : sumoVehicles.values()) {
                if (vehicle.currentVehicleData != null) {
                    updateBestLanes.execute(bridge, vehicle.id);
                }
            }
            updateBestLanesBeforeNextSimulationStep = false;
        }
    }

    private LaneAreaDetectorInfo processLaneAreaSubscriptionResult(long time, LaneAreaSubscriptionResult laneArea) {
        return new LaneAreaDetectorInfo.Builder(time, laneArea.id)
                .vehicleData(laneArea.vehicleCount, laneArea.meanSpeed)
                .density((laneArea.vehicleCount * 1000d) / laneArea.length)
                .haltingVehicles(laneArea.haltingVehicles)
                .length(laneArea.length)
                .create();
    }

    private TrafficLightGroupInfo processTrafficLightSubscriptionResult(TrafficLightSubscriptionResult trafficLightSubscriptionResult) {
        final String trafficLightGroupId = trafficLightSubscriptionResult.id;
        final String currentProgram = trafficLightSubscriptionResult.currentProgramId;
        final int currentPhaseIndex = trafficLightSubscriptionResult.currentPhaseIndex;
        final long assumedTimeOfNextSwitch = trafficLightSubscriptionResult.assumedNextPhaseSwitchTime;
        final List<TrafficLightState> trafficLightState = TrafficLightStateDecoder.createStateListFromEncodedString(
                trafficLightSubscriptionResult.currentStateEncoded
        );
        return new TrafficLightGroupInfo(
                trafficLightGroupId, currentProgram, currentPhaseIndex, assumedTimeOfNextSwitch, trafficLightState
        );
    }


    /**
     * Creates an immutable object holding front and rear distance sensor data based on leading vehicle information.
     *
     * @param vehicleState    The state of the vehicle the VehicleSensors object should be created for
     * @param leadingVehicle  Information about the leading vehicle.
     * @param followerVehicle Information about the follower vehicle.
     * @param minGap          The minimum gap of the current vehicle.
     * @return an immutable sensor data object
     */
    private VehicleSensors createSensorData(
            SumoVehicleState vehicleState, LeadFollowVehicle leadingVehicle, LeadFollowVehicle followerVehicle, double minGap
    ) {

        boolean hasFrontSensorActivated = vehicleState.frontSensorDistance != null;
        boolean hasBackSensorActivated = vehicleState.rearSensorDistance != null;

        double frontDistance = hasFrontSensorActivated ? Double.POSITIVE_INFINITY : -1;
        double leaderSpeed = hasFrontSensorActivated ? Double.POSITIVE_INFINITY : -1;
        double rearDistance = hasBackSensorActivated ? Double.POSITIVE_INFINITY : -1;

        if (hasFrontSensorActivated
                && leadingVehicle.getDistance() < vehicleState.frontSensorDistance
        ) {
            frontDistance = leadingVehicle.getDistance() + minGap;
            VehicleData leadingVehicleData = getLastKnownVehicleData(leadingVehicle.getOtherVehicleId());
            leaderSpeed = leadingVehicleData != null ? leadingVehicleData.getSpeed() : -1;
        }
        if (hasBackSensorActivated
                && followerVehicle.getDistance() < vehicleState.rearSensorDistance
        ) {
            // using the own minGap is not 100% correct here, but it would be very expensive to fetch the minGap of the follower
            rearDistance = followerVehicle.getDistance() + minGap;
        }
        return new VehicleSensors(
                new DistanceSensor(frontDistance, rearDistance, -1d, -1d),
                new RadarSensor(leaderSpeed)
        );
    }

    /**
     * Maps vehicles to the their current lane segments (on which a vehicle is located).
     *
     * @param subscriptions Subscription data.
     * @return The segment of the vehicle.
     */
    private Map<String, String> calculateVehicleSegmentInfo(List<AbstractSubscriptionResult> subscriptions) {
        Map<String, String> vehicleToSegmentMap = new HashMap<>();
        for (AbstractSubscriptionResult laneAreaDetector : subscriptions) {
            if (!(laneAreaDetector instanceof LaneAreaSubscriptionResult result)) {
                continue;
            }
            for (String vehicle : result.vehicles) {
                vehicleToSegmentMap.putIfAbsent(vehicle, laneAreaDetector.id);
            }
        }
        return vehicleToSegmentMap;
    }

    private SumoVehicleState getOrCreateVehicleState(String id) {
        return sumoVehicles.computeIfAbsent(id, SumoVehicleState::new);
    }

    /**
     * Calculates vehicle consumption.
     *
     * @param veh             The result of subscribed vehicle.
     * @param lastVehicleData Last information of the vehicle.
     * @return The vehicle consumption.
     */
    private VehicleConsumptions calculateConsumptions(VehicleSubscriptionResult veh, VehicleData lastVehicleData) {
        final Consumptions currentConsumptions = new Consumptions(fixFuelConsumptionValue(veh.fuel));
        if (lastVehicleData != null && lastVehicleData.getVehicleConsumptions() != null) {
            return new VehicleConsumptions(
                    currentConsumptions,
                    lastVehicleData.getVehicleConsumptions().getAllConsumptions().addConsumptions(currentConsumptions)
            );
        }
        return new VehicleConsumptions(currentConsumptions, currentConsumptions);
    }

    private double fixFuelConsumptionValue(double consumption) {
        if (bridge.getCurrentVersion().isGreaterOrEqualThan(SumoVersion.SUMO_1_14_x)) {
            return fixEmissionValue(consumption / FUEL_DENSITY / 1000d);
        }
        return fixEmissionValue(consumption);
    }

    /**
     * Calculates the produced emissions of the vehicle.
     *
     * @param veh             The result of subscribed vehicle.
     * @param lastVehicleData Last information of the vehicle.
     * @return The produced emissions.
     */
    private VehicleEmissions calculateEmissions(VehicleSubscriptionResult veh, VehicleData lastVehicleData) {
        final Emissions currentEmissions = new Emissions(
                fixEmissionValue(veh.co2),
                fixEmissionValue(veh.co),
                fixEmissionValue(veh.hc),
                fixEmissionValue(veh.pmx),
                fixEmissionValue(veh.nox));
        if (lastVehicleData != null && lastVehicleData.getVehicleEmissions() != null) {
            return new VehicleEmissions(
                    currentEmissions,
                    lastVehicleData.getVehicleEmissions().getAllEmissions().addEmissions(currentEmissions)
            );
        }
        return new VehicleEmissions(currentEmissions, currentEmissions);
    }

    private double fixEmissionValue(double consumption) {
        return consumption * (sumoConfiguration.updateInterval / 1000d);
    }

    /**
     * Getter for the {@link IRoadPosition}.
     *
     * @param veh             The result of subscribed vehicle.
     * @param lastVehicleData Last information of the vehicle.
     * @return The position in the form of IRoadPosition.
     */
    private IRoadPosition getRoadPosition(VehicleSubscriptionResult veh, VehicleData lastVehicleData) {
        if (veh.edgeId == null) {
            return null;
        }

        IRoadPosition roadPosition = null;
        if (!veh.edgeId.contains(":")) {
            roadPosition = createRoadPosition(
                    veh.edgeId,
                    veh.laneIndex,
                    veh.lanePosition,
                    veh.lateralLanePosition
            );
        } else if (lastVehicleData != null) {
            roadPosition = lastVehicleData.getRoadPosition();
        }
        return roadPosition;
    }

    /**
     * Fixing the driven distance.
     *
     * @param value           Driven distance.
     * @param lastVehicleData Last vehicle info.
     * @return Fixed driven distance.
     */
    private double fixDistanceDriven(double value, VehicleData lastVehicleData) {
        if (value < 0) {
            return (lastVehicleData != null) ? lastVehicleData.getDistanceDriven() : 0;
        } else {
            return value;
        }
    }

    /**
     * Creates a road position as {@link IRoadPosition}.
     *
     * @param edgeId              The Id of the edge.
     * @param offset              The offset.
     * @param lateralLanePosition The lateral lane position.
     * @return Road position.
     */
    private IRoadPosition createRoadPosition(String edgeId, int laneIndex, double offset, double lateralLanePosition) {
        return new SimpleRoadPosition(edgeId, laneIndex, offset, lateralLanePosition);
    }


    /**
     * This method decodes the vehicle signals.
     *
     * @param signalsEncoded Encoded number indicating the vehicle signals.
     * @return a new {@link VehicleSignals} object
     */
    private VehicleSignals decodeVehicleSignals(int signalsEncoded) {
        boolean blinkerRight = getBit(signalsEncoded, 0);
        boolean blinkerLeft = getBit(signalsEncoded, 1);
        boolean blinkerEmergency = getBit(signalsEncoded, 2);
        boolean brakeLight = getBit(signalsEncoded, 3);
        boolean reverseDrive = getBit(signalsEncoded, 7);
        return new VehicleSignals(
                blinkerLeft, blinkerRight, blinkerEmergency, brakeLight, reverseDrive
        );
    }


    /**
     * Takes the decimal number Function takes the Nth bit (1 to 31).
     *
     * @param decimal the number
     * @param n       the Nth bit
     * @return the value of Nth bit from decimal. <code>true</code> if the bit is set, otherwise <code>false</code>.
     */
    private static boolean getBit(int decimal, int n) {
        // Shifting the 1 for N-1 bits
        int constant = 1 << n;
        // if the bit is set, return true, otherwise false
        return (decimal & constant) > 0;
    }

    /**
     * Getter for the last vehicle data.
     *
     * @param vehicleId The Id of the vehicle.
     * @return The latest known vehicle data.
     */
    public VehicleData getLastKnownVehicleData(String vehicleId) {
        SumoVehicleState vehicle = sumoVehicles.get(vehicleId);
        return vehicle != null ? vehicle.currentVehicleData : null;
    }

    /**
     * This method removes vehicles from the simulation.
     *
     * @param vehicleId The Id of the vehicle.
     * @param reason    The reason of remove.
     * @throws InternalFederateException if could not remove the wanted vehicle from the simulation
     */
    public void removeVehicle(String vehicleId, VehicleSetRemove.Reason reason) throws InternalFederateException {
        try {
            remove.execute(bridge, vehicleId, reason);
        } catch (CommandException e) {
            log.warn("Could not remove vehicle {}", vehicleId);
        }
    }
}
