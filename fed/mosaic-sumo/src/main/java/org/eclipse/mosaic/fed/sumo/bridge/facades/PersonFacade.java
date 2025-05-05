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

package org.eclipse.mosaic.fed.sumo.bridge.facades;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.fed.sumo.bridge.api.PersonGetTaxiReservations;
import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.List;

public class PersonFacade {

    private final Bridge bridge;

    private final PersonGetTaxiReservations personGetTaxiReservations;

    /**
     * Constructor with TraCI connection.
     *
     * @param bridge connection for communicating with TraCI.
     */
    public PersonFacade(Bridge bridge) {
        this.bridge = bridge;

        this.personGetTaxiReservations = bridge.getCommandRegister().getOrCreate(PersonGetTaxiReservations.class);
    }

    /**
     * This method gets the available taxi reservations for the requested state.
     */
    public List<TaxiReservation> getTaxiReservations(int reservationState) throws InternalFederateException {
        try {
            return personGetTaxiReservations.execute(bridge, reservationState);
        }
        catch(CommandException e) {
            throw new InternalFederateException(String.format("Could not retrieve taxi reservations for state %s", reservationState), e);
        }
    }
}
