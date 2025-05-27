/*
 * Copyright (c) 2022 Fraunhofer FOKUS and others. All rights reserved.
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
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandRetrieveTrafficLightValue;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.TraciDatatypes;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.ListTraciReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.StringTraciReader;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.List;
import java.util.Locale;

/**
 * This class represents the SUMO command which allows to get the list of junctions controlled by a traffic light.
 */
public class TrafficLightGetControlledJunctions
        extends AbstractTraciCommand<List<String>>
        implements org.eclipse.mosaic.fed.sumo.bridge.api.TrafficLightGetControlledJunctions {

    public TrafficLightGetControlledJunctions() {
        super(TraciVersion.LOWEST);

        write()
                .command(CommandRetrieveTrafficLightValue.COMMAND)
                .variable(CommandRetrieveTrafficLightValue.VAR_CONTROLLED_JUNCTIONS)
                .writeStringParam();

        read()
                .skipBytes(2)
                .skipString()
                .expectByte(TraciDatatypes.STRING_LIST)
                .readComplex(new ListTraciReader<>(new StringTraciReader()));
    }

    /**
     * This method executes the command with the given arguments in order to get the controlled junctions of a traffic light.
     *
     * @param bridge Connection to Traci.
     * @param tlId   Id of the traffic light.
     * @return List of the junctions controlled by the given traffic light
     * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
     * @throws InternalFederateException if some serious error occurs during writing or reading. The TraCI connection is shut down.
     */
    public List<String> execute(Bridge bridge, String tlId) throws CommandException, InternalFederateException {
        return super.executeAndReturn(bridge, tlId).orElseThrow(
                () -> new CommandException(String.format(
                        Locale.ENGLISH, "Could not read list of controlled Junctions for TrafficLight %s.", tlId)
                )
        );
    }

    @Override
    protected List<String> constructResult(Status status, Object... objects) {
        return (List<String>) objects[0];
    }
}
