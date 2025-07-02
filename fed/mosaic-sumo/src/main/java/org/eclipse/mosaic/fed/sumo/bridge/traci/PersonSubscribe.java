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

import static org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandRetrievePersonState.VAR_ANGLE;
import static org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandRetrievePersonState.VAR_POSITION_3D;
import static org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandRetrievePersonState.VAR_SPEED;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.fed.sumo.bridge.SumoVersion;
import org.eclipse.mosaic.fed.sumo.bridge.TraciVersion;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.Status;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandVariableSubscriptions;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.SumoVar;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * This class represents the SUMO command which allows to subscribe the vehicle to the application.
 * Several options for vehicle subscription are implemented in this class.
 */
public class PersonSubscribe
        extends AbstractTraciCommand<Void>
        implements org.eclipse.mosaic.fed.sumo.bridge.api.PersonSubscribe {

    /**
     * Creates a new {@link PersonSubscribe} object.
     */
    public PersonSubscribe() {
        this(List.of(VAR_SPEED, VAR_ANGLE, VAR_POSITION_3D));
    }

    /**
     * Creates a new {@link PersonSubscribe} object.
     * Access needs to be public, because command is called using Reflection.
     *
     * @param subscriptionCodes The parameters for an applicable configuration.
     */
    @SuppressWarnings("WeakerAccess")
    public PersonSubscribe(Collection<SumoVar> subscriptionCodes) {
        super(TraciVersion.LOWEST);

        final int subscriptionSize = subscriptionCodes.size();

        TraciCommandWriterBuilder write = write()
                .command(CommandVariableSubscriptions.COMMAND_SUBSCRIBE_PERSON_VALUES)
                .writeDoubleParam() // start time
                .writeDoubleParam() // end time
                .writePersonIdParam()
                .writeByte(subscriptionSize);

        for (SumoVar subscriptionVar : subscriptionCodes) {
            write.writeByte(subscriptionVar.var);
        }

        read()
                .expectByte(CommandVariableSubscriptions.RESPONSE_SUBSCRIBE_PERSON_VALUES)
                .skipString()
                .expectByte(subscriptionSize)
                .skipRemaining();
    }

    /**
     * This method executes the command with the given arguments in order to subscribe the person to the application.
     *
     * @param bridge    Connection to SUMO.
     * @param personId  The Id of the person.
     * @param startTime The time to subscribe the person.
     * @param endTime   The end time of the subscription of the person in the application.
     * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
     * @throws InternalFederateException if some serious error occurs during writing or reading. The TraCI connection is shut down.
     */
    public void execute(Bridge bridge, String personId, long startTime, long endTime) throws CommandException, InternalFederateException {
        super.execute(bridge, ((double) startTime) / TIME.SECOND, ((double) endTime) / TIME.SECOND, personId);
    }

    @Override
    protected Void constructResult(Status status, Object... objects) {
        return null;
    }
}
