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

package org.eclipse.mosaic.fed.sumo.bridge.api;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.List;

/**
 * This class represents the SUMO command which dispatches a taxi for the given reservations.
 */
public interface VehicleDispatchTaxi {
    
    /**
     * This method dispatches the taxi with the given id to service the given reservations.<br>
     * If only a single reservation is given, this implies pickup and drop-off.
     * If multiple reservations are given, each reservation id must occur twice
     * (once for pickup and once for drop-off) and the list encodes ride-sharing
     * of passengers (in pickup and drop-off order).
     *
     * @param bridge       Connection to SUMO.
     * @param vehicleId    id of the taxi vehicle.
     * @param reservations list of reservations that has to be served by the taxi.
     * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
     * @throws InternalFederateException if some serious error occurs during writing or reading. The connection to SUMO is shut down.
     */
    void execute(Bridge bridge, String vehicleId, List<String> reservations) throws CommandException, InternalFederateException;
}
