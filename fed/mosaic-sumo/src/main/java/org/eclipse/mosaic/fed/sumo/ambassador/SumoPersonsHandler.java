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

package org.eclipse.mosaic.fed.sumo.ambassador;

import org.eclipse.mosaic.interactions.mapping.AgentRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioAgentRegistration;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.ArrayList;
import java.util.List;

/**
 * All the ambassador's logic which takes care of persons/agents.
 */
public class SumoPersonsHandler extends AbstractHandler {

    private final List<String> personsSubscriptionCache = new ArrayList<>();

    void propagatePersonsToRti(long time) throws InternalFederateException {
        List<String> persons = bridge.getSimulationControl().getDepartedPersons();
        String personType;
        for (String personId : persons) {
            personType = bridge.getPersonControl().getPersonTypeId(personId);
            try {
                rti.triggerInteraction(new ScenarioAgentRegistration(time, personId, personType));
            } catch (IllegalValueException e) {
                throw new InternalFederateException(e);
            }
            personsSubscriptionCache.add(personId);
        }
    }

    void handleRegistration(AgentRegistration agentRegistration) {
        String personId = agentRegistration.getMapping().getName();
        if (agentRegistration.getMapping().hasApplication() // only subscribe to persons with mapped applications
                // FIXME: This is a workaround as otherwise the ambassador will try to subscribe to Agents added by
                //  other simulators than SUMO, which is currently not possible
                && personsSubscriptionCache.contains(personId)) {
            try {
                bridge.getSimulationControl().subscribeForPerson(personId, agentRegistration.getTime(), ambassador.getEndTime());
            } catch (InternalFederateException e) {
                log.warn("Could not subscribe to unknown person {}", personId);
            }
            personsSubscriptionCache.remove(personId);
        }
    }
}
