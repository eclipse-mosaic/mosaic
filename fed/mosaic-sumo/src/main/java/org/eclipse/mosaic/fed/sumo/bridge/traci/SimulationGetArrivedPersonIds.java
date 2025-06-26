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

package org.eclipse.mosaic.fed.sumo.bridge.traci;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.fed.sumo.bridge.TraciVersion;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.Status;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandRetrieveSimulationValue;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.TraciDatatypes;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.ListTraciReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.PersonIdTraciReader;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.List;

/**
 * This class represents the SUMO command which allows to get the Id's of the vehicles departed the simulation.
 */
public class SimulationGetArrivedPersonIds
        extends AbstractTraciCommand<List<String>>
        implements org.eclipse.mosaic.fed.sumo.bridge.api.SimulationGetArrivedPersonIds {

    /**
     * Creates a new {@link SimulationGetArrivedPersonIds} traci command,
     * which will return a list of all departed persons once executed.
     * Access needs to be public, because command is called using Reflection.
     */
    @SuppressWarnings("WeakerAccess")
    public SimulationGetArrivedPersonIds() {
        super(TraciVersion.LOWEST);

        write()
                .command(CommandRetrieveSimulationValue.COMMAND)
                .variable(CommandRetrieveSimulationValue.VAR_ARRIVED_PERSONS)
                .writeString("0");

        read()
                .skipBytes(2)
                .skipString()
                .expectByte(TraciDatatypes.STRING_LIST)
                .readComplex(new ListTraciReader<>(new PersonIdTraciReader()));
    }

    /**
     * This method executes the command with the given arguments in order to get the vehicles Id's
     * in the simulation, which departed the simulation.
     *
     * @param bridge Connection to SUMO.
     * @return List of vehicle Id's.
     * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
     * @throws InternalFederateException if some serious error occurs during writing or reading. The TraCI connection is shut down.
     */
    public List<String> execute(Bridge bridge) throws CommandException, InternalFederateException {
        return executeAndReturn(bridge).orElseThrow(
                () -> new CommandException("Could not read list of departed Persons.")
        );
    }

    @Override
    protected List<String> constructResult(Status status, Object... objects) {
        return (List<String>) objects[0];
    }
}
