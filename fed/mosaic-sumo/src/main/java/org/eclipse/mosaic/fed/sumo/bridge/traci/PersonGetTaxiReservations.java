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
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandRetrievePersonState;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.ListTraciReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.TaxiReservationTraciReader;
import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PersonGetTaxiReservations extends AbstractTraciCommand<List<TaxiReservation>>
        implements org.eclipse.mosaic.fed.sumo.bridge.api.PersonGetTaxiReservations {

    /**
     * Creates a new {@link PersonGetTaxiReservations} traci command, which will return all taxi reservations
     * for the requested state.
     * Access needs to be public, because command is called using Reflection.
     *
     * @see <a href="https://sumo.dlr.de/docs/TraCI/Person_Value_Retrieval.html">Person Value Retrieval</a>
     */
    @SuppressWarnings("WeakerAccess")
    public PersonGetTaxiReservations() {
        super(TraciVersion.LOWEST);

        write()
                .command(CommandRetrievePersonState.COMMAND)
                .variable(CommandRetrievePersonState.VAR_TAXI_RESERVATIONS)
                .writeString("") // command does not refer to a specific person
                .writeIntWithType(0); // 0 = All reservations

        read()
                .skipBytes(2)
                .skipString()
                .readComplex(new ListTraciReader<>(new TaxiReservationTraciReader(), true));
    }

    public List<TaxiReservation> execute(Bridge bridge) throws CommandException, InternalFederateException {
        return executeAndReturn(bridge).orElseThrow(() -> new CommandException("Could not return taxi reservations."));
    }

    @Override
    protected List<TaxiReservation> constructResult(Status status, Object... objects) {
        List<?> intermediateResult = (List<?>) objects[0];
        List<TaxiReservation> result = new ArrayList<>();
        for (Object element : intermediateResult) {
            // testing all elements for proper types
            if (element instanceof TaxiReservation taxiReservation) {
                result.add(taxiReservation);
            }
        }
        return result;
    }
}
