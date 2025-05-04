/*
 * Copyright (c) 2021 Fraunhofer FOKUS and others. All rights reserved.
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
import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.sumo.libsumo.Person;
import org.eclipse.sumo.libsumo.TraCIReservation;
import org.eclipse.sumo.libsumo.TraCIReservationVector;

import java.util.ArrayList;
import java.util.List;

public class PersonGetTaxiReservations implements org.eclipse.mosaic.fed.sumo.bridge.api.PersonGetTaxiReservations {

	@Override
	public List<TaxiReservation> execute(Bridge bridge, int reservationState)
		throws CommandException, InternalFederateException {
		TraCIReservationVector traCIReservations = Person.getTaxiReservations(reservationState);

		List<TaxiReservation> taxiReservations = new ArrayList<>();

		for (TraCIReservation res : traCIReservations) {
			taxiReservations.add(new TaxiReservation.Builder()
					.withId(res.getId())
					.withPersonList(res.getPersons())
					.withGroup(res.getGroup())
					.withFromEdge(res.getFromEdge())
					.withToEdge(res.getToEdge())
					.withDepartPos(res.getDepartPos())
					.withArrivalPos(res.getArrivalPos())
					.withDepart(res.getDepart())
					.withReservationTime(res.getReservationTime())
					.build());
		}

		return taxiReservations;
	}
}
