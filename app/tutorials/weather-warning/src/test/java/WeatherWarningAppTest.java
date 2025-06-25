/*
 * Copyright (c) 2021 Fraunhofer FOKUS and others. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.mosaic.app.tutorial.WeatherWarningApp;
import org.eclipse.mosaic.fed.application.ambassador.simulation.VehicleParameters;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModule;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.ambassador.simulation.navigation.RoutingNavigationModule;
import org.eclipse.mosaic.fed.application.ambassador.util.UnitLogger;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.fed.application.app.api.perception.BasicSensorModule;
import org.eclipse.mosaic.lib.enums.EnvironmentEventCause;
import org.eclipse.mosaic.lib.geo.GeoArea;
import org.eclipse.mosaic.lib.objects.addressing.AdHocMessageRoutingBuilder;
import org.eclipse.mosaic.lib.objects.addressing.SourceAddressContainer;
import org.eclipse.mosaic.lib.objects.environment.Sensor;
import org.eclipse.mosaic.lib.objects.road.IConnection;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xReceiverInformation;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Denm;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleRoute;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.routing.RoutingResponse;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class WeatherWarningAppTest {
    @Mock
    private VehicleOperatingSystem operatingSystem;

    @Mock
    private UnitLogger log;

    @Mock
    private VehicleData vehicleData;

    @Mock
    private RoutingNavigationModule navigationModuleMock;

    @Mock
    private AdHocModule adHocModuleMock;

    @Mock
    private BasicSensorModule sensorModuleMock;

    @Before
    public void setup() {
        when(operatingSystem.getAdHocModule()).thenReturn(adHocModuleMock);
        when(operatingSystem.getBasicSensorModule()).thenReturn(sensorModuleMock);

        VehicleParameters.VehicleParametersChangeRequest vehicleParametersChangeRequestMock =
                mock(VehicleParameters.VehicleParametersChangeRequest.class);
        when(operatingSystem.requestVehicleParametersUpdate()).thenReturn(vehicleParametersChangeRequestMock);
        when(vehicleParametersChangeRequestMock.changeColor(any())).thenReturn(vehicleParametersChangeRequestMock);

    }

    @Test
    public void test_SendDenmIfSensorDetectsIce() {
        final WeatherWarningApp app = new WeatherWarningApp();
        // SETUP
        app.setUp(operatingSystem, log);

        // set sensor data
        setSensor(Sensor.ICE, 10);
        // setup position of vehicle to inflict sending of V2xMessage
        setupVehiclePosition();
        // setup message routing
        setupMessageRouting();

        // RUN
        app.onVehicleUpdated(vehicleData, vehicleData);

        // ASSERT
        verify(operatingSystem.getAdHocModule()).sendV2xMessage(
                argThat(v2xMessage -> v2xMessage instanceof Denm && assertDenm((Denm) v2xMessage))
        );
    }

    @Test
    public void test_SwitchRouteIfDenmWasReceived() {
        final WeatherWarningApp app = new WeatherWarningApp();
        // SETUP
        app.setUp(operatingSystem, log);

        // setup DENM and message routing in order to inflict route change
        Denm denmMock = setupDenm();
        // setup navigation module and routing to inflict route change
        setUpRoutingFunctionality();
        // RUN
        app.onMessageReceived(new ReceivedV2xMessage(denmMock, new V2xReceiverInformation(0)));

        // ASSERT
        verify(operatingSystem.getNavigationModule()).switchRoute(any());
    }

    private void setSensor(Sensor<Integer> sensor, int value) {
        when(operatingSystem.getBasicSensorModule().getSensorValue(same(sensor))).thenReturn(Optional.of(value));
    }

    private void setupMessageRouting() {
        AdHocMessageRoutingBuilder adHocMessageRoutingBuilderMock = mock(AdHocMessageRoutingBuilder.class);
        when(adHocModuleMock.createMessageRouting()).thenReturn(adHocMessageRoutingBuilderMock);
        MessageRouting messageRoutingMock = mock(MessageRouting.class);
        when(adHocMessageRoutingBuilderMock.broadcast()).thenReturn(adHocMessageRoutingBuilderMock);
        when(adHocMessageRoutingBuilderMock.geographical(any(GeoArea.class))).thenReturn(adHocMessageRoutingBuilderMock);
        when(adHocMessageRoutingBuilderMock.build()).thenReturn(messageRoutingMock);
    }

    private void setupVehiclePosition() {
        IRoadPosition roadPositionMock = mock(IRoadPosition.class);
        IConnection connectionMock = mock(IConnection.class);
        when(vehicleData.getRoadPosition()).thenReturn(roadPositionMock);
        when(roadPositionMock.getConnection()).thenReturn(connectionMock);
        when(connectionMock.getId()).thenReturn("0_1_2_3_0");
        when(operatingSystem.getVehicleData()).thenReturn(vehicleData);
    }

    private Denm setupDenm() {
        Denm denmMock = mock(Denm.class);
        MessageRouting messageRoutingMock = mock(MessageRouting.class);
        when(denmMock.getRouting()).thenReturn(messageRoutingMock);
        SourceAddressContainer sourceAddressContainerMock = mock(SourceAddressContainer.class);
        when(messageRoutingMock.getSource()).thenReturn(sourceAddressContainerMock);
        when(sourceAddressContainerMock.getSourceName()).thenReturn("mock_0");
        when(denmMock.getEventRoadId()).thenReturn("0_1_2_3_0");
        return denmMock;
    }

    private void setUpRoutingFunctionality() {
        when(operatingSystem.getNavigationModule()).thenReturn(navigationModuleMock);
        VehicleRoute vehicleRouteMock = mock(VehicleRoute.class);
        when(navigationModuleMock.getCurrentRoute()).thenReturn(vehicleRouteMock);
        when(vehicleRouteMock.getConnectionIds()).thenReturn(Lists.newArrayList("0_1_2_3_0"));
        RoutingResponse routingResponseMock = mock(RoutingResponse.class);
        when(navigationModuleMock.calculateRoutes(any(RoutingPosition.class), any())).thenReturn(routingResponseMock);
        CandidateRoute candidateRouteMock = mock(CandidateRoute.class);
        when(routingResponseMock.getBestRoute()).thenReturn(candidateRouteMock);
    }

    private boolean assertDenm(Denm denm) {
        assertEquals(25 / 3.6f, denm.getCausedSpeed(), 0.1f);
        assertEquals(EnvironmentEventCause.ADVERSE_WEATHER_CONDITION, denm.getEventCause());
        return true;
    }

}
