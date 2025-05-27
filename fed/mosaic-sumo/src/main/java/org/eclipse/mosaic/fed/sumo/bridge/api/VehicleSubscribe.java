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

/**
 * This class represents the SUMO command which allows to subscribe the vehicle to the application.
 * Several options for vehicle subscription are implemented in this class.
 */
public interface VehicleSubscribe {

    /**
     * Number of next stops to fetch via subscription. (0 == unlimited)
     */
    int FETCH_NUM_NEXT_STOPS = 0;

    /**
     * This method executes the command with the given arguments in order to subscribe the vehicle to the application.
     *
     * @param bridge    Connection to SUMO.
     * @param vehicleId The Id of the Vehicle.
     * @param startTime The time to subscribe the vehicle.
     * @param endTime   The end time of the subscription of the vehicle in the application.
     * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
     * @throws InternalFederateException if some serious error occurs during writing or reading. The connection to SUMO is shut down.
     */
    void execute(Bridge bridge, String vehicleId, long startTime, long endTime) throws CommandException, InternalFederateException;
}
