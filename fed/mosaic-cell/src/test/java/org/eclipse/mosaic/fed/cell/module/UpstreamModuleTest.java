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

package org.eclipse.mosaic.fed.cell.module;

import static org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties.GLOBAL_NETWORK_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.mosaic.fed.cell.chain.ChainManager;
import org.eclipse.mosaic.fed.cell.chain.SampleV2xMessage;
import org.eclipse.mosaic.fed.cell.config.model.TransmissionMode;
import org.eclipse.mosaic.fed.cell.data.ConfigurationData;
import org.eclipse.mosaic.fed.cell.data.SimulationData;
import org.eclipse.mosaic.fed.cell.junit.CellConfigurationRule;
import org.eclipse.mosaic.fed.cell.junit.CellSimulationRule;
import org.eclipse.mosaic.fed.cell.message.CellModuleMessage;
import org.eclipse.mosaic.fed.cell.message.StreamResult;
import org.eclipse.mosaic.fed.cell.module.streammodules.UpstreamModule;
import org.eclipse.mosaic.fed.cell.utility.NodeCapacityUtility;
import org.eclipse.mosaic.interactions.communication.V2xMessageAcknowledgement;
import org.eclipse.mosaic.lib.enums.DestinationType;
import org.eclipse.mosaic.lib.enums.NegativeAckReason;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.UtmPoint;
import org.eclipse.mosaic.lib.geo.UtmZone;
import org.eclipse.mosaic.lib.junit.GeoProjectionRule;
import org.eclipse.mosaic.lib.junit.IpResolverRule;
import org.eclipse.mosaic.lib.math.DefaultRandomNumberGenerator;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.model.delay.ConstantDelay;
import org.eclipse.mosaic.lib.objects.addressing.CellMessageRoutingBuilder;
import org.eclipse.mosaic.lib.objects.addressing.DestinationAddressContainer;
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;
import org.eclipse.mosaic.lib.objects.addressing.NetworkAddress;
import org.eclipse.mosaic.lib.objects.addressing.SourceAddressContainer;
import org.eclipse.mosaic.lib.objects.communication.CellConfiguration;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.DATA;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.RtiAmbassador;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import java.io.File;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class UpstreamModuleTest {

    private static final long HEADER_UDP = 8 * DATA.BYTE;
    private static final long HEADER_IP = 20 * DATA.BYTE;
    private static final long HEADER_TCP = 20 * DATA.BYTE;
    private static final long HEADER_CELLULAR = 18 * DATA.BYTE;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    @Mock
    private SourceAddressContainer sourceAddressContainerMock;

    @Mock
    private DestinationAddressContainer destinationAddressContainer;
    @Mock
    public RtiAmbassador rti;

    private AmbassadorParameter ambassadorParameter;

    @Rule
    public IpResolverRule ipResolverRule = new IpResolverRule();

    private final GeoProjectionRule transformationRule = new GeoProjectionRule(
            UtmPoint.eastNorth(UtmZone.from(GeoPoint.lonLat(13.3856, 52.5415)), 388405.53, 5820063.64)
    );

    private final CellConfigurationRule configRule = new CellConfigurationRule()
            .withNetworkConfig("configs/network_for_moduletest.json");

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(transformationRule).around(configRule);

    @Rule
    public CellSimulationRule simulationRule = new CellSimulationRule();

    private final static long SEED = 182931861823L;
    private final RandomNumberGenerator rng = new DefaultRandomNumberGenerator(SEED);
    private final List<CellModuleMessage> cellModuleMessages = new ArrayList<>();
    private final List<Interaction> rtiInteractionsSent = new ArrayList<>();
    private final List<Interaction> rtiV2xAcknowledgementsSent = new ArrayList<>();
    private final AtomicReference<MessageRouting> routing = new AtomicReference<>();
    private static final long DELAY_VALUE_IN_MS = 50 * TIME.MILLI_SECOND;


    private UpstreamModule upstreamModule;

    @Before
    public void setup() throws IllegalValueException, InternalFederateException {
        File ambassadorConfiguration = new File("cell_config.json");
        ambassadorParameter = new AmbassadorParameter(null, ambassadorConfiguration);
        ChainManager chainManager = new ChainManager(rti, rng, ambassadorParameter) {
            @Override
            public void finishEvent(CellModuleMessage cellModuleMessage) {
                cellModuleMessages.add(cellModuleMessage);
            }
        };

        doAnswer(
                invocationOnMock -> {
                    Interaction interaction = (Interaction) invocationOnMock.getArguments()[0];
                    rtiInteractionsSent.add(interaction);
                    if (interaction.getTypeId().equals(V2xMessageAcknowledgement.TYPE_ID)) {
                        rtiV2xAcknowledgementsSent.add((V2xMessageAcknowledgement) invocationOnMock.getArguments()[0]);
                    }
                    return null;
                }
        ).when(rti).triggerInteraction(ArgumentMatchers.isA(Interaction.class));

        upstreamModule = new UpstreamModule(chainManager);

        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);

        IpResolver.getSingleton().registerHost("veh_0");
    }

    @Test
    public void testProcessMessage_regularMessageNodeLimited() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{10, 2, 0, 0})
                .topological()
                .build());

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);
        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true, 2240 * DATA.BIT, 2240 * DATA.BIT);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, rtiInteractionsSent.size());
        assertEquals(2, cellModuleMessages.size());
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);
        checkNotifyOnFinishMessage(notifyOnFinishMessage, 2240 * DATA.BIT, 10 * TIME.SECOND, (long) (10.2 * TIME.SECOND));

        CellModuleMessage resultMessage = cellModuleMessages.get(1);
        checkResultMessage(resultMessage, 2240 * DATA.BIT, 10 * TIME.SECOND, (long) (10.2 * TIME.SECOND));

        assertEquals(0L, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals((21000 - 2240) * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeLimitedRegionLessLimited() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{10, 2, 0, 0})
                .topological()
                .build());

        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true, 2240 * DATA.BIT, 2240 * DATA.BIT);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 3000 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.maxCapacity = 3000 * DATA.BIT;

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, rtiInteractionsSent.size());
        assertEquals(2, cellModuleMessages.size());
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);
        checkNotifyOnFinishMessage(notifyOnFinishMessage, 2240 * DATA.BIT, 10 * TIME.SECOND, (long) (10.2 * TIME.SECOND));

        CellModuleMessage resultMessage = cellModuleMessages.get(1);
        checkResultMessage(resultMessage, 2240 * DATA.BIT, 10 * TIME.SECOND, (long) (10.2 * TIME.SECOND));

        assertEquals(0L, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals((3000 - 2240) * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeLimitedRegionMoreLimited() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{10, 2, 0, 0})
                .topological()
                .build());

        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true, 2240 * DATA.BIT, 2240 * DATA.BIT);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 1120 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.maxCapacity = 1120 * DATA.BIT;

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, rtiInteractionsSent.size());
        assertEquals(2, cellModuleMessages.size());
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);
        checkNotifyOnFinishMessage(notifyOnFinishMessage, 1120, 10 * TIME.SECOND, (long) (10.4 * TIME.SECOND));

        CellModuleMessage resultMessage = cellModuleMessages.get(1);
        checkResultMessage(resultMessage, 1120, 10 * TIME.SECOND, (long) (10.4 * TIME.SECOND));

        assertEquals((2240 - 1120) * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(0L, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeLimitedRegionBlocked() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);
        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true, 400 * DATA.BIT, 400 * DATA.BIT);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);
        // the maxCapacity is set to 21000, hence the region is blocked
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 600 * DATA.BIT;

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.CHANNEL_CAPACITY_EXCEEDED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(400 * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(600 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeUnlimited_plusFreeBandwidth() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{10, 2, 0, 0})
                .topological()
                .build());

        long messageLength = 10 * DATA.BYTE;
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), messageLength);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, rtiInteractionsSent.size());

        long actualMessageLength = messageLength + HEADER_UDP + HEADER_IP + HEADER_CELLULAR;
        long expectedBandwidth = (long) (actualMessageLength / (DELAY_VALUE_IN_MS / (double) TIME.SECOND));

        assertEquals(2, cellModuleMessages.size());
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);
        checkNotifyOnFinishMessage(notifyOnFinishMessage, expectedBandwidth, 10 * TIME.SECOND,
                10 * TIME.SECOND + DELAY_VALUE_IN_MS);

        CellModuleMessage resultMessage = cellModuleMessages.get(1);
        checkResultMessage(resultMessage, expectedBandwidth, 10 * TIME.SECOND,
                10 * TIME.SECOND + DELAY_VALUE_IN_MS);

        assertEquals((Long.MAX_VALUE - expectedBandwidth) * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0")
                .getAvailableUplinkBitrate());
        assertEquals((21000 - expectedBandwidth) * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());

        // FREE
        // SETUP
        Event freeEvent = new Event(notifyOnFinishMessage.getEndTime(), upstreamModule, notifyOnFinishMessage);

        // RUN
        upstreamModule.processEvent(freeEvent);

        // ASSERT
        assertEquals(Long.MAX_VALUE * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(21000 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeUnlimitedRegionLimited() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{10, 2, 0, 0})
                .topological()
                .build());

        long messageLength = 10 * DATA.BYTE;
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), messageLength);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 1000 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.maxCapacity = 1000 * DATA.BIT;

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, rtiInteractionsSent.size());

        assertEquals(2, cellModuleMessages.size());
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);

        // actual message length = 10 + UDP + IP + CELLULAR = 64 Bytes = 512 Bit
        long expectedBandwidth = 1000 * DATA.BIT;
        checkNotifyOnFinishMessage(notifyOnFinishMessage, expectedBandwidth, 10 * TIME.SECOND, (long) (10.448 * TIME.SECOND));

        CellModuleMessage resultMessage = cellModuleMessages.get(1);
        checkResultMessage(resultMessage, expectedBandwidth, 10 * TIME.SECOND,
                (long) (10.448 * TIME.SECOND));

        assertEquals(
                (Long.MAX_VALUE - expectedBandwidth) * DATA.BIT,
                SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate()
        );
        assertEquals(0L, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());

    }

    @Test
    public void testProcessMessage_packetLossTcp() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        long messageLength = 10 * DATA.BYTE;
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), messageLength);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.transmission.lossProbability = 2d;

        double delayInS = ((ConstantDelay) ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.delay).delay
                / (double) TIME.SECOND;
        long actualMessageLength = messageLength + HEADER_TCP + HEADER_IP + HEADER_CELLULAR;
        long expectedBandwidth = (long) (actualMessageLength / delayInS);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(1, cellModuleMessages.size());
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);
        checkNotifyOnFinishMessage(notifyOnFinishMessage, expectedBandwidth, 10 * TIME.SECOND,
                10 * TIME.SECOND + DELAY_VALUE_IN_MS);

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.PACKET_LOSS);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(
                (Long.MAX_VALUE - expectedBandwidth) * DATA.BIT,
                SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate()
        );
        assertEquals((21000 - expectedBandwidth) * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_packetLossUdp() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{10, 2, 0, 0})
                .topological()
                .build());

        long messageLength = 10 * DATA.BYTE;
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), messageLength);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.transmission.lossProbability = 2d;

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, rtiInteractionsSent.size());

        assertEquals(1, cellModuleMessages.size());
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);

        double delayInS = ((ConstantDelay) ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.delay).delay
                / (double) TIME.SECOND;
        long actualMessageLength = messageLength + HEADER_UDP + HEADER_IP + HEADER_CELLULAR;
        long expectedBandwidth = (long) (actualMessageLength / delayInS);
        checkNotifyOnFinishMessage(notifyOnFinishMessage, expectedBandwidth, 10 * TIME.SECOND,
                10 * TIME.SECOND + DELAY_VALUE_IN_MS);

        assertEquals(
                (Long.MAX_VALUE - expectedBandwidth) * DATA.BIT,
                SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate()
        );
        assertEquals((21000 - expectedBandwidth) * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_nodeCapacityExceededRegionUnlimited() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        Event event = new Event(10 * TIME.NANO_SECOND, upstreamModule, sampleV2XMessage);
        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0");
        cellConfiguration.consumeUplink(Long.MAX_VALUE * DATA.BIT);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.NODE_CAPACITY_EXCEEDED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(0L, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(21000 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_nodeCapacityExceededRegionLimited() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0");
        cellConfiguration.consumeUplink(Long.MAX_VALUE * DATA.BIT);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 200 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.maxCapacity = 200 * DATA.BIT;

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        Event event = new Event(10 * TIME.NANO_SECOND, upstreamModule, sampleV2XMessage);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.NODE_CAPACITY_EXCEEDED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(0L, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(200 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_channelCapacityExceededTcp() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        Event event = new Event(10 * TIME.NANO_SECOND, upstreamModule, sampleV2XMessage);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 0L;

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.CHANNEL_CAPACITY_EXCEEDED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(Long.MAX_VALUE * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(0L, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_channelNodeCapacityExceeded() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        Event event = new Event(10 * TIME.NANO_SECOND, upstreamModule, sampleV2XMessage);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 0L;

        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0");
        cellConfiguration.consumeUplink(Long.MAX_VALUE * DATA.BIT);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons =
                Arrays.asList(NegativeAckReason.CHANNEL_CAPACITY_EXCEEDED, NegativeAckReason.NODE_CAPACITY_EXCEEDED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(0L, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
        assertEquals(0L, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
    }

    @Test
    public void testProcessMessage_nodeDeactivatedTcp() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        Event event = new Event(10 * TIME.NANO_SECOND, upstreamModule, sampleV2XMessage);

        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0");
        cellConfiguration.setEnabled(false);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Arrays.asList(NegativeAckReason.NODE_DEACTIVATED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(21000 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
        assertEquals(Long.MAX_VALUE * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
    }

    @Test
    public void testProcessMessage_nodeDeactivatedUdp() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{10, 2, 0, 0})
                .topological()
                .build());

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        Event event = new Event(10 * TIME.NANO_SECOND, upstreamModule, sampleV2XMessage);

        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0");
        cellConfiguration.setEnabled(false);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());
        assertEquals(0, rtiInteractionsSent.size());
    }

    @Test
    public void testProcessMessage_nodeNotRegisteredTcp() throws InternalFederateException, UnknownHostException {
        // SETUP
        // TCP
        when(sourceAddressContainerMock.getSourceName()).thenReturn("veh_0");
        when(destinationAddressContainer.getProtocolType()).thenReturn(ProtocolType.TCP);
        Inet4Address inet4Address = (Inet4Address) Inet4Address.getByName("10.2.0.0");
        when(destinationAddressContainer.getAddress()).thenReturn(mock(NetworkAddress.class));
        when(destinationAddressContainer.getAddress().getIPv4Address()).thenReturn(inet4Address);
        when(destinationAddressContainer.getType()).thenReturn(DestinationType.CELL_TOPOCAST);

        routing.set(new MessageRouting(destinationAddressContainer, sourceAddressContainerMock));

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        Event event = new Event(10 * TIME.NANO_SECOND, upstreamModule, sampleV2XMessage);

        when(sourceAddressContainerMock.getSourceName()).thenReturn("veh_1");

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());
        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.NODE_DEACTIVATED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(21000 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
        assertEquals(Long.MAX_VALUE * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
    }

    @Test
    public void testProcessMessage_nodeNotRegisteredUdp() {
        // SETUP
        IpResolver.getSingleton().registerHost("veh_1");
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_1", null)
                .destination(new byte[]{10, 2, 0, 0})
                .topological()
                .build());

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        Event event = new Event(10 * TIME.NANO_SECOND, upstreamModule, sampleV2XMessage);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());
        assertEquals(0, rtiInteractionsSent.size());
    }

    @Test
    public void testFreeBandwidth_regularMessage() {
        // SETUP
        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true, 400 * DATA.BIT, 400 * DATA.BIT);
        NodeCapacityUtility.consumeCapacityUp(cellConfiguration, 200 * DATA.BIT);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = (21000 - 200) * DATA.BIT;

        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{1, 2, 3, 4})
                .topological()
                .build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        StreamResult streamResult = new StreamResult(
                GLOBAL_NETWORK_ID,
                400 * DATA.BIT,
                TransmissionMode.UplinkUnicast,
                "veh_0",
                sampleV2XMessage
        );
        CellModuleMessage freeMessage = new CellModuleMessage.Builder("Upstream", "Upstream").resource(streamResult).build();

        Event event = new Event(10 * TIME.NANO_SECOND, upstreamModule, freeMessage);

        // ASSERT
        assertEquals(200 * DATA.BIT, cellConfiguration.getAvailableUplinkBitrate());

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(400 * DATA.BIT, cellConfiguration.getAvailableUplinkBitrate());
        assertEquals(21000 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }



    // helper methods to analyze the messages generated by the UpstreamModule

    private void checkRtiMessages(List<NegativeAckReason> negativeAckReasons, SampleV2xMessage sampleV2XMessage) {
        assertTrue(rtiInteractionsSent.get(0) instanceof V2xMessageAcknowledgement);
        V2xMessageAcknowledgement ackV2xMessage = (V2xMessageAcknowledgement) rtiInteractionsSent.get(0);
        assertEquals(sampleV2XMessage.getId(), ackV2xMessage.getOriginatingMessageId());
        assertEquals(negativeAckReasons, ackV2xMessage.getNegativeReasons());
        assertFalse(ackV2xMessage.isAcknowledged());
    }

    private void checkResultMessage(CellModuleMessage resultMessage, long bandwidth, long startTime, long endTime) {
        assertEquals("Upstream", resultMessage.getEmittingModule());
        assertEquals("Geocaster", resultMessage.getNextModule());
        assertTrue(resultMessage.getResource() instanceof StreamResult);
        StreamResult streamResultMessage = resultMessage.getResource();
        assertNotNull(streamResultMessage.getV2xMessage());
        assertEquals(GLOBAL_NETWORK_ID, streamResultMessage.getRegionId());
        assertEquals(bandwidth, streamResultMessage.getConsumedBandwidth());
        assertEquals(startTime, resultMessage.getStartTime());
        assertEquals(endTime, resultMessage.getEndTime());
    }

    private void checkNotifyOnFinishMessage(CellModuleMessage resultMessage, long bandwidth, long startTime, long endTime) {
        assertEquals("Upstream", resultMessage.getEmittingModule());
        assertEquals("Upstream", resultMessage.getNextModule());
        assertTrue(resultMessage.getResource() instanceof StreamResult);
        StreamResult streamResultMessage = resultMessage.getResource();
        assertNotNull(streamResultMessage.getV2xMessage());
        assertEquals(GLOBAL_NETWORK_ID, streamResultMessage.getRegionId());
        assertEquals(bandwidth, streamResultMessage.getConsumedBandwidth());
        assertEquals(startTime, resultMessage.getStartTime());
        assertEquals(endTime, resultMessage.getEndTime());
    }

}
