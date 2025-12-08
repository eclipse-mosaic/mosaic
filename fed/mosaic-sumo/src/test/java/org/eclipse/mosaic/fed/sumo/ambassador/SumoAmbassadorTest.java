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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.mosaic.fed.sumo.bridge.TraciClientBridge;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.TraciSimulationStepResult;
import org.eclipse.mosaic.fed.sumo.bridge.facades.RouteFacade;
import org.eclipse.mosaic.fed.sumo.bridge.facades.SimulationFacade;
import org.eclipse.mosaic.fed.sumo.bridge.facades.TrafficLightFacade;
import org.eclipse.mosaic.fed.sumo.bridge.facades.VehicleFacade;
import org.eclipse.mosaic.interactions.agent.AgentUpdates;
import org.eclipse.mosaic.interactions.mapping.VehicleRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioTrafficLightRegistration;
import org.eclipse.mosaic.interactions.traffic.TaxiUpdates;
import org.eclipse.mosaic.interactions.traffic.TrafficDetectorUpdates;
import org.eclipse.mosaic.interactions.traffic.TrafficLightUpdates;
import org.eclipse.mosaic.interactions.traffic.VehicleRoutesInitialization;
import org.eclipse.mosaic.interactions.traffic.VehicleTypesInitialization;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteRegistration;
import org.eclipse.mosaic.interactions.vehicle.VehicleSpeedChange;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleDeparture;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleRoute;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;
import org.eclipse.mosaic.lib.util.junit.TestFileRule;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.RtiAmbassador;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.api.parameters.FederateDescriptor;
import org.eclipse.mosaic.rti.api.parameters.FederatePriority;
import org.eclipse.mosaic.rti.config.CLocalHost;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tests for {@link SumoAmbassador}.
 */
public class SumoAmbassadorTest {

    private final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final TestFileRule testFileRule = new TestFileRule(temporaryFolder)
            .basedir("sumo")
            .with("testScenario.net.xml", "/sumo-test-scenario/scenario.net.xml")
            .with("testScenario.rou.xml", "/sumo-test-scenario/scenario.rou.xml")
            .with("testScenario.sumocfg", "/sumo-test-scenario/scenario.sumocfg");

    @Rule
    public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(testFileRule);

    private TraciClientBridge traciClientBridgeMock;
    private RtiAmbassador rtiMock;

    private SumoAmbassador ambassador;

    @Before
    public void setup() throws IOException {
        rtiMock = mock(RtiAmbassador.class);
        FederateDescriptor handleMock = mock(FederateDescriptor.class);
        File workingDir = temporaryFolder.getRoot();

        CLocalHost testHostConfig = new CLocalHost();
        testHostConfig.workingDirectory = workingDir.getAbsolutePath();
        when(handleMock.getHost()).thenReturn(testHostConfig);
        when(handleMock.getId()).thenReturn("sumo");
        when(handleMock.getPriority()).thenReturn(FederatePriority.DEFAULT);

        traciClientBridgeMock = null;
        ambassador = new SumoAmbassador(new AmbassadorParameter("sumo", temporaryFolder.newFile("sumo/sumo_config.json"))) {
            @Override
            protected void initSumoConnection() throws InternalFederateException {

                if (this.bridge == null) {
                    this.bridge = createTraciClientMock();
                    this.socket = mock(Socket.class);
                }
            }
        };

        ambassador.setRtiAmbassador(rtiMock);
        ambassador.setFederateDescriptor(handleMock);
    }

    @Test
    public void initialize_doNotInitTraci() throws Throwable {
        assertNull(traciClientBridgeMock);

        // RUN
        ambassador.initialize(0, 1000 * TIME.SECOND);

        // ASSERT
        verify(rtiMock, times(1))
                .requestAdvanceTime(eq(0L), eq(0L), eq((byte) (FederatePriority.DEFAULT - 1)));
        assertNull(traciClientBridgeMock);
    }

    @Test
    public void sendVehiclePathsAndTypes_doInitTraci() throws Throwable {
        initialize_doNotInitTraci();

        // RUN
        final Map<String, VehicleRoute> routes = new HashMap<>();
        routes.put("0", new VehicleRoute("0", Lists.newArrayList("a", "b", "c"), Lists.newArrayList(), 0));
        Interaction vehicleRoutesInitialization = new VehicleRoutesInitialization(0L, routes);
        ambassador.processInteraction(vehicleRoutesInitialization);

        // ASSERT
        // because SumoAmbassador.cachedVehicleTypesInitialization was "null" at the moment when we called
        // processMessage(VehicleRoutesInitialization) but as we called it, we saved the reference to the
        // VehicleRoutesInitialization in the cachedVehicleTypesInitialization variable, so at the next
        // assertion traci will already be initialised (at the time of calling processMessage(VehicleTypesInitialization)
        assertNull(traciClientBridgeMock);

        // RUN
        final Map<String, VehicleType> types = new HashMap<>();
        types.put("default", new VehicleType("default"));
        Interaction vehicleTypesInitialization = new VehicleTypesInitialization(0L, types);
        ambassador.processInteraction(vehicleTypesInitialization);

        // ASSERT
        assertNotNull(traciClientBridgeMock);
    }

    @Test
    public void simulationStep() throws Throwable {
        sendVehiclePathsAndTypes_doInitTraci();
        // there is one route registration for the initially read route "0"
        verify(rtiMock, times(1)).triggerInteraction(isA(VehicleRouteRegistration.class));
        clearInvocations(rtiMock);
        // SETUP
        VehicleData veh0 = new VehicleData.Builder(0L, "veh_0").route("0").create();
        mockSimulationStepResult(0L, veh0);

        // RUN
        ambassador.advanceTime(0L);

        // ASSERT
        verify(rtiMock, times(1)).requestAdvanceTime(eq(TIME.SECOND), eq(0L), anyByte());
        verify(rtiMock, times(1)).triggerInteraction(isA(VehicleUpdates.class));
        verify(rtiMock, times(1)).triggerInteraction(isA(TrafficDetectorUpdates.class));
        // in this simulation step there is no new route propagated
        verify(rtiMock, never()).triggerInteraction(isA(VehicleRouteRegistration.class));
    }

    @Test
    public void finish() throws Throwable {
        sendVehiclePathsAndTypes_doInitTraci();

        // RUN
        ambassador.finishSimulation();

        // ASSERT
        verify(traciClientBridgeMock).close();
    }

    @Test
    public void receiveMessage_VehicleSpeedChangeWithInterval() throws Throwable {
        sendVehiclePathsAndTypes_doInitTraci();

        // RUN
        VehicleSpeedChange vehicleSpeedChange =
                new VehicleSpeedChange(0, "veh_0", VehicleSpeedChange.VehicleSpeedChangeType.WITH_DURATION, 10, 5 * TIME.SECOND, 0);
        ambassador.processInteraction(vehicleSpeedChange);
        mockSimulationStepResult(0L);
        ambassador.advanceTime(0L);

        // ASSERT
        verify(traciClientBridgeMock.getVehicleControl(), never()).setSpeed(anyString(), anyDouble());
        verify(traciClientBridgeMock.getVehicleControl()).slowDown(eq("veh_0"), eq(10.0), eq(5 * TIME.SECOND));

        // RUN+ASSERT: setSpeed is NOT called after 4 seconds
        ambassador.advanceTime(4 * TIME.SECOND);
        verify(traciClientBridgeMock.getVehicleControl(), never()).setSpeed(anyString(), anyDouble());

        // RUN+ASSERT: setSpeed is FINALLY called after 5 seconds interval
        ambassador.advanceTime(5 * TIME.SECOND);
        verify(traciClientBridgeMock.getVehicleControl()).setSpeed(eq("veh_0"), eq(10.0));
    }

    @Test
    public void receiveMessage_VehicleSpeedChangeAtNonMultipleOfTimeStep() throws Throwable {
        // if the adjustToSumoTimeStep doesn't get a multiple of sumoConfig.updateInterval * TIME.MILLI_SECOND, this fails
        sendVehiclePathsAndTypes_doInitTraci();
        long sendTime = 1100L;
        // RUN
        mockSimulationStepResult(0L);
        ambassador.advanceTime(sendTime); // advancing time enough to process interaction
        VehicleSpeedChange vehicleSpeedChange = new VehicleSpeedChange(sendTime, "veh_0", VehicleSpeedChange.VehicleSpeedChangeType.WITH_DURATION, 10, 5 * TIME.SECOND, 0);
        ambassador.processInteraction(vehicleSpeedChange);
        //  ASSERT
        //  -> nothing should be called yet
        verify(traciClientBridgeMock.getVehicleControl(), never()).setSpeed(anyString(), anyDouble());
        verify(traciClientBridgeMock.getVehicleControl(), never()).slowDown(eq("veh_0"), eq(10.0), eq(5 * TIME.SECOND));
        // RUN + ASSERT
        // slow down should be called at next time step
        ambassador.advanceTime(ambassador.sumoConfig.updateInterval * TIME.MILLI_SECOND);
        verify(traciClientBridgeMock.getVehicleControl()).slowDown(eq("veh_0"), eq(10.0), eq(5 * TIME.SECOND));
        // RUN
        for (int i = 1; i <= 5; i++) {
            ambassador.advanceTime(i * ambassador.sumoConfig.updateInterval * TIME.MILLI_SECOND);
        }
        // ASSERT
        // after 5 seconds setSpeed should have been called
        verify(traciClientBridgeMock.getVehicleControl()).setSpeed(eq("veh_0"), eq(10.0));
    }

    @Test
    public void addVehicleAndSimulate_subscribeToAllVehiclesTrue_vehicleIsAddedAndSubscribedFor() throws Throwable {
        testSubscribeToAllVehicles(true);
    }

    @Test
    public void addVehicleAndSimulate_subscribeToAllVehiclesFalse_vehicleIsAddedButNotSubscribed() throws Throwable {
        testSubscribeToAllVehicles(false);
    }

    private void testSubscribeToAllVehicles(boolean subscribeToAllVehicles) throws Throwable {
        // set subscribeToAllVehicles to requested value
        ambassador.sumoConfig.subscribeToAllVehicles = subscribeToAllVehicles;
        sendVehiclePathsAndTypes_doInitTraci();

        // PREPARE
        VehicleDeparture departInfo = mock(VehicleDeparture.class);
        when(departInfo.getRouteId()).thenReturn("0");
        when(departInfo.getDepartureSpeedMode()).thenReturn(VehicleDeparture.DepartureSpeedMode.MAXIMUM);
        when(departInfo.getLaneSelectionMode()).thenReturn(VehicleDeparture.LaneSelectionMode.DEFAULT);

        // RUN send added vehicle and run first step
        ambassador.processInteraction(
                new VehicleRegistration(0, "veh_0", null, Lists.newArrayList(), departInfo, new VehicleType("default"))
        );
        mockSimulationStepResult(0L);
        ambassador.advanceTime(0L);

        // ASSERT not yet added (can not add vehicles before simulating the first step)
        verify(
                traciClientBridgeMock.getSimulationControl(),
                never()).addVehicle(anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );

        // RUN second step
        long time = TIME.SECOND;
        VehicleData veh0 = new VehicleData.Builder(time, "veh_0").route("0").create();
        mockSimulationStepResult(time, veh0);
        ambassador.advanceTime(time);

        // ASSERT vehicle has now been added but not subscribed to
        verify(traciClientBridgeMock.getSimulationControl(), times(1))
                .addVehicle(eq("veh_0"), eq("0"), eq("default"), eq("0"), anyString(), eq("max"));

        if (subscribeToAllVehicles) {
            verify(traciClientBridgeMock.getSimulationControl(), times(1))
                    .subscribeForVehicle(eq("veh_0"), eq(0L), eq(1000 * TIME.SECOND));
        } else {
            verify(traciClientBridgeMock.getSimulationControl(), never())
                    .subscribeForVehicle(eq("veh_0"), eq(0L), eq(1000 * TIME.SECOND));
        }
    }

    @Test
    public void initTrafficLights() throws Throwable {
        // SETUP
        sendVehiclePathsAndTypes_doInitTraci();

        TrafficLightGroup tlg = new TrafficLightGroup("tl_0", new HashMap<>(1), Lists.newArrayList());
        when(traciClientBridgeMock.getSimulationControl().getTrafficLightGroupIds())
                .thenReturn(Lists.newArrayList(tlg.getGroupId(), "unknown"));

        TrafficLightFacade trafficLightControlMock = mock(TrafficLightFacade.class);
        when(traciClientBridgeMock.getTrafficLightControl()).thenReturn(trafficLightControlMock);
        when(trafficLightControlMock.getTrafficLightGroup(eq(tlg.getGroupId()))).thenReturn(tlg);
        when(trafficLightControlMock.getTrafficLightGroup(eq("unknown"))).thenThrow(new InternalFederateException());
        when(trafficLightControlMock.getControlledLanes(eq(tlg.getGroupId()))).thenReturn(Lists.newArrayList("edge_0", "edge_1"));

        ArgumentCaptor<Interaction> captor = ArgumentCaptor.forClass(Interaction.class);

        // RUN
        mockSimulationStepResult(0L);
        ambassador.advanceTime(0L);

        // ASSERT
        verify(rtiMock, atLeastOnce()).triggerInteraction(captor.capture());
        Optional<Interaction> registration =
                captor.getAllValues().stream().filter(m -> m instanceof ScenarioTrafficLightRegistration).findFirst();
        if (!registration.isPresent()) {
            fail();
        }
        ScenarioTrafficLightRegistration trafficLights = (ScenarioTrafficLightRegistration) registration.get();

        assertTrue(trafficLights.getTrafficLightGroups().contains(tlg));
        assertTrue(trafficLights.getLanesControlledByGroups().containsKey(tlg.getGroupId()));
        assertTrue(trafficLights.getLanesControlledByGroups().get(tlg.getGroupId()).contains("edge_0"));
        assertTrue(trafficLights.getLanesControlledByGroups().get(tlg.getGroupId()).contains("edge_1"));
    }

    private void mockSimulationStepResult(long time, VehicleData... vehicles) throws InternalFederateException {
        VehicleUpdates vehicleUpdates = new VehicleUpdates(time, Lists.newArrayList(vehicles), Lists.newArrayList(), Lists.newArrayList());
        TraciSimulationStepResult traciSimulationResult = new TraciSimulationStepResult(
                vehicleUpdates,
                new AgentUpdates(time, Lists.newArrayList(), Lists.newArrayList()),
                new TrafficDetectorUpdates(time, Lists.newArrayList(), Lists.newArrayList()),
                new TrafficLightUpdates(time, new HashMap<>()),
                new TaxiUpdates(time, Lists.newArrayList(), Lists.newArrayList())
        );
        when(traciClientBridgeMock.getSimulationControl().simulateUntil(anyLong())).thenReturn(traciSimulationResult);
    }

    private TraciClientBridge createTraciClientMock() throws InternalFederateException {
        this.traciClientBridgeMock = mock(TraciClientBridge.class);

        RouteFacade traciRouteFacade = mock(RouteFacade.class);
        when(traciClientBridgeMock.getRouteControl()).thenReturn(traciRouteFacade);

        VehicleFacade traciVehicleFacade = mock(VehicleFacade.class);
        when(traciClientBridgeMock.getVehicleControl()).thenReturn(traciVehicleFacade);

        SimulationFacade traciSimulationFacade = mock(SimulationFacade.class);
        when(traciClientBridgeMock.getSimulationControl()).thenReturn(traciSimulationFacade);

        when(traciClientBridgeMock.getRouteControl().getRouteIds()).thenReturn(Lists.newArrayList("0"));

        return this.traciClientBridgeMock;
    }

}
