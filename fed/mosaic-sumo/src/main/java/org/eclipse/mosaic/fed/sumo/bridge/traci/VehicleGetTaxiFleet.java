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
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandRetrieveVehicleState;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.TraciDatatypes;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.ListTraciReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.VehicleIdTraciReader;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.List;
import java.util.Locale;

public class VehicleGetTaxiFleet extends AbstractTraciCommand<List<String>>
        implements org.eclipse.mosaic.fed.sumo.bridge.api.VehicleGetTaxiFleet {

    /**
     * Creates a new {@link VehicleGetTaxiFleet} traci command, which will return all taxis for the requested state.
     * Access needs to be public, because command is called using Reflection.
     *
     * @see <a href="https://sumo.dlr.de/docs/TraCI/VehicleType_Value_Retrieval.html">VehicleType Value Retrieval</a>
     */
    @SuppressWarnings("WeakerAccess")
    public VehicleGetTaxiFleet() {
        super(TraciVersion.LOWEST);

        write()
                .command(CommandRetrieveVehicleState.COMMAND)
                .variable(CommandRetrieveVehicleState.VAR_TAXI_FLEET)
                .writeString("")  // command does not refer to a specific vehicle
                .writeIntParamWithType();

        read()
                .skipBytes(2)
                .skipString()
                .expectByte(TraciDatatypes.STRING_LIST)
                .readComplex(new ListTraciReader<>(new VehicleIdTraciReader()));
    }

    public List<String> execute(Bridge bridge, int taxiState) throws CommandException, InternalFederateException {
        return executeAndReturn(bridge, taxiState).orElseThrow(() -> new CommandException(
                String.format(Locale.ENGLISH, "Could not return taxi vehicles for the state %d", taxiState)));
    }

    @Override
    protected List<String> constructResult(Status status, Object... objects) {
        return (List<String>) objects[0];
    }
}
