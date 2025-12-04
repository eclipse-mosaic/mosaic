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
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandChangeVehicleValue;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.List;

public class VehicleDispatchTaxi extends AbstractTraciCommand<String>
        implements org.eclipse.mosaic.fed.sumo.bridge.api.VehicleDispatchTaxi {

    /**
     * Creates a new {@link VehicleDispatchTaxi} traci command, which will return all taxis for the requested state.
     * Access needs to be public, because command is called using Reflection.
     *
     * @see <a href="https://sumo.dlr.de/docs/TraCI/VehicleType_Value_Retrieval.html">VehicleType Value Retrieval</a>
     */
    @SuppressWarnings("WeakerAccess")
    public VehicleDispatchTaxi() {
        super(TraciVersion.LOWEST);

        write()
                .command(CommandChangeVehicleValue.COMMAND)
                .variable(CommandChangeVehicleValue.VAR_TAXI_DISPATCH)
                .writeVehicleIdParam()
                .writeStringListParamWithType();
    }

    public void execute(Bridge bridge, String vehicleId, List<String> reservations) throws CommandException, InternalFederateException {
        super.execute(bridge, vehicleId, reservations);
    }

    @Override
    protected String constructResult(Status status, Object... objects) {
        return null;
    }
}
