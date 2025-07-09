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

package org.eclipse.mosaic.fed.sumo.config;

import org.eclipse.mosaic.fed.sumo.bridge.traci.VehicleSetMoveToXY;
import org.eclipse.mosaic.lib.util.gson.TimeFieldAdapter;

import com.google.gson.annotations.JsonAdapter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The SUMO Ambassador configuration class.
 */
public class CSumo implements Serializable {

    private static final long serialVersionUID = 1479294781446446539L;

    /**
     * The Interval after which positions are published.
     * Define the size of one simulation step in sumo (minimal value: 1).
     * The default value is 1000 (1s). Unit: [ms].
     */
    @JsonAdapter(TimeFieldAdapter.LegacyMilliSeconds.class)
    public Long updateInterval = 1000L;

    /**
     * Name of the main SUMO scenario configuration (*.sumocfg). If this member
     * equals null, the SUMO ambassador will try to find a ".sumocfg" file.
     */
    public String sumoConfigurationFile;

    /**
     * If too many vehicles try to enter the simulation, SUMO might skip some
     * vehicles and tries to enter them later again. This behavior can lead to
     * wrong simulation results. This parameter defines, if the ambassador should try
     * to continue the simulation in such cases. Quit SUMO, if an error occurs
     * while inserting a new vehicle (e.g. due to high vehicle densities)
     * (recommended: true).
     */
    public boolean exitOnInsertionError = true;

    /**
     * Add additional parameter to the SUMO start command. Prepend always a
     * blank. The default is a seed of 100000. Set a particular seed for the
     * random number generator. By using different values you can have different
     * but still reproducible simulation runs. Ignore possible waiting times by
     * setting time-to-teleport to 0. This avoid unmoved "vehicles" (in our case
     * also RSUs) being removed from simulation.
     */
    public String additionalSumoParameters = "--time-to-teleport 0 --seed 100000";

    /**
     * Defines the time window in seconds in which vehicle counts on induction loops
     * should be aggregated to traffic flow (veh/h).
     */
    public int trafficFlowMeasurementWindowInS = 300;

    /**
     * This offset is added to all time-gap related parametrizations of vehicles.
     * (e.g. declaring vehicle types to SUMO, changing time-gap/reaction time during simulation)
     * This could be helpful as IDM should be parametrized with lower time gaps to achieve specific time gap values.
     */
    public double timeGapOffset = 0;

    /**
     * If set to {@code true} all vehicles will be subscribed (see
     * {@link org.eclipse.mosaic.fed.sumo.bridge.facades.SimulationFacade#subscribeForVehicle(String, long, long)}).
     * If set to {@code false} only vehicles with applications mapped to them will be subscribed.
     */
    public boolean subscribeToAllVehicles = true;

    /**
     * Prints out all traci calls.
     */
    public boolean debugTraciCalls = false;

    /**
     * A optional list of subscriptions for each vehicle in the simulation. The less subscriptions given,
     * the faster the simulation. Per default (if this list is set to null), all subscriptions are activated.
     * Please note, that some components expect specific information, such as the road position. If those information
     * is not subscribed, these components may fail.
     * <br/><br/>
     * Possible values are: "roadposition", "signals", "emissions", "leader"
     */
    public Collection<String> subscriptions;

    /**
     * Subscription identifier for everything which is related to the position of the vehicle on the road,
     * such as the ID of the road.
     */
    public final static String SUBSCRIPTION_ROAD_POSITION = "roadposition";

    /**
     * Subscription identifier for everything which is related to the signals on the vehicle.
     */
    public final static String SUBSCRIPTION_SIGNALS = "signals";

    /**
     * Subscription identifier for everything which is related to the emissions on the vehicle, such as
     * CO2, NOX, and more, including fuel consumption.
     */
    public final static String SUBSCRIPTION_EMISSIONS = "emissions";

    /**
     * Subscription identifier for subscribing to leader and follower information for each vehicle.
     */
    public final static String SUBSCRIPTION_LEADER = "leader";

    /**
     * Subscription identifier for subscribing to train information such next stops and line information.
     */
    public final static String SUBSCRIPTION_TRAINS = "trains";

    /**
     * The default lane width to be used when adding traffic signs per
     * lane (default: 3.2) (only relevant when using SUMO-GUI)
     */
    public double trafficSignLaneWidth = 3.2;

    /**
     * Configure to highlight a vehicle in the GUI if it's
     * performing a route or lane change, e.g. for debugging purposes
     * (only relevant when using SUMO-GUI).
     * <br/><br/>
     * Possible values are: "changeLane", "changeRoute"
     */
    public Collection<String> highlights = new ArrayList<>();

    /**
     * Allows configuring specialised vType parameters, which can't be configured via Mapping.
     * E.g. parameters for the lane change model of vehicles.
     */
    public Map<String, Map<String, String>> additionalVehicleTypeParameters = new HashMap<>();


    /**
     * Configure the mode with which a vehicle is moved to a explicit postion with the SUMO command moveToXY().
     * (SWITCH_ROUTE, KEEP_ROUTE or EXACT_POSITION)
     */
    public VehicleSetMoveToXY.Mode moveToXyMode = org.eclipse.mosaic.fed.sumo.bridge.api.VehicleSetMoveToXY.Mode.SWITCH_ROUTE;

    public final static String HIGHLIGHT_CHANGE_LANE = "changeLane";
    public final static String HIGHLIGHT_CHANGE_ROUTE = "changeRoute";
}

