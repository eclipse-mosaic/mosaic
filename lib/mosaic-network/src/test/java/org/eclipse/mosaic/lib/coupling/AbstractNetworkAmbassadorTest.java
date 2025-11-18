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

package org.eclipse.mosaic.lib.coupling;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.mosaic.interactions.communication.AdHocCommunicationConfiguration;
import org.eclipse.mosaic.interactions.mapping.RsuRegistration;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.AddNode.NodeType;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.CommandMessage.CommandType;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.UtmPoint;
import org.eclipse.mosaic.lib.geo.UtmZone;
import org.eclipse.mosaic.lib.junit.GeoProjectionRule;
import org.eclipse.mosaic.lib.objects.communication.AdHocConfiguration;
import org.eclipse.mosaic.lib.objects.communication.InterfaceConfiguration;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.api.parameters.FederateDescriptor;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

@RunWith(MockitoJUnitRunner.class)
public class AbstractNetworkAmbassadorTest {

    @Mock
    public ClientServerChannel ambassadorFederateChannelMock;

    @Rule
    public GeoProjectionRule transformationRule = new GeoProjectionRule(
            UtmPoint.eastNorth(UtmZone.from(GeoPoint.lonLat(13, 52)), 385281.94, 5817994.50)
    );

    @Mock
    public ClientServerChannel federateAmbassadorChannelMock;

    private AbstractNetworkAmbassador networkAmbassador;

    @Before
    public void setup() throws IOException {
        AmbassadorParameter ambassadorParameter = new AmbassadorParameter("testAmbassador", null);
        networkAmbassador = new AbstractNetworkAmbassador(ambassadorParameter, "Test Ambassador", "Test Federate") {
            @Override
            public void connectToFederate(String host, int port) {
                this.ambassadorFederateChannel = ambassadorFederateChannelMock;
                this.federateAmbassadorChannel = federateAmbassadorChannelMock;
            }

        };
        networkAmbassador.connectToFederate(null, -1);
        FederateDescriptor descriptor = mock(FederateDescriptor.class);
        networkAmbassador.setFederateDescriptor(descriptor);

        when(ambassadorFederateChannelMock.writeInitBody(anyLong(), anyLong(), anyBoolean())).thenReturn(CommandType.SUCCESS);
        when(ambassadorFederateChannelMock.writeAddNodeMessage(
                anyLong(),
                eq(NodeType.RADIO_NODE),
                isA(ClientServerChannel.NodeDataContainer.class))
        ).thenReturn(CommandType.SUCCESS);
        when(ambassadorFederateChannelMock.writeConfigureWifiRadio(
                anyLong(),
                anyInt(),
                anyInt(),
                isA(AdHocConfiguration.class))
        ).thenReturn(CommandType.SUCCESS);
    }

    private static Inet4Address createDummyIp() {
        try {
            return (Inet4Address) InetAddress.getByAddress(new byte[]{0, 0, 0, 0});
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @Test
    public void initialize_success() throws Exception {
        // Run
        networkAmbassador.initialize(0, 1000);

        // Assert
        verify(ambassadorFederateChannelMock, times(1)).writeInitBody(eq(0L), eq(1000L), eq(false));
    }

    @Test
    public void vehicleMoved_noConfigurationMessageSent() throws Exception {
        // Setup
        networkAmbassador.initialize(0, 1000);

        // Run
        final Interaction vehicleUpdates = new VehicleUpdates(
                2 * TIME.SECOND,
                Lists.newArrayList(createVehicleInfo("veh_0")),
                Lists.newArrayList(),
                Lists.newArrayList()
        );
        networkAmbassador.processInteraction(vehicleUpdates);

        // Assert
        // even though processMessage(VehicleUpdates message) has been called,
        // they can only be called when an ad hoc configuration is configured, which we haven't done yet
        verify(ambassadorFederateChannelMock, never()).writeAddNodeMessage(anyLong(), eq(NodeType.RADIO_NODE), isA(ClientServerChannel.NodeDataContainer.class));
        verify(ambassadorFederateChannelMock, never()).writeConfigureWifiRadio(anyLong(), anyInt(), anyInt(), isA(AdHocConfiguration.class));
    }

    @Test
    public void vehicleMovedConfigured_configurationMessageSent() throws Exception {
        // Setup
        networkAmbassador.initialize(0, 1000);

        // Run

        final Interaction vehicleUpdates = new VehicleUpdates(
                2 * TIME.SECOND,
                Lists.newArrayList(createVehicleInfo("veh_0")),
                Lists.newArrayList(),
                Lists.newArrayList()
        );
        networkAmbassador.processInteraction(vehicleUpdates); // Move vehicle

        final AdHocConfiguration adHocConfiguration = new AdHocConfiguration.Builder("veh_0")
                .addInterface(new InterfaceConfiguration.Builder(AdHocChannel.CCH).power(50d).ip(createDummyIp()).subnet(createDummyIp()).create())
                .create();
        final Interaction adHocCommunicationConfiguration = new AdHocCommunicationConfiguration(2 * TIME.SECOND, adHocConfiguration);
        // Configure vehicle -> Add vehicle to simulation, send configuration to federate
        networkAmbassador.processInteraction(adHocCommunicationConfiguration);

        // Assert
        verify(ambassadorFederateChannelMock, times(1)).writeAddNodeMessage(eq(2 * TIME.SECOND), eq(NodeType.RADIO_NODE), isA(ClientServerChannel.NodeDataContainer.class));
        verify(ambassadorFederateChannelMock, times(1)).writeConfigureWifiRadio(eq(2 * TIME.SECOND), anyInt(), anyInt(), eq(adHocConfiguration));
    }

    @Test
    public void vehicleConfiguredMoved_configurationMessageSent() throws Exception {
        // Setup
        networkAmbassador.initialize(0, 1000);

        // Run
        final AdHocConfiguration adHocConfiguration = new AdHocConfiguration.Builder("veh_0")
                .addInterface(new InterfaceConfiguration.Builder(AdHocChannel.CCH).power(50d).ip(createDummyIp()).subnet(createDummyIp()).create())
                .create();
        final Interaction adHocCommunicationConfiguration = new AdHocCommunicationConfiguration(2 * TIME.SECOND, adHocConfiguration);
        // Configure vehicle -> Add vehicle to simulation, send configuration to federate
        networkAmbassador.processInteraction(adHocCommunicationConfiguration);

        final Interaction vehicleUpdates = new VehicleUpdates(
                2 * TIME.SECOND,
                Lists.newArrayList(createVehicleInfo("veh_0")),
                Lists.newArrayList(),
                Lists.newArrayList()
        );
        networkAmbassador.processInteraction(vehicleUpdates); // Move vehicle

        // Assert
        verify(ambassadorFederateChannelMock, times(1)).writeAddNodeMessage(eq(2 * TIME.SECOND), eq(NodeType.RADIO_NODE), isA(ClientServerChannel.NodeDataContainer.class));
        verify(ambassadorFederateChannelMock, times(1)).writeConfigureWifiRadio(eq(2 * TIME.SECOND), anyInt(), anyInt(), eq(adHocConfiguration));
    }

    @Test
    public void rsuAdded_noConfigurationMessageSent() throws Exception {
        // Setup
        networkAmbassador.initialize(0, 1000);

        // Run
        final Interaction rsuRegistration = new RsuRegistration(
                1 * TIME.SECOND,
                "rsu_0",
                "rsu",
                Lists.newArrayList(),
                GeoPoint.latLon(52.0, 13.5)
        );
        networkAmbassador.processInteraction(rsuRegistration);

        // Assert
        verify(ambassadorFederateChannelMock, never()).writeAddNodeMessage(anyLong(), eq(NodeType.RADIO_NODE), isA(ClientServerChannel.NodeDataContainer.class));
        verify(ambassadorFederateChannelMock, never()).writeConfigureWifiRadio(anyLong(), anyInt(), anyInt(), isA(AdHocConfiguration.class));
    }

    @Test
    public void rsuAddedAndConfigured_configurationMessageSent() throws Exception {
        // Setup
        networkAmbassador.initialize(0, 1000);

        // Run
        final Interaction rsuRegistration = new RsuRegistration(1 * TIME.SECOND, "rsu_0", "rsu", Lists.newArrayList(), GeoPoint.latLon(52.0, 13.5));
        networkAmbassador.processInteraction(rsuRegistration);

        final AdHocConfiguration adHocConfiguration = new AdHocConfiguration.Builder("rsu_0")
                .addInterface(new InterfaceConfiguration.Builder(AdHocChannel.CCH).power(50d).ip(createDummyIp()).subnet(createDummyIp()).create())
                .create();
        final Interaction adHocCommunicationConfiguration = new AdHocCommunicationConfiguration(2 * TIME.SECOND, adHocConfiguration);
        // Configure rsu adhoc -> Add rsu to simulation, send configuration to federate
        networkAmbassador.processInteraction(adHocCommunicationConfiguration);

        // Assert
        verify(ambassadorFederateChannelMock, times(1)).writeAddNodeMessage(eq(2 * TIME.SECOND), eq(NodeType.RADIO_NODE), isA(ClientServerChannel.NodeDataContainer.class));
        verify(ambassadorFederateChannelMock, times(1)).writeConfigureWifiRadio(eq(2 * TIME.SECOND), anyInt(), anyInt(), eq(adHocConfiguration));
    }

    private VehicleData createVehicleInfo(String string) {
        VehicleData vehInfo = mock(VehicleData.class);
        when(vehInfo.getName()).thenReturn(string);
        when(vehInfo.getProjectedPosition()).thenReturn(CartesianPoint.xy(10, 20));
        return vehInfo;
    }

}
