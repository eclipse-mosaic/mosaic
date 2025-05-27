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

package org.eclipse.mosaic.fed.sumo.bridge.traci.constants;

public class CommandChangeVehicleValue {

    /**
     * Command to set vehicle variable.
     */
    public final static int COMMAND = 0xc4;

    /**
     * Vehicle stop.
     */
    public final static int VAR_STOP = 0x12;

    /**
     * Change to the lane.
     */
    public final static int VAR_CHANGE_LANE = 0x13;

    /**
     * Changes the speed for slow down.
     */
    public final static int VAR_SLOW_DOWN = 0x14;

    /**
     * Resume from a stop.
     */
    public final static int VAR_RESUME = 0x19;

    /**
     * The vehicle speed.
     */
    public final static int VAR_SPEED = 0x40;

    /**
     * The vehicle's color.
     */
    public final static int VAR_COLOR = 0x45;

    /**
     * Change route vy given Id.
     */
    public final static int VAR_CHANGE_ROUTE_BY_ID = 0x53;

    /**
     * Moves the vehicle to a new specific position.
     */
    public final static int VAR_MOVE_TO_XY = 0xb4;

    /**
     * Speed mode.
     */
    public final static int VAR_SPEED_MODE = 0xb3;

    /**
     * Speed factor to exceed the maximum permitted speed or to decelerate.
     */
    public final static int VAR_SPEED_FACTOR = 0x5e;

    /**
     * The maximum speed of the vehicle.
     */
    public final static int VAR_MAX_SPEED = 0x41;

    /**
     * Lane change mode of the vehicle.
     */
    public final static int VAR_LANE_CHANGE_MODE = 0xb6;

    /**
     * Add a vehicle.
     */
    public final static int VAR_ADD = 0x85;

    /**
     * Remove the vehicle.
     */
    public final static int VAR_REMOVE = 0x81;

    /**
     * The vehicle's minimum headway gap.
     */
    public final static int VAR_MIN_GAP = 0x4c;

    /**
     * The maximum acceleration.
     */
    public final static int VAR_ACCELERATION = 0x46;

    /**
     * The maximum deceleration.
     */
    public final static int VAR_DECELERATION = 0x47;

    /**
     * Driver imperfection.
     */

    public final static int VAR_IMPERFECTION = 0x5d;

    /**
     * Driver reaction time.
     */
    public final static int VAR_TAU = 0x48;

    /**
     * Update the lanes.
     */
    public static final int VAR_UPDATE_BEST_LANES = 0x6a;

    /**
     * The length of the vehicle.
     */
    public final static int VAR_LENGTH = 0x44;

    /**
     * Additional parameter for the vehicle.
     */
    public final static int VAR_PARAMETER = 0x7e;

    /**
     * Highlighting the vehicle in the GUI.
     */
    public final static int VAR_HIGHLIGHT = 0x6c;



    public final static SumoVar VAR_TAXI_DISPATCH = SumoVar.var(0x21);
}

