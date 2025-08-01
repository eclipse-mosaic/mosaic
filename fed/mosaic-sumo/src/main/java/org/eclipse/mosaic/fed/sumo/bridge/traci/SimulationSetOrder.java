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

package org.eclipse.mosaic.fed.sumo.bridge.traci;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.fed.sumo.bridge.TraciVersion;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.Status;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandSimulationControl;
import org.eclipse.mosaic.rti.api.InternalFederateException;

public class SimulationSetOrder extends AbstractTraciCommand<Void> implements org.eclipse.mosaic.fed.sumo.bridge.api.SimulationSetOrder {


    @SuppressWarnings("WeakerAccess")
    public SimulationSetOrder() {
        super(TraciVersion.LOWEST);

        write()
                .command(CommandSimulationControl.COMMAND_SET_ORDER)
                .writeIntParam();
    }

    @Override
    public void execute(Bridge bridge, int order) throws CommandException, InternalFederateException {
        super.execute(bridge, order);
    }

    @Override
    protected Void constructResult(Status status, Object... objects) {
        return null;
    }
}
