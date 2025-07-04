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

package org.eclipse.mosaic.fed.sumo.bridge.libsumo;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.rti.api.InternalFederateException;

public class PersonSubscribe implements org.eclipse.mosaic.fed.sumo.bridge.api.PersonSubscribe {
    @Override
    public void execute(Bridge bridge, String personId, long startTime, long endTime) throws CommandException, InternalFederateException {
        if (!SimulationSimulateStep.VEHICLE_SUBSCRIPTIONS.contains(personId)) {
            SimulationSimulateStep.VEHICLE_SUBSCRIPTIONS.add(personId);
        }
    }
}
