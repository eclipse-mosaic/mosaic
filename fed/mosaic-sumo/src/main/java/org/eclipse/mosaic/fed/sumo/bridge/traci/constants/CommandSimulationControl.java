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

public class CommandSimulationControl {

    /**
     * Command to get the API version.
     */
    public final static int COMMAND_VERSION = 0x00;

    /**
     * Command forces SUMO to perform simulation until the given time step is reached.
     */
    public final static int COMMAND_SIMULATION_STEP = 0x02;

    /**
     * This TraCI command closes the connection to any client, stops simulation and shuts down SUMO.
     */
    public final static int COMMAND_CLOSE = 0x7f;

    /**
     * Command to set the client order if multiple clients are supposed to be connected to a single simulation (must be unique).
     */
    public final static int COMMAND_SET_ORDER = 0x03;
}

