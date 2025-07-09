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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.mosaic.fed.sumo.bridge.api.complex.AbstractSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.PersonSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.junit.SumoRunner;
import org.eclipse.mosaic.rti.TIME;

import com.google.common.collect.Iterables;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(SumoRunner.class)
public class PersonSubscribeTest extends AbstractTraciCommandTest {

    @Test
    public void execute_PersonAlreadyDeparted() throws Exception {
        // PRE-ASSERT
        List<AbstractSubscriptionResult> subscriptions = simulateStep.execute(traci.getTraciConnection(), 6 * TIME.SECOND);
        assertTrue(subscriptions.isEmpty());

        // RUN
        new PersonSubscribe().execute(traci.getTraciConnection(), "p_0", 0L, 10 * TIME.SECOND);

        // ASSERT
        subscriptions = simulateStep.execute(traci.getTraciConnection(), 10 * TIME.SECOND);
        assertEquals(1, subscriptions.size());
        assertTrue(((PersonSubscriptionResult) Iterables.getOnlyElement(subscriptions)).speed > 4.0 / 3.6);

        subscriptions = simulateStep.execute(traci.getTraciConnection(), 11 * TIME.SECOND);
        assertTrue(subscriptions.isEmpty());

    }
}
