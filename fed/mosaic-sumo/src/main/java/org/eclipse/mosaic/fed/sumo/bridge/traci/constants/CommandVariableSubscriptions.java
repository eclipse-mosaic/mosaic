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

public class CommandVariableSubscriptions {

    /**
     * Command to subscribe vehicle variable.
     */
    public final static int COMMAND_SUBSCRIBE_VEHICLE_VALUES = 0xd4;

    /**
     * Response to subscribe vehicle variable.
     */
    public final static int RESPONSE_SUBSCRIBE_VEHICLE_VALUES = 0xe4;

    /**
     * Command to subscribe person variable.
     */
    public final static int COMMAND_SUBSCRIBE_PERSON_VALUES = 0xde;

    /**
     * Command to subscribe person variable.
     */
    public final static int RESPONSE_SUBSCRIBE_PERSON_VALUES = 0xee;

    /**
     * Command to subscribe induction loop variable.
     */
    public final static int COMMAND_SUBSCRIBE_INDUCTION_LOOP_VALUES = 0xd0;

    /**
     * Response to subscribe induction loop variable.
     */
    public final static int RESPONSE_SUBSCRIBE_INDUCTION_LOOP_VALUES = 0xe0;

    /**
     * Command to subscribe areal detector variable.
     */
    public final static int COMMAND_SUBSCRIBE_LANE_AREA_VALUES = 0xdd;

    /**
     * Response to subscribe areal detector variable.
     */
    public final static int RESPONSE_SUBSCRIBE_LANE_AREA_VALUES = 0xed;

    /**
     * Change the state of a lane.
     */
    public final static int COMMAND_CHANGE_LANE_STATE = 0xc3;

    /**
     * Retrieve information from a traffic light group.
     */
    public final static int COMMAND_SUBSCRIBE_TRAFFIC_LIGHT_VALUES = 0xd2;

    /**
     * Response to subscribe traffic light group variable.
     */
    public final static int RESPONSE_SUBSCRIBE_TRAFFIC_LIGHT_VALUES = 0xe2;

    /**
     * Subscribe for vehicle values within a context of another vehicle.
     */
    public final static int COMMAND_SUBSCRIBE_CONTEXT_VEHICLE_VALUES = 0x84;

    /**
     * Response to vehicle context subscriptions.
     */
    public final static int RESPONSE_SUBSCRIBE_CONTEXT_VEHICLE_VALUES = 0x94;

    /**
     * Add a filter to reduce previous set context subscription.
     */
    public final static int COMMAND_ADD_CONTEXT_SUBSCRIPTION_FILTER = 0x7e;

    /**
     * A subscription filter to return vehicles within field of vision
     */
    public final static int SUBSCRIPTION_FILTER_FIELD_OF_VISION = 0x0a;

}
