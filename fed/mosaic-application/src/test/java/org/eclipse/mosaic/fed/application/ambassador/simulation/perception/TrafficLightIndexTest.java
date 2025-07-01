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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernelRule;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.TrafficLightObject;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.providers.TrafficLightIndex;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.CartesianRectangle;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.MutableCartesianPoint;
import org.eclipse.mosaic.lib.junit.GeoProjectionRule;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLight;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroupInfo;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightProgram;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TrafficLightIndexTest {

    private TrafficLightIndex trafficLightIndex;

    private final CentralPerceptionComponent cpcMock = mock(CentralPerceptionComponent.class);

    @Rule
    public SimulationKernelRule simulationKernelRule = new SimulationKernelRule(null, null, null, cpcMock);

    @Rule
    public GeoProjectionRule projectionRule = new GeoProjectionRule(GeoPoint.latLon(52.5, 13.4));

    @Before
    public void before() {
        int BUCKET_SIZE = 20;
        trafficLightIndex = new TrafficLightIndex(BUCKET_SIZE);
        when(cpcMock.getScenarioBounds())
                .thenReturn(new CartesianRectangle(new MutableCartesianPoint(0, 0, 0), new MutableCartesianPoint(1000, 1000, 0)));
    }

    private void addTrafficLightsHelper(CartesianPoint... positions) {
        HashMap<String, TrafficLightProgram> trafficLightProgramsMocks = new HashMap<>();
        TrafficLightProgram trafficLightProgramMock = mock(TrafficLightProgram.class);
        trafficLightProgramsMocks.put("0", trafficLightProgramMock);
        List<TrafficLight> trafficLightMocks = new ArrayList<>();
        for (CartesianPoint position : positions) {
            TrafficLight trafficLightMock = mock(TrafficLight.class);
            when(trafficLightMock.getPosition()).thenReturn(position.toGeo());
            when(trafficLightMock.getCurrentState()).thenReturn(TrafficLightState.GREEN);
            when(trafficLightMock.getIncomingLane()).thenReturn("E0_0");
            when(trafficLightMock.getOutgoingLane()).thenReturn("E1_0");
            when(trafficLightMock.getIndex()).thenReturn((int) position.getZ()); // on purpose
            trafficLightMocks.add(trafficLightMock);
        }
        TrafficLightGroup trafficLightGroup = new TrafficLightGroup("tls", trafficLightProgramsMocks, trafficLightMocks);
        trafficLightIndex.addTrafficLight(trafficLightGroup);
    }

    @Test
    public void testAddTrafficLight() {
        addTrafficLightsHelper(new MutableCartesianPoint(110, 100, 1));
        assertEquals(1, trafficLightIndex.getNumberOfTrafficLights());

        addTrafficLightsHelper(new MutableCartesianPoint(220, 200, 2));
        assertEquals(2, trafficLightIndex.getNumberOfTrafficLights());

        // position out of ScenarioBounds
        addTrafficLightsHelper(new MutableCartesianPoint(1001, 200, 2));
        assertEquals(2, trafficLightIndex.getNumberOfTrafficLights());
    }

    @Test
    public void testSearchTrafficLight() {
        addTrafficLightsHelper(new MutableCartesianPoint(0, 100, 1));
        addTrafficLightsHelper(new MutableCartesianPoint(0, 200, 2));

        assertEquals(0,
                trafficLightIndex.getTrafficLightsInCircle(
                        new GeoCircle(new Vector3d(0,0,0).toGeo(), 100)
                        ).size());
        assertEquals(1,
                trafficLightIndex.getTrafficLightsInCircle(
                        new GeoCircle(new Vector3d(0,0,0).toGeo(), 100.1)
                        ).size());
        assertEquals(1,
                trafficLightIndex.getTrafficLightsInCircle(
                        new GeoCircle(new Vector3d(0,0,0).toGeo(), 200)
                        ).size());
        assertEquals(2,
                trafficLightIndex.getTrafficLightsInCircle(
                        new GeoCircle(new Vector3d(0,0,0).toGeo(), 200.1)
                        ).size());
    }

    @Test
    public void testUpdateTrafficLightViaAdd() {

        // add
        TrafficLight tl = new TrafficLight(
                1248,
                new MutableCartesianPoint(0, 100, 1).toGeo(),
                "E0_0",
                "E1_0",
                TrafficLightState.GREEN
        );
        HashMap<String, TrafficLightProgram> trafficLightProgramsMocks = new HashMap<>();
        TrafficLightProgram trafficLightProgramMock = mock(TrafficLightProgram.class);
        trafficLightProgramsMocks.put("0", trafficLightProgramMock);
        List<TrafficLight> trafficLights = new ArrayList<>();
        trafficLights.add(tl);
        TrafficLightGroup tlg = new TrafficLightGroup("tlg_1", trafficLightProgramsMocks, trafficLights);
        trafficLightIndex.addTrafficLight(tlg);

        // check
        TrafficLightObject result = trafficLightIndex.getTrafficLightsInCircle(
                new GeoCircle(new Vector3d(0, 0, 0).toGeo(), 100.1)
        ).get(0);
        assertEquals(TrafficLightState.GREEN, result.getTrafficLightState());

        // adding same TL but with red
        tl.setCurrentState(TrafficLightState.RED);
        trafficLightIndex.addTrafficLight(tlg);
        result = trafficLightIndex.getTrafficLightsInCircle(
                new GeoCircle(new Vector3d(0, 0, 0).toGeo(), 100.1)
        ).get(0);
        assertEquals(TrafficLightState.RED, result.getTrafficLightState());

    }

    @Test
    public void testUpdateTrafficLight() {

        // add green TL
        TrafficLight tl = new TrafficLight(
                0, // id here used as index later
                new MutableCartesianPoint(0, 100, 1).toGeo(),
                "E0_0",
                "E1_0",
                TrafficLightState.GREEN
        );
        HashMap<String, TrafficLightProgram> trafficLightProgramsMocks = new HashMap<>();
        TrafficLightProgram trafficLightProgramMock = mock(TrafficLightProgram.class);
        trafficLightProgramsMocks.put("0", trafficLightProgramMock);
        List<TrafficLight> trafficLights = new ArrayList<>();
        trafficLights.add(tl);
        TrafficLightGroup tlg = new TrafficLightGroup("tlg_1", trafficLightProgramsMocks, trafficLights);
        trafficLightIndex.addTrafficLight(tlg);

        // check
        TrafficLightObject result = trafficLightIndex.getTrafficLightsInCircle(
                new GeoCircle(new Vector3d(0, 0, 0).toGeo(), 100.1)
        ).get(0);
        assertEquals(TrafficLightState.GREEN, result.getTrafficLightState());

        // update to red TL
        tl.setCurrentState(TrafficLightState.RED);
        HashMap<String, TrafficLightGroupInfo> tlgim = new HashMap<>();
        List<TrafficLightState> trafficLightStates = new ArrayList<>();
        trafficLightStates.add(new TrafficLightState(true, false, false));
        TrafficLightGroupInfo tlgi = new TrafficLightGroupInfo(
                "tlg_1",
                "currentProgramId",
                0,
                0L,
                trafficLightStates
                );
        tlgim.put(tlgi.getGroupId(), tlgi);
        trafficLightIndex.updateTrafficLights(tlgim);

        // check
        result = trafficLightIndex.getTrafficLightsInCircle(
                new GeoCircle(new Vector3d(0, 0, 0).toGeo(), 100.1)
        ).get(0);
        assertEquals(TrafficLightState.RED, result.getTrafficLightState());
    }
}
