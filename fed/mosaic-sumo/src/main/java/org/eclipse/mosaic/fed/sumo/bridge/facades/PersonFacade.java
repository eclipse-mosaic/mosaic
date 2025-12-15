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

package org.eclipse.mosaic.fed.sumo.bridge.facades;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.fed.sumo.bridge.api.PersonGetTypeId;
import org.eclipse.mosaic.fed.sumo.bridge.api.PersonGetTaxiReservations;
import org.eclipse.mosaic.lib.objects.fleet.RideReservation;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.List;

public class PersonFacade {

    private final Bridge bridge;
    private final PersonGetTypeId personGetTypeId;
    private final PersonGetTaxiReservations personGetTaxiReservations;

    /**
     * Constructor with TraCI connection.
     *
     * @param bridge connection for communicating with TraCI.
     */
    public PersonFacade(Bridge bridge) {
        this.bridge = bridge;

        this.personGetTypeId = bridge.getCommandRegister().getOrCreate(PersonGetTypeId.class);
        this.personGetTaxiReservations = bridge.getCommandRegister().getOrCreate(PersonGetTaxiReservations.class);
    }

    public String getPersonTypeId(String personId) throws InternalFederateException {
        try {
            return personGetTypeId.execute(bridge, personId);
        } catch (IllegalArgumentException | CommandException e) {
            throw new InternalFederateException("Could not request type for person " + personId, e);
        }
    }

    /**
     * This method returns the available taxi reservations.
     */
    public List<RideReservation> getTaxiReservations() throws InternalFederateException {
        try {
            return personGetTaxiReservations.execute(bridge);
        } catch (CommandException e) {
            throw new InternalFederateException("Could not retrieve taxi reservations.", e);
        }
    }
}
