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

package org.eclipse.mosaic.fed.sumo.bridge.traci;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.fed.sumo.bridge.TraciVersion;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.Status;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandRetrievePersonState;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.Locale;

/**
 * This class represents the SUMO command which allows to get the Id of the person type.
 */
public class PersonGetTypeId
        extends AbstractTraciCommand<String>
        implements org.eclipse.mosaic.fed.sumo.bridge.api.PersonGetTypeId {

    /**
     * Creates a new {@link PersonGetTypeId} traci command, which
     * will return the person type id for the given person once executed.
     */
    public PersonGetTypeId() {
        super(TraciVersion.LOWEST);

        write()
                .command(CommandRetrievePersonState.COMMAND)
                .variable(CommandRetrievePersonState.VAR_TYPE_ID)
                .writePersonIdParam();

        read()
                .skipBytes(2)
                .skipString()
                .readStringWithType();
    }

    @Override
    public String execute(Bridge bridge, String personId) throws CommandException, InternalFederateException {
        return executeAndReturn(bridge, personId).orElseThrow(
                () -> new CommandException(
                        String.format(Locale.ENGLISH, "Could not read TypeId for person: %s.", personId)
                )
        );
    }

    @Override
    protected String constructResult(Status status, Object... objects) {
        return (String) objects[0];
    }
}
