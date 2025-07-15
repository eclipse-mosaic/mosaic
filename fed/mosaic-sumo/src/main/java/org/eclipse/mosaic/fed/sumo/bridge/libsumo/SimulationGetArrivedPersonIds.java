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
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.eclipse.sumo.libsumo.Simulation;
import org.eclipse.sumo.libsumo.StringVector;

import java.util.List;

public class SimulationGetArrivedPersonIds implements org.eclipse.mosaic.fed.sumo.bridge.api.SimulationGetArrivedPersonIds {
    @Override
    public List<String> execute(Bridge bridge) throws CommandException, InternalFederateException {
        final StringVector arrivedIds = Simulation.getDepartedPersonIDList();
        try {
            return arrivedIds.stream()
                    .map(Bridge.PERSON_ID_TRANSFORMER::fromExternalId)
                    .toList();
        } finally {
            arrivedIds.delete();
        }
    }
}
