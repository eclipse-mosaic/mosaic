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

package org.eclipse.mosaic.fed.sumo.bridge.libsumo;

import static org.eclipse.mosaic.fed.sumo.bridge.api.VehicleSubscribe.FETCH_NUM_NEXT_STOPS;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.AbstractSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.InductionLoopSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.InductionLoopVehicleData;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.LaneAreaSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.LeadFollowVehicle;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.PersonSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.TrafficLightSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.VehicleContextSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.VehicleSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.config.CSumo;
import org.eclipse.mosaic.lib.enums.VehicleStopMode;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.pt.PtVehicleData;
import org.eclipse.mosaic.lib.util.objects.Position;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.sumo.libsumo.ContextSubscriptionResults;
import org.eclipse.sumo.libsumo.InductionLoop;
import org.eclipse.sumo.libsumo.LaneArea;
import org.eclipse.sumo.libsumo.Person;
import org.eclipse.sumo.libsumo.Simulation;
import org.eclipse.sumo.libsumo.StringDoublePair;
import org.eclipse.sumo.libsumo.StringVector;
import org.eclipse.sumo.libsumo.TraCINextStopData;
import org.eclipse.sumo.libsumo.TraCINextStopDataVector;
import org.eclipse.sumo.libsumo.TraCIPosition;
import org.eclipse.sumo.libsumo.TraCIVehicleData;
import org.eclipse.sumo.libsumo.TrafficLight;
import org.eclipse.sumo.libsumo.Vehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class SimulationSimulateStep implements org.eclipse.mosaic.fed.sumo.bridge.api.SimulationSimulateStep {

    private final static Logger LOG = LoggerFactory.getLogger(SimulationSimulateStep.class);

    final static List<String> VEHICLE_SUBSCRIPTIONS = new ArrayList<>();
    final static List<String> PERSON_SUBSCRIPTIONS = new ArrayList<>();
    final static List<String> INDUCTION_LOOP_SUBSCRIPTIONS = new ArrayList<>();
    final static List<String> LANE_AREA_SUBSCRIPTIONS = new ArrayList<>();
    final static List<String> TRAFFIC_LIGHT_SUBSCRIPTIONS = new ArrayList<>();

    private final boolean fetchRoadPosition;
    private final boolean fetchEmissions;
    private final boolean fetchLeaderAndFollower;
    private final boolean fetchSignals;
    private final boolean fetchTrainData;

    /**
     * Constructor for {@link SimulationSimulateStep}, used
     * @param ignored the {@link Bridge} ignored for the libsumo implementation
     * @param sumoConfiguration configuration to see which subscriptions should be fetched
     */
    public SimulationSimulateStep(Bridge ignored, CSumo sumoConfiguration) {
        VEHICLE_SUBSCRIPTIONS.clear();
        PERSON_SUBSCRIPTIONS.clear();
        INDUCTION_LOOP_SUBSCRIPTIONS.clear();
        LANE_AREA_SUBSCRIPTIONS.clear();
        TRAFFIC_LIGHT_SUBSCRIPTIONS.clear();

        fetchRoadPosition = sumoConfiguration.subscriptions.contains(CSumo.SUBSCRIPTION_ROAD_POSITION);
        fetchEmissions = sumoConfiguration.subscriptions.contains(CSumo.SUBSCRIPTION_EMISSIONS);
        fetchSignals = sumoConfiguration.subscriptions.contains(CSumo.SUBSCRIPTION_SIGNALS);
        fetchLeaderAndFollower = sumoConfiguration.subscriptions.contains(CSumo.SUBSCRIPTION_LEADER);
        fetchTrainData = sumoConfiguration.subscriptions.contains(CSumo.SUBSCRIPTION_TRAINS);
    }

    /**
     * Constructor for {@link SimulationSimulateStep} subscribing to all values.
     */
    public SimulationSimulateStep() {
        VEHICLE_SUBSCRIPTIONS.clear();
        PERSON_SUBSCRIPTIONS.clear();
        INDUCTION_LOOP_SUBSCRIPTIONS.clear();
        LANE_AREA_SUBSCRIPTIONS.clear();
        TRAFFIC_LIGHT_SUBSCRIPTIONS.clear();

        fetchRoadPosition = true;
        fetchEmissions = true;
        fetchSignals = true;
        fetchLeaderAndFollower = true;
        fetchTrainData = true;
    }

    public List<AbstractSubscriptionResult> execute(Bridge bridge, long time) throws CommandException, InternalFederateException {
        final List<AbstractSubscriptionResult> results = new ArrayList<>();

        Simulation.step((double) (time) / TIME.SECOND);

        readVehicles(results);
        readPersons(results);
        readSurroundingVehiclesFromContextSubscriptions(results);
        readInductionLoops(results);
        readLaneAreas(results);
        readTrafficLights(results);
        return results;
    }

    private void readPersons(List<AbstractSubscriptionResult> results) {
        StringVector arrivedPersonIds = Simulation.getArrivedPersonIDList();
        for (String arrivedPersonId : arrivedPersonIds) {
            PERSON_SUBSCRIPTIONS.remove(Bridge.PERSON_ID_TRANSFORMER.fromExternalId(arrivedPersonId));
        }
        arrivedPersonIds.delete();

        String sumoPersonId;
        String mosaicPersonId;
        for (Iterator<String> subscriptionIterator = PERSON_SUBSCRIPTIONS.iterator(); subscriptionIterator.hasNext(); ) {
            mosaicPersonId = subscriptionIterator.next();
            sumoPersonId = Bridge.PERSON_ID_TRANSFORMER.toExternalId(mosaicPersonId);

            PersonSubscriptionResult result = new PersonSubscriptionResult();
            result.id = mosaicPersonId;

            try {
                result.position = getPosition(Person.getPosition(sumoPersonId, true));
            } catch (IllegalArgumentException e) {
                LOG.error("Could not read position from person. Unsubscribe.", e);
                subscriptionIterator.remove();
            }
            if (result.position == null) {
                continue;
            }

            result.speed = Person.getSpeed(sumoPersonId);
            result.heading = Person.getAngle(sumoPersonId);

            results.add(result);
        }
    }

    private void readVehicles(List<AbstractSubscriptionResult> results) {
        StringVector arrivedIds = Simulation.getArrivedIDList();
        for (String arrived : arrivedIds) {
            VEHICLE_SUBSCRIPTIONS.remove(Bridge.VEHICLE_ID_TRANSFORMER.fromExternalId(arrived));
        }
        arrivedIds.delete();

        String sumoVehicleId;
        String mosaicVehicleId;
        for (Iterator<String> subscriptionsIt = VEHICLE_SUBSCRIPTIONS.iterator(); subscriptionsIt.hasNext(); ) {
            mosaicVehicleId = subscriptionsIt.next();
            sumoVehicleId = Bridge.VEHICLE_ID_TRANSFORMER.toExternalId(mosaicVehicleId);

            VehicleSubscriptionResult result = new VehicleSubscriptionResult();
            result.id = mosaicVehicleId;

            try {
                result.position = getPosition(Vehicle.getPosition(sumoVehicleId, true));
            } catch (IllegalArgumentException e) {
                LOG.error("Could not read position from vehicle. Unsubscribe.", e);
                subscriptionsIt.remove();
            }
            if (result.position == null) {
                continue;
            }

            result.speed = Vehicle.getSpeed(sumoVehicleId);
            result.distanceDriven = Vehicle.getDistance(sumoVehicleId);
            result.heading = Vehicle.getAngle(sumoVehicleId);
            result.slope = Vehicle.getSlope(sumoVehicleId);
            result.acceleration = Vehicle.getAcceleration(sumoVehicleId);
            result.stoppedStateEncoded = Vehicle.getStopState(sumoVehicleId);
            result.routeId = Vehicle.getRouteID(sumoVehicleId);

            if (fetchSignals) {
                result.signalsEncoded = Vehicle.getSignals(sumoVehicleId);
            }

            if (fetchRoadPosition) {
                result.edgeId = Vehicle.getRoadID(sumoVehicleId);
                result.lanePosition = Vehicle.getLanePosition(sumoVehicleId);
                result.lateralLanePosition = Vehicle.getLateralLanePosition(sumoVehicleId);
                result.laneIndex = Vehicle.getLaneIndex(sumoVehicleId);
            }

            if (fetchEmissions) {
                result.co2 = Vehicle.getCO2Emission(sumoVehicleId);
                result.co = Vehicle.getCOEmission(sumoVehicleId);
                result.hc = Vehicle.getHCEmission(sumoVehicleId);
                result.pmx = Vehicle.getPMxEmission(sumoVehicleId);
                result.nox = Vehicle.getNOxEmission(sumoVehicleId);
                result.fuel = Vehicle.getFuelConsumption(sumoVehicleId);
            }

            if (fetchLeaderAndFollower) {
                result.minGap = Vehicle.getMinGap(sumoVehicleId);
                result.leadingVehicle = getLeaderFollower(Vehicle.getLeader(sumoVehicleId));
                result.followerVehicle = getLeaderFollower(Vehicle.getFollower(sumoVehicleId));
            } else {
                result.leadingVehicle = LeadFollowVehicle.NONE;
                result.followerVehicle = LeadFollowVehicle.NONE;
            }

            if (fetchTrainData) {
                result.line = Vehicle.getLine(sumoVehicleId);
                result.nextStops = getNextStop(Vehicle.getStops(sumoVehicleId, FETCH_NUM_NEXT_STOPS));
            }

            results.add(result);
        }
    }

    private List<PtVehicleData.StoppingPlace> getNextStop(TraCINextStopDataVector stops) {
        if (stops.isEmpty()) {
            return null;
        }
        List<PtVehicleData.StoppingPlace> nextStops = new ArrayList<>();
        for (TraCINextStopData stopData : stops) {
            PtVehicleData.StoppingPlace stoppingPlace = new PtVehicleData.StoppingPlace.Builder()
                    .stoppingPlaceId(stopData.getStoppingPlaceID())
                    .laneId(stopData.getLane())
                    .startPos(stopData.getStartPos()).endPos(stopData.getEndPos())
                    .stopFlags(VehicleStopMode.fromSumoInt(stopData.getStopFlags()))
                    .stopDuration(stopData.getDuration()).stoppedUntil(stopData.getUntil())
                    .build();
            nextStops.add(stoppingPlace);
        }
        return nextStops;
    }

    private LeadFollowVehicle getLeaderFollower(StringDoublePair leaderFollower) {
        try {
            if (StringUtils.isNotBlank(leaderFollower.getFirst())) {
                return new LeadFollowVehicle(
                        Bridge.VEHICLE_ID_TRANSFORMER.fromExternalId(leaderFollower.getFirst()),
                        leaderFollower.getSecond()
                );
            }
            return LeadFollowVehicle.NONE;
        } finally {
            leaderFollower.delete();
        }
    }

    private Position getPosition(TraCIPosition traCIPosition) {
        try {
            if (traCIPosition.getX() < -1000 && traCIPosition.getY() < -1000) {
                return null;
            }
            return new Position(CartesianPoint.xyz(traCIPosition.getX(), traCIPosition.getY(), traCIPosition.getZ() < -1000 ? 0 : traCIPosition.getZ()));
        } finally {
            traCIPosition.delete();
        }
    }

    private void readSurroundingVehiclesFromContextSubscriptions(List<AbstractSubscriptionResult> results) {
        ContextSubscriptionResults contextResults = Vehicle.getAllContextSubscriptionResults();
        contextResults.forEach((sumoId, subscriptionResults) -> {
            final VehicleContextSubscriptionResult result = new VehicleContextSubscriptionResult();
            result.id = Bridge.VEHICLE_ID_TRANSFORMER.fromExternalId(sumoId);
            subscriptionResults.keySet().forEach((childSumoId) -> {
                        if (childSumoId.equals(sumoId)) {
                            return;
                        }
                        final VehicleSubscriptionResult surroundingVehicle = new VehicleSubscriptionResult();
                        surroundingVehicle.id = Bridge.VEHICLE_ID_TRANSFORMER.fromExternalId(childSumoId);

                        // reading from subscription is much slower than directly accessing vehicle values
                        surroundingVehicle.position = getPosition(Vehicle.getPosition(childSumoId, true));
                        surroundingVehicle.speed = Vehicle.getSpeed(childSumoId);
                        surroundingVehicle.heading = Vehicle.getAngle(childSumoId);

                        if (surroundingVehicle.position != null) {
                            result.contextSubscriptions.add(surroundingVehicle);
                        }
                    }
            );
            if (!result.contextSubscriptions.isEmpty()) {
                results.add(result);
            }
            subscriptionResults.delete();
        });
        contextResults.delete();
    }

    private void readInductionLoops(List<AbstractSubscriptionResult> results) {
        for (String inductionLoop : INDUCTION_LOOP_SUBSCRIPTIONS) {
            InductionLoopSubscriptionResult result = new InductionLoopSubscriptionResult();
            result.id = inductionLoop;
            result.meanSpeed = InductionLoop.getLastStepMeanSpeed(inductionLoop);
            result.meanVehicleLength = InductionLoop.getLastStepMeanLength(inductionLoop);
            result.vehiclesOnInductionLoop = new ArrayList<>();

            for (TraCIVehicleData inductionLoopVeh : InductionLoop.getVehicleData(inductionLoop)) {
                InductionLoopVehicleData vehicleData = new InductionLoopVehicleData();
                vehicleData.vehicleId = Bridge.VEHICLE_ID_TRANSFORMER.fromExternalId(inductionLoopVeh.getId());
                vehicleData.entryTime = (long) (inductionLoopVeh.getEntryTime() * TIME.NANO_SECOND);
                vehicleData.leaveTime = (long) (inductionLoopVeh.getLeaveTime() * TIME.NANO_SECOND);
                result.vehiclesOnInductionLoop.add(vehicleData);
            }
            results.add(result);
        }
    }

    private void readLaneAreas(List<AbstractSubscriptionResult> results) {
        for (String laneArea : LANE_AREA_SUBSCRIPTIONS) {
            LaneAreaSubscriptionResult result = new LaneAreaSubscriptionResult();
            result.id = laneArea;
            result.vehicleCount = LaneArea.getLastStepVehicleNumber(laneArea);
            result.meanSpeed = LaneArea.getLastStepMeanSpeed(laneArea);
            result.haltingVehicles = LaneArea.getLastStepHaltingNumber(laneArea);
            result.length = LaneArea.getLength(laneArea);

            final StringVector laneAreaIds = LaneArea.getLastStepVehicleIDs(laneArea);
            for (String vehicle : laneAreaIds) {
                result.vehicles.add(Bridge.VEHICLE_ID_TRANSFORMER.fromExternalId(vehicle));
            }
            laneAreaIds.delete();
            results.add(result);
        }
    }

    private void readTrafficLights(List<AbstractSubscriptionResult> results) {
        for (String trafficLight : TRAFFIC_LIGHT_SUBSCRIPTIONS) {
            TrafficLightSubscriptionResult result = new TrafficLightSubscriptionResult();
            result.id = trafficLight;
            result.currentProgramId = TrafficLight.getProgram(trafficLight);
            result.assumedNextPhaseSwitchTime = (long) (TrafficLight.getNextSwitch(trafficLight) * TIME.SECOND);
            result.currentPhaseIndex = TrafficLight.getPhase(trafficLight);
            result.currentStateEncoded = TrafficLight.getRedYellowGreenState(trafficLight);
            results.add(result);
        }

    }
}
