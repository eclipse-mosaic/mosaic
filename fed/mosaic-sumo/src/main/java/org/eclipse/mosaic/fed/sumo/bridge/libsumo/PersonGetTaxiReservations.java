/*
 * Copyright (c) 2025 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.sumo.bridge.libsumo;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.lib.objects.fleet.RideReservation;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.eclipse.sumo.libsumo.Person;
import org.eclipse.sumo.libsumo.TraCIReservation;
import org.eclipse.sumo.libsumo.TraCIReservationVector;

import java.util.ArrayList;
import java.util.List;

public class PersonGetTaxiReservations implements org.eclipse.mosaic.fed.sumo.bridge.api.PersonGetTaxiReservations {

    @Override
    public List<RideReservation> execute(Bridge bridge)
            throws CommandException, InternalFederateException {
        TraCIReservationVector traCIReservations = Person.getTaxiReservations();

        List<RideReservation> rideReservations = new ArrayList<>();

        for (TraCIReservation res : traCIReservations) {
            rideReservations.add(new RideReservation.Builder()
                    .withId(res.getId())
                    .withState(RideReservation.State.of(res.getState()))
                    .withPersonList(res.getPersons().stream().map(Bridge.PERSON_ID_TRANSFORMER::fromExternalId).toList())
                    .withFromEdge(res.getFromEdge())
                    .withToEdge(res.getToEdge())
                    .build());
        }

        return rideReservations;
    }
}
