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
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.List;

/**
 * This class represents the SUMO command which allows getting all taxis for the requested state.
 */
public interface VehicleGetTaxiFleet {
	/**
	 * This method executes the command with the given arguments in order to retrieve taxis based on a state.
	 * States:
	 * <ul>
	 *     <li>-1: all</li>
	 *     <li>0: empty</li>
	 *     <li>1: pickup</li>
	 *     <li>2: occupied</li>
	 *     <li>3: pickup+occupied</li>
	 * </ul>
	 * <p>
	 * Note: vehicles that are in state pickup+occupied (due to ride-sharing) will also be returned when requesting state 1 or 2.
	 * </p>
	 * @param bridge  Connection to SUMO.
	 * @param taxiState state of the taxi vehicle.
	 * @return a list of taxis based on the given state.
	 * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
	 * @throws InternalFederateException if some serious error occurs during writing or reading. The connection to SUMO is shut down.
	 */
	List<String> execute(Bridge bridge, int taxiState) throws CommandException, InternalFederateException;
}
