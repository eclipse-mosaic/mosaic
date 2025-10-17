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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.application.ambassador.SimulationKernelRule;
import org.eclipse.mosaic.fed.application.ambassador.simulation.VehicleUnit;
import org.eclipse.mosaic.fed.application.ambassador.simulation.navigation.CentralNavigationComponent;
import org.eclipse.mosaic.fed.application.config.CApplicationAmbassador;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.CartesianRectangle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.MutableCartesianPoint;
import org.eclipse.mosaic.lib.junit.GeoProjectionRule;
import org.eclipse.mosaic.lib.junit.IpResolverRule;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLight;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightProgram;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;
import org.eclipse.mosaic.lib.perception.PerceptionConfiguration;
import org.eclipse.mosaic.lib.perception.PerceptionEgo;
import org.eclipse.mosaic.lib.perception.PerceptionIndex;
import org.eclipse.mosaic.lib.perception.index.TrafficLightIndex;
import org.eclipse.mosaic.lib.perception.index.VehicleGrid;
import org.eclipse.mosaic.lib.perception.index.VehicleIndex;
import org.eclipse.mosaic.lib.perception.index.VehicleTree;
import org.eclipse.mosaic.lib.perception.objects.VehicleObject;
import org.eclipse.mosaic.lib.util.scheduling.EventManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

@RunWith(Parameterized.class)
public class SimplePerceptionModuleTest {

    private final String vehicleIndexType;
    private final EventManager eventManagerMock = mock(EventManager.class);
    private final CentralPerceptionComponent cpcMock = mock(CentralPerceptionComponent.class);
    private final CentralNavigationComponent cncMock = mock(CentralNavigationComponent.class);

    @Rule
    public MockitoRule initRule = MockitoJUnit.rule();

    @Rule
    public GeoProjectionRule projectionRule = new GeoProjectionRule(GeoPoint.latLon(52, 13));
    @Mock
    public VehicleData egoVehicleData;

    @Rule
    public SimulationKernelRule simulationKernelRule = new SimulationKernelRule(eventManagerMock, null, cncMock, cpcMock);

    @Rule
    public IpResolverRule ipResolverRule = new IpResolverRule();

    public PerceptionIndex perceptionIndex;

    private SimplePerceptionModule simplePerceptionModule;

    public SimplePerceptionModuleTest(String vehicleIndexType) {
        this.vehicleIndexType = vehicleIndexType;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"grid"}, {"tree"}
        });
    }

    @Before
    public void setup() {
        CartesianRectangle bounds = new CartesianRectangle(new MutableCartesianPoint(100, 90, 0), new MutableCartesianPoint(310, 115, 0));
        SimulationKernel.SimulationKernel.setConfiguration(new CApplicationAmbassador());

        VehicleIndex vehicleIndex = switch (vehicleIndexType) {
            case "tree" -> new VehicleTree(20, 12);
            case "grid" -> new VehicleGrid(5, 5);
            default -> null;
        };

        perceptionIndex = new PerceptionIndex.Builder(mock((Logger.class)), bounds)
                .withVehicleIndex(vehicleIndex)
                .withTrafficLightIndex(new TrafficLightIndex(20))
                .build();

        // setup cpc
        when(cpcMock.getPerceptionIndex()).thenReturn(perceptionIndex);
        // setup perception module
        VehicleUnit egoVehicleUnit = spy(new VehicleUnit("veh_0", mock(VehicleType.class), null));
        doReturn(egoVehicleData).when(egoVehicleUnit).getVehicleData();
        PerceptionEgo ego = new VehicleUnit.PerceptionEgoVehicleAdapter(egoVehicleUnit);
        simplePerceptionModule = spy(new SimplePerceptionModule(ego, null, mock(Logger.class)));
        simplePerceptionModule.enable(new PerceptionConfiguration.Builder(90d, 200d).build());

        // setup ego vehicle
        when(egoVehicleData.getHeading()).thenReturn(90d);
        when(egoVehicleData.getProjectedPosition()).thenReturn(new MutableCartesianPoint(100, 100, 0));
    }

    @Test
    public void vehicleCanBePerceived() {
        setupVehicles(new MutableCartesianPoint(110, 100, 0));
        assertEquals(1, simplePerceptionModule.getPerceivedVehicles().size());
    }

    @Test
    public void vehicleCanBePerceived_includesDimensions() {
        setupVehicles(new MutableCartesianPoint(110, 100, 0));
        List<VehicleObject> perceivedVehicles = simplePerceptionModule.getPerceivedVehicles();
        assertEquals(1, perceivedVehicles.size());
        VehicleObject perceivedVehicle = perceivedVehicles.get(0);
        assertEquals(5d, perceivedVehicle.getLength(), 0.01);
        assertEquals(2.5d, perceivedVehicle.getWidth(), 0.01);
        assertEquals(10d, perceivedVehicle.getHeight(), 0.01);
    }

    @Test
    public void vehicleCannotBePerceived_outOfRange() {
        setupVehicles(new MutableCartesianPoint(310, 100, 0));
        assertEquals(0, simplePerceptionModule.getPerceivedVehicles().size());
    }

    @Test
    public void vehicleCanBePerceived_OnLeftBoundVector() {
        setupVehicles(new MutableCartesianPoint(110, 110, 0));
        assertEquals(1, simplePerceptionModule.getPerceivedVehicles().size());
    }

    @Test
    public void vehicleCanBePerceived_OnRightBoundVector() {
        setupVehicles(new MutableCartesianPoint(110, 90, 0));
        assertEquals(1, simplePerceptionModule.getPerceivedVehicles().size());
    }

    @Test
    public void vehicleCannotBePerceived_tooFarLeft() {
        setupVehicles(new MutableCartesianPoint(105, 115, 0));
        assertEquals(0, simplePerceptionModule.getPerceivedVehicles().size());
    }

    @Test
    public void vehicleCannotBePerceived_tooFarRight() {
        setupVehicles(new MutableCartesianPoint(105, 90, 0));
        assertEquals(0, simplePerceptionModule.getPerceivedVehicles().size());
    }

    @Test
    public void vehicleCanBePerceived_FarLeft_270viewingAngle() {
        simplePerceptionModule.enable(new PerceptionConfiguration.Builder(270d, 200d).build()); // overwrite config
        setupVehicles(new MutableCartesianPoint(105, 115, 0));
        assertEquals(1, simplePerceptionModule.getPerceivedVehicles().size());
    }

    @Test
    public void vehicleCanBePerceived_FarRight_270viewingAngle() {
        simplePerceptionModule.enable(new PerceptionConfiguration.Builder(270d, 200d).build()); // overwrite config
        setupVehicles(new MutableCartesianPoint(105, 90, 0));
        assertEquals(1, simplePerceptionModule.getPerceivedVehicles().size());
    }

    @Test
    public void vehicleCanBeRemoved() {
        simplePerceptionModule.enable(new PerceptionConfiguration.Builder(360d, 1000d).build());
        List<VehicleData> addedVehicles = setupVehicles(
                new MutableCartesianPoint(105, 90, 0),
                new MutableCartesianPoint(105, 90, 0),
                new MutableCartesianPoint(105, 90, 0)
        );
        // assert all vehicles have been added
        assertEquals(3, perceptionIndex.getNumberOfVehicles());
        assertEquals(3, simplePerceptionModule.getPerceivedVehicles().size());
        // modify vehicle objects
        List<VehicleData> adjustedVehicles = addedVehicles.stream()
                .map(vehicleData ->
                        new VehicleData.Builder(vehicleData.getTime(), vehicleData.getName())
                                .copyFrom(vehicleData)
                                .movement(1d, 1d, 1d) // adjust vehicles
                                .create())
                .toList();
        perceptionIndex.updateVehicles(adjustedVehicles);
        // try to remove vehicles
        perceptionIndex.removeVehicles(addedVehicles.stream().map(VehicleData::getName).toList());
        // assert vehicles have been properly removed
        assertEquals(0, perceptionIndex.getNumberOfVehicles());
        assertTrue(simplePerceptionModule.getPerceivedVehicles().isEmpty());
    }

    @Test
    public void trafficLightsCanBePerceived() {
        setupTrafficLights(new MutableCartesianPoint(110, 100, 0));

        assertEquals(1, simplePerceptionModule.getTrafficLightsInRange().size());
        assertEquals(TrafficLightState.GREEN, simplePerceptionModule.getTrafficLightsInRange().get(0).getTrafficLightState());
    }

    private List<VehicleData> setupVehicles(CartesianPoint... positions) {
        List<VehicleData> vehiclesInIndex = new ArrayList<>();
        int i = 1;
        for (CartesianPoint position : positions) {
            String vehicleName = "veh_" + i++;
            VehicleData vehicleDataMock = mock(VehicleData.class);
            when(vehicleDataMock.getProjectedPosition()).thenReturn(position);
            when(vehicleDataMock.getName()).thenReturn(vehicleName);
            vehiclesInIndex.add(vehicleDataMock);

            VehicleType vehicleType = mock(VehicleType.class);
            when(vehicleType.getLength()).thenReturn(5d);
            when(vehicleType.getWidth()).thenReturn(2.5d);
            when(vehicleType.getHeight()).thenReturn(10d);
            perceptionIndex.registerVehicleType(vehicleName, vehicleType);
        }
        perceptionIndex.updateVehicles(vehiclesInIndex);
        return vehiclesInIndex;
    }

    private void setupTrafficLights(CartesianPoint... positions) {
        HashMap<String, TrafficLightProgram> trafficLightProgramsMocks = new HashMap<>();
        TrafficLightProgram trafficLightProgramMock = mock(TrafficLightProgram.class);
        trafficLightProgramsMocks.put("0", trafficLightProgramMock);
        List<TrafficLight> trafficLightMocks = new ArrayList<>();
        int i = 0;
        for (CartesianPoint position : positions) {
            TrafficLight trafficLightMock = mock(TrafficLight.class);
            when(trafficLightMock.getPosition()).thenReturn(position.toGeo());
            when(trafficLightMock.getCurrentState()).thenReturn(TrafficLightState.GREEN);
            when(trafficLightMock.getIncomingLane()).thenReturn("E0_0");
            when(trafficLightMock.getOutgoingLane()).thenReturn("E1_0");
            when(trafficLightMock.getIndex()).thenReturn(i++);
            trafficLightMocks.add(trafficLightMock);
        }
        TrafficLightGroup trafficLightGroup = new TrafficLightGroup("tls", trafficLightProgramsMocks, trafficLightMocks);
        perceptionIndex.addTrafficLightGroup(trafficLightGroup);
    }
}
