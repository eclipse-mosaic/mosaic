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

public class CommandRetrieveSimulationValue {

    /**
     * Command for the value of a certain variable of the induction loop within the last simulation step.
     */
    public final static int COMMAND = 0xab;

    /**
     * Id's of vehicles which departed in this time step.
     */
    public final static int VAR_DEPARTED_VEHICLES = 0x74;


    /**
     * Id's of persons which departed in this time step.
     */
    public final static int VAR_DEPARTED_PERSONS = 0x25;

    /**
     * Id's of persons which arrived in this time step.
     */
    public final static int VAR_ARRIVED_PERSONS = 0x27;
}
