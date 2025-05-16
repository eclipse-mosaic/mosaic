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

public class CommandRetrieveTrafficLightValue {

    /**
     * Command to asking for the value of a certain variable of the traffic light.
     */
    public final static int COMMAND = 0xa2;

    /**
     * Ids of traffic light groups within the simulation.
     */
    public final static int VAR_ID_LIST = 0x00;

    /**
     * The tl's state of light definitions.
     */
    public final static int VAR_STATE = 0x20;

    /**
     * The default total duration of the currently active phase.
     */
    public final static int VAR_PHASE_DEFAULT_DURATION = 0x24;

    /**
     * The list of lanes which are controlled by the traffic light.
     */
    public final static int VAR_CONTROLLED_LANES = 0x26;

    /**
     * The links controlled by the traffic light.
     */
    public final static int VAR_CONTROLLED_LINKS = 0x27;

    /**
     * The junctions controlled by the traffic light.
     */
    public final static int VAR_CONTROLLED_JUNCTIONS = 0x2a;

    /**
     * The index of the current phase in the current program.
     */
    public final static int VAR_CURRENT_PHASE_INDEX = 0x28;

    /**
     * Definition of the complete traffic light program.
     */
    public final static int VAR_COMPLETE_DEFINITION = 0x2b;

    /**
     * The assumed time at which the traffic lights change the phase.
     */
    public final static int VAR_TIME_OF_NEXT_SWITCH = 0x2d;

    /**
     * The id of the current program.
     */
    public static final int VAR_CURRENT_PROGRAM = 0x29;

    /**
     * The encoded state of the traffic light group.
     */
    public static final int VAR_CURRENT_STATE = 0x20;
}
