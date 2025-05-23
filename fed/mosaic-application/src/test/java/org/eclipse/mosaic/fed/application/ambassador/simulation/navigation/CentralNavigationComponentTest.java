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

package org.eclipse.mosaic.fed.application.ambassador.simulation.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernelRule;
import org.eclipse.mosaic.fed.application.config.CApplicationAmbassador;
import org.eclipse.mosaic.interactions.traffic.VehicleRoutesInitialization;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteRegistration;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.road.IConnection;
import org.eclipse.mosaic.lib.objects.road.INode;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleRoute;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.routing.IllegalRouteException;
import org.eclipse.mosaic.lib.routing.RoutingParameters;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.routing.RoutingRequest;
import org.eclipse.mosaic.lib.routing.VehicleRouting;
import org.eclipse.mosaic.lib.routing.config.CPublicTransportRouting;
import org.eclipse.mosaic.lib.routing.config.CVehicleRouting;
import org.eclipse.mosaic.lib.routing.norouting.NoRouting;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.RtiAmbassador;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CentralNavigationComponentTest {

    private final TemporaryFolder folderRule = new TemporaryFolder();
    private final CentralNavigationComponentTestRule navigationRule = new CentralNavigationComponentTestRule(folderRule);
    private final TestRule lazySimKernelRule = (base, description) -> new Statement() {
        @Override
        public void evaluate() {
            // we must initialize the sim-kernel rule only when navigation rule has been already initialized
            // therefore we use this wrapping TestRule which inits sim-kernel rule on the fly
            new SimulationKernelRule(
                    null, null, navigationRule.getCentralNavigationComponent(), null
            ).apply(base, description);
        }
    };

    @Rule // chain both junit rules in a specific order
    public RuleChain testRules = RuleChain.outerRule(folderRule).around(navigationRule).around(lazySimKernelRule);

    private CentralNavigationComponent cnc;

    private VehicleRouting routingMock;
    private RtiAmbassador rtiAmbassadorMock;

    private static VehicleRoute createExampleRoute0() {
        final List<String> nodes = Arrays.asList("1", "2", "3", "4");
        final List<String> edges = Arrays.asList("1_1_2_1", "2_2_4_2", "2_2_4_3");
        return new VehicleRoute("0", edges, nodes, 100);
    }

    private static VehicleRoute createExampleRoute1() {
        final List<String> nodes = Arrays.asList("1", "2", "3");
        final List<String> edges = Arrays.asList("1_1_2_1", "2_2_4_2");
        return new VehicleRoute("1", edges, nodes, 100);
    }

    private static VehicleRoute createExampleRoute2() {
        final List<String> nodes = Arrays.asList("1", "2", "5");
        final List<String> edges = Arrays.asList("1_1_2_1", "3_2_5_2");
        return new VehicleRoute("2", edges, nodes, 100);
    }

    @Before
    public void setup() throws IllegalRouteException {
        routingMock = navigationRule.getRoutingMock();
        cnc = navigationRule.getCentralNavigationComponent();
        rtiAmbassadorMock = navigationRule.getRtiAmbassadorMock();

        final Map<String, VehicleRoute> routeMap = new HashMap<>();
        final VehicleRoute exampleRoute0 = createExampleRoute0();
        final VehicleRoute exampleRoute1 = createExampleRoute1();
        routeMap.put(exampleRoute0.getId(), exampleRoute0);
        routeMap.put(exampleRoute1.getId(), exampleRoute1);
        when(routingMock.getRoutesFromDatabaseForMessage()).thenReturn(routeMap);

        final VehicleRoute exampleRoute2 = createExampleRoute2();
        when(routingMock.createRouteForRTI(argThat(
                argument -> argument.getConnectionIds().equals(exampleRoute2.getConnectionIds())
        ))).thenReturn(exampleRoute2);

        when(routingMock.refineRoute(isA(VehicleRoute.class))).thenAnswer(c -> c.getArguments()[0]);
    }

    @Test
    public void initialize_vehicleRoutesInitializationSent() throws InternalFederateException, IllegalValueException {
        // RUN
        cnc.initialize(rtiAmbassadorMock);

        //ASSERT
        verify(routingMock).initialize(isA(CVehicleRouting.class), isA(File.class));
        verify(rtiAmbassadorMock).triggerInteraction(isA(VehicleRoutesInitialization.class));
    }

    @Test
    public void getTargetPositionOfRoute() throws InternalFederateException {
        //PREPARE
        cnc.initialize(rtiAmbassadorMock);
        doReturn(createConnectionMock(GeoPoint.lonLat(1, 1))).when(routingMock).getConnection(eq("1_1_2_1"));
        doReturn(createConnectionMock(GeoPoint.lonLat(2, 2))).when(routingMock).getConnection(eq("3_2_5_2"));
        doReturn(createConnectionMock(GeoPoint.lonLat(3, 3))).when(routingMock).getConnection(eq("2_2_4_2"));
        doReturn(createConnectionMock(GeoPoint.lonLat(4, 4))).when(routingMock).getConnection(eq("2_2_4_3"));

        // RUN + ASSERT (Last node of route "0" = 4)
        final GeoPoint gpEndOf0 = cnc.getTargetPositionOfRoute("0");
        assertNotNull(gpEndOf0);
        assertEquals(4, gpEndOf0.getLatitude(), 0.1d);
        assertEquals(4, gpEndOf0.getLongitude(), 0.1d);

        // RUN + ASSERT (Last node of route "1" = 3)
        final GeoPoint gpEndOf1 = cnc.getTargetPositionOfRoute("1");
        assertNotNull(gpEndOf1);
        assertEquals(3, gpEndOf1.getLatitude(), 0.1d);
        assertEquals(3, gpEndOf1.getLongitude(), 0.1d);

        // RUN + ASSERT (Route "2" yet unknown)
        final GeoPoint gpEndOf2 = cnc.getTargetPositionOfRoute("2");
        assertNull(gpEndOf2);
    }

    private IConnection createConnectionMock(GeoPoint endPoint) {
        IConnection connection = mock(IConnection.class);
        when(connection.getEndNode()).thenReturn(mock(INode.class));
        when(connection.getEndNode().getPosition()).thenReturn(endPoint);
        return connection;
    }

    @Test
    public void findRoutes_requestRoutesOnly_noRouteChangeInitiated() throws InternalFederateException, IllegalValueException {
        // PREPARE
        cnc.initialize(rtiAmbassadorMock);
        RoutingRequest routingRequest = new RoutingRequest(
                new RoutingPosition(GeoPoint.latLon(0, 0)),
                new RoutingPosition(GeoPoint.latLon(0, 0)),
                new RoutingParameters()
        );

        // RUN
        cnc.findRoutes(routingRequest);

        verify(routingMock).findRoutes(same(routingRequest));
        verify(rtiAmbassadorMock, never()).triggerInteraction(isA(VehicleRouteRegistration.class));
        verify(rtiAmbassadorMock, never()).triggerInteraction(isA(VehicleRouteChange.class));
    }

    @Test
    public void switchRoute_alreadyOnRoute() throws InternalFederateException, IllegalValueException, IllegalRouteException {
        // PREPARE
        cnc.initialize(rtiAmbassadorMock);

        VehicleData vehicleData = mock(VehicleData.class);
        when(vehicleData.getRouteId()).thenReturn("0");
        final CandidateRoute candidateRoute = new CandidateRoute(Arrays.asList("1_1_2_1", "2_2_4_2", "2_2_4_3"), 0, 0);
        VehicleRoute currentRoute = routingMock.getRoutesFromDatabaseForMessage().get(vehicleData.getRouteId());

        // RUN
        VehicleRoute result = cnc.switchRoute(vehicleData, candidateRoute, currentRoute, 0 * TIME.SECOND);

        // ASSERT
        assertSame(currentRoute, result);
        verify(rtiAmbassadorMock, never()).triggerInteraction(isA(VehicleRouteRegistration.class));
        verify(rtiAmbassadorMock, never()).triggerInteraction(isA(VehicleRouteChange.class));
    }

    @Test
    public void switchRoute_routeExisting_switch() throws InternalFederateException, IllegalValueException, IllegalRouteException {
        // PREPARE
        cnc.initialize(rtiAmbassadorMock);

        VehicleData vehicleData = mock(VehicleData.class);
        when(vehicleData.getRouteId()).thenReturn("1");
        final CandidateRoute candidateRoute = new CandidateRoute(Arrays.asList("1_1_2_1", "2_2_4_2", "2_2_4_3"), 0, 0);

        // RUN
        VehicleRoute result = cnc.switchRoute(vehicleData, candidateRoute, mock(VehicleRoute.class), 0 * TIME.SECOND);

        // ASSERT
        assertNotEquals(vehicleData.getRouteId(), result.getId());
        verify(rtiAmbassadorMock, never()).triggerInteraction(isA(VehicleRouteRegistration.class));
        verify(rtiAmbassadorMock, times(1)).triggerInteraction(isA(VehicleRouteChange.class));
    }

    @Test
    public void switchRoute_routeNotExisting_propagateAndSwitch() throws InternalFederateException, IllegalValueException, IllegalRouteException {
        // PREPARE
        cnc.initialize(rtiAmbassadorMock);

        VehicleData vehicleData = mock(VehicleData.class);
        when(vehicleData.getRouteId()).thenReturn("1");
        final CandidateRoute candidateRoute = new CandidateRoute(Arrays.asList("1_1_2_1", "3_2_5_2"), 0, 0);

        // RUN
        VehicleRoute result = cnc.switchRoute(vehicleData, candidateRoute, mock(VehicleRoute.class), 0 * TIME.SECOND);

        // ASSERT
        assertNotEquals(vehicleData.getRouteId(), result.getId());
        verify(rtiAmbassadorMock, times(1)).triggerInteraction(isA(VehicleRouteRegistration.class));
        verify(rtiAmbassadorMock, times(1)).triggerInteraction(isA(VehicleRouteChange.class));
    }

    @Test
    public void initializeNoRouting() throws InternalFederateException, IOException {

        AmbassadorParameter ambassadorParameter = new AmbassadorParameter(
                "test", folderRule.newFile("config.json")
        );

        CApplicationAmbassador.CRoutingByType routingConfig = new CApplicationAmbassador.CRoutingByType();
        routingConfig.type = "no-routing";

        CentralNavigationComponent centralNavigationComponent
                = new CentralNavigationComponent(ambassadorParameter, routingConfig, new CPublicTransportRouting());
        centralNavigationComponent.initialize(rtiAmbassadorMock);

        assertNotNull(centralNavigationComponent.getRouting());
        assertTrue(centralNavigationComponent.getRouting() instanceof NoRouting);
    }

    @Test
    public void initializeMyTestRouting() throws InternalFederateException, IOException {

        AmbassadorParameter ambassadorParameter = new AmbassadorParameter(
                "test", folderRule.newFile("config.json")
        );

        CApplicationAmbassador.CRoutingByType routingConfig = new CApplicationAmbassador.CRoutingByType();
        routingConfig.type = MyTestRouting.class.getCanonicalName();

        CentralNavigationComponent centralNavigationComponent
                = new CentralNavigationComponent(ambassadorParameter, routingConfig, new CPublicTransportRouting());
        centralNavigationComponent.initialize(rtiAmbassadorMock);

        assertNotNull(centralNavigationComponent.getRouting());
        assertTrue(centralNavigationComponent.getRouting() instanceof MyTestRouting);
    }

}
