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

package org.eclipse.mosaic.fed.sumo.bridge.traci.reader;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.PersonSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandRetrievePersonState;
import org.eclipse.mosaic.lib.util.objects.Position;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersonSubscriptionTraciReader extends AbstractSubscriptionTraciReader<PersonSubscriptionResult> {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    PersonSubscriptionResult createSubscriptionResult(String id) {
        PersonSubscriptionResult result = new PersonSubscriptionResult();
        result.id = Bridge.PERSON_ID_TRANSFORMER.fromExternalId(id);
        return result;
    }

    /**
     * This method enables to handle the subscription variable of the person.
     *
     * @param result   The result of the person.
     * @param varId    The Id of the variable.
     * @param varValue The value of the variable.
     */
    @Override
    protected void handleSubscriptionVariable(PersonSubscriptionResult result, int varId, Object varValue) {
        if (varId == CommandRetrievePersonState.VAR_SPEED.var) {
            result.speed = (double) varValue;
        } else if (varId == CommandRetrievePersonState.VAR_POSITION.var) {
            result.position = (Position) varValue;
        } else if (varId == CommandRetrievePersonState.VAR_POSITION_3D.var) {
            result.position = (Position) varValue;
        } else if (varId == CommandRetrievePersonState.VAR_ANGLE.var) {
            result.heading = (double) varValue;
        } else {
            log.warn("Unknown subscription variable {}. Skipping.", String.format("%02X ", varId));
        }
    }
}
