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

package org.eclipse.mosaic.fed.sumo.ambassador;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import org.eclipse.mosaic.rti.TIME;

import org.junit.Test;

public class SumoVehicleActionsHandlerTest {

    private final static long sumoTimeStep = TIME.SECOND;

    @Test
    public void testEqualTimeStep() {
        // Requested time is the same as the sumo timestep. Shouldn't be changed
        assertEquals(sumoTimeStep, SumoVehicleActionsHandler.adjustToSumoTimeStep(TIME.SECOND, sumoTimeStep));
    }

    @Test
    public void testRoundDown() {
        // Just 2 over, should round down
        assertEquals(sumoTimeStep, SumoVehicleActionsHandler.adjustToSumoTimeStep(TIME.SECOND + 2 * TIME.MILLI_SECOND, sumoTimeStep));
    }

    @Test
    public void testRoundUp() {
        // Closer to the next higher value, should round up
        assertEquals(sumoTimeStep * 2, SumoVehicleActionsHandler.adjustToSumoTimeStep(TIME.SECOND + 999 * TIME.MILLI_SECOND, sumoTimeStep));
    }

    @Test
    public void testTooSmall() {
        // Requested event time is smaller than the half of the sumo step (closer to previous step value - 0 in this case),
        // so it is being reduced to 0 and then brought to sumo time step value
        assertEquals(sumoTimeStep, SumoVehicleActionsHandler.adjustToSumoTimeStep(sumoTimeStep / 3, sumoTimeStep));
    }
}
