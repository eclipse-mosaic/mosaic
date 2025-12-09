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
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.Locale;

/**
 * This class represents the SUMO command which allows getting the vehicle's person capacity.
 */
public class VehicleGetPersonCapacity
        extends AbstractTraciCommand<Integer>
        implements org.eclipse.mosaic.fed.sumo.bridge.api.VehicleGetPersonCapacity {

    /**
     * Creates a new {@link VehicleGetPersonCapacity} traci command, which will
     * return the person capacity this vehicle has.
     * Access needs to be public, because command is called using Reflection.
     *
     * @see <a href="https://sumo.dlr.de/docs/TraCI/VehicleType_Value_Retrieval.html">VehicleType Value Retrieval</a>
     */
    @SuppressWarnings("WeakerAccess")
    public VehicleGetPersonCapacity() {
        super(TraciVersion.LOWEST);

        write()
                .command(CommandRetrieveVehicleState.COMMAND)
                .variable(CommandRetrieveVehicleState.VAR_PERSON_CAPACITY)
                .writeVehicleIdParam();

        read()
                .skipBytes(2)
                .skipString()
                .readIntegerWithType();
    }

    /**
     * This method executes the command with the given arguments in order to retrieve the
     * number of people that can ride this vehicle simultaneously.
     *
     * @param bridge    Connection to SUMO.
     * @param vehicleId ID of the vehicle.
     * @return the person capacity of the given vehicle
     * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
     * @throws InternalFederateException if some serious error occurs during writing or reading. The TraCI connection is shut down.
     */
    public int execute(Bridge bridge, String vehicleId) throws CommandException, InternalFederateException {
        return executeAndReturn(bridge, vehicleId).orElseThrow(
                () -> new CommandException(
                        String.format(Locale.ENGLISH, "Could not extract person capacity for Vehicle: %s.", vehicleId)
                )
        );
    }

    @Override
    protected Integer constructResult(Status status, Object... objects) {
        return (Integer) objects[0];
    }
}
