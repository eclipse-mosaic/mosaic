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

public class CommandRetrievePersonState {

    /**
     * Command for the tracking the given person.
     */
    public final static int COMMAND = 0xae;

    /**
     * Taxi reservations within the scenario.
     */
    public final static SumoVar VAR_TAXI_RESERVATIONS = SumoVar.var(0xc6);
}
