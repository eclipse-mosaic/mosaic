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

package org.eclipse.mosaic.lib.objects.trafficlight;

import static org.junit.Assert.assertEquals;

import org.eclipse.mosaic.lib.geo.GeoPoint;

import org.junit.Before;
import org.junit.Test;


public class TrafficLightTest{

    TrafficLight trafficLight;

    @Before
    public void setup() {
        trafficLight = new TrafficLight(0,
                GeoPoint.latLon(0.0,0.0),
                "32935480_21677261_21668930_21677261_0",
                "32935480_21668930_27537748_21668930_0",
                new TrafficLightState(true, false, false));
    }

    @Test
    public void getId() {
        assertEquals("Traffic light id didn't match the expected one", 0, trafficLight.getIndex());
    }

    @Test
    public void getGeoPosition() {
        assertEquals("Traffic light geo position didn't match the expected one", GeoPoint.latLon(0.0,0.0), trafficLight.getPosition());
    }

    @Test
    public void getControlledLanes() {
        assertEquals("Incoming lane of a traffic light didn't match the expected one", "32935480_21677261_21668930_21677261_0", trafficLight.getIncomingLane());
        assertEquals("Outgoing lane of a traffic light didn't match the expected one", "32935480_21668930_27537748_21668930_0", trafficLight.getOutgoingLane());
    }

    @Test
    public void getCurrentState() {
        TrafficLightState newState = new TrafficLightState(true, false, false);
        assertEquals("Current state of a traffic light didn't match the expected one", newState.toString(), trafficLight.getCurrentState().toString());
    }

    @Test
    public void setCurrentState() {
        TrafficLightState newState = new TrafficLightState(false, true, false);
        trafficLight.setCurrentState(newState);
        assertEquals("Current state of a traffic light didn't match the expected one", newState, trafficLight.getCurrentState());
    }

}
