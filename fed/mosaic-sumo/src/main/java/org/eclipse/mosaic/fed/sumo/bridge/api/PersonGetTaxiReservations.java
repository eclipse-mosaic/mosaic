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

package org.eclipse.mosaic.fed.sumo.bridge.api;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.List;

/**
 * This class represents the SUMO command which gets the available taxi reservations for the given state.
 */
public interface PersonGetTaxiReservations {
	/**
	 * This method gets the available taxi reservations for the requested state.
	 * <p>
	 * The following arguments for reservationState are supported:
	 * 	<ul>
	 * 		<li>0: return all reservations regardless of state</li>
	 * 		<li>1: return only new reservations</li>
	 * 		<li>2: return reservations already retrieved</li>
	 * 		<li>4: return reservations that have been assigned to a taxi</li>
	 * 		<li>8: return reservations that have been picked up</li>
	 *	</ul>
	 * </p>
	 * Combinations of these values are also supported.
	 * For example, sending a value of 3 (= 1 + 2) will return all reservations of both states 1 and 2.
	 *
	 * @param bridge  Connection to SUMO.
	 * @param reservationState the state of the reservations to return.
	 * @return list of the taxi reservations in the requested state
	 * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
	 * @throws InternalFederateException if some serious error occurs during writing or reading. The connection to SUMO is shut down.
	 */
	List<TaxiReservation> execute(Bridge bridge, int reservationState) throws CommandException, InternalFederateException;
}
