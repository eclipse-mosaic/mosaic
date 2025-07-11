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
import org.eclipse.mosaic.fed.cell.data.ConfigurationData;
import org.eclipse.mosaic.fed.cell.data.SimulationData;
import org.eclipse.mosaic.fed.cell.junit.CellConfigurationRule;
import org.eclipse.mosaic.fed.cell.junit.CellSimulationRule;
import org.eclipse.mosaic.fed.cell.message.CellModuleMessage;
import org.eclipse.mosaic.fed.cell.message.StreamResult;
import org.eclipse.mosaic.fed.cell.module.streammodules.UpstreamModule;
import org.eclipse.mosaic.interactions.communication.V2xMessageAcknowledgement;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.enums.NegativeAckReason;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.UtmPoint;
import org.eclipse.mosaic.lib.geo.UtmZone;
import org.eclipse.mosaic.lib.junit.GeoProjectionRule;
import org.eclipse.mosaic.lib.junit.IpResolverRule;
import org.eclipse.mosaic.lib.math.DefaultRandomNumberGenerator;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.objects.addressing.CellMessageRoutingBuilder;
import org.eclipse.mosaic.lib.objects.addressing.DestinationAddressContainer;
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;
import org.eclipse.mosaic.lib.objects.addressing.NetworkAddress;
import org.eclipse.mosaic.lib.objects.addressing.SourceAddressContainer;
import org.eclipse.mosaic.lib.objects.communication.CellConfiguration;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.MessageStreamRouting;
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

public class UpstreamModuleStreamingTest {

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
    private static final long DELAY_VALUE = 50 * TIME.MILLI_SECOND;


    private UpstreamModule upstreamModule;


    // Try to send 8000 bits in 8 seconds, leading to a data rate of 1000 bits per second

    // event duration is given in ns
    private final static long EVENT_DURATION = 8 * TIME.SECOND;
    // message size is given in bytes
    private final static long MESSAGE_SIZE = 1000;
    // expected bandwidth is given in bits per second
    private final static long EXPECTED_BANDWIDTH = 1000 * DATA.BIT;

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
                .streaming(EVENT_DURATION, EXPECTED_BANDWIDTH)
                .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), MESSAGE_SIZE);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);
        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true, 400L, 400L);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, rtiInteractionsSent.size());
        assertEquals(0, cellModuleMessages.size());

        assertEquals(400, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(21000, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeLimitedRegionLessLimited() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .streaming(EVENT_DURATION, EXPECTED_BANDWIDTH)
                .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true, 400L, 400L);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 600 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.maxCapacity = 600 * DATA.BIT;

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), MESSAGE_SIZE);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);
        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, rtiInteractionsSent.size());
        assertEquals(0, cellModuleMessages.size());

        assertEquals(400, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(600, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeLimitedRegionMoreLimited() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .streaming(EVENT_DURATION, EXPECTED_BANDWIDTH)
                .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true, 400L, 400L);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 200 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.maxCapacity = 200 * DATA.BIT;

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), MESSAGE_SIZE);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);
        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, rtiInteractionsSent.size());
        assertEquals(0, cellModuleMessages.size());

        assertEquals(400, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(200, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeLimitedRegionWouldBeBlockedButIsSufficient() throws InternalFederateException {
        // For a stream the duration is not extended if the capacity is not sufficient. Hence, it is considered whether the
        // capacity is sufficient but not whether the region is blocked due to missing capacity.

        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .streaming(EVENT_DURATION, 10)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        int smallerStreamSize = 10;
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), smallerStreamSize);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);
        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true, 400L, 400L);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);
        // because the default maxCapacity of the region is 21000 setting the capacity to 600 bps results in a blocking of the region
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 600 * DATA.BIT;

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(2, cellModuleMessages.size());
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);
        checkNotifyOnFinishMessage(notifyOnFinishMessage, smallerStreamSize, 10 * TIME.SECOND,
                10 * TIME.SECOND + DELAY_VALUE + EVENT_DURATION);

        CellModuleMessage resultMessage = cellModuleMessages.get(1);
        checkResultMessage(resultMessage, smallerStreamSize, 10 * TIME.SECOND,
                10 * TIME.SECOND + DELAY_VALUE);

        assertEquals(0, rtiInteractionsSent.size());

        assertEquals(400 - smallerStreamSize, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(600 - smallerStreamSize, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeUnlimited_plusFreeBandwidth() throws InternalFederateException {
        // SETUP
        // UDP
        MessageRouting messageRouting = new CellMessageRoutingBuilder("veh_0", null)
                .streaming(EVENT_DURATION, EXPECTED_BANDWIDTH)
                .destination(new byte[]{10, 2, 0, 0}).topological().build();
        routing.set(messageRouting);

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), MESSAGE_SIZE);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, rtiInteractionsSent.size());

        assertEquals(2, cellModuleMessages.size());
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);
        checkNotifyOnFinishMessage(notifyOnFinishMessage, EXPECTED_BANDWIDTH, 10 * TIME.SECOND,
                10 * TIME.SECOND + DELAY_VALUE + EVENT_DURATION);

        CellModuleMessage resultMessage = cellModuleMessages.get(1);
        checkResultMessage(resultMessage, EXPECTED_BANDWIDTH, 10 * TIME.SECOND,
                10 * TIME.SECOND + DELAY_VALUE);

        assertEquals(Long.MAX_VALUE - EXPECTED_BANDWIDTH, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0")
                .getAvailableUplinkBitrate());
        assertEquals(21000 - EXPECTED_BANDWIDTH, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());

        // FREE
        // SETUP
        Event freeEvent = new Event(notifyOnFinishMessage.getEndTime(), upstreamModule, notifyOnFinishMessage);

        // RUN
        upstreamModule.processEvent(freeEvent);

        // ASSERT
        assertEquals(Long.MAX_VALUE, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(21000, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeUnlimitedRegionLimited() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .streaming(EVENT_DURATION, EXPECTED_BANDWIDTH)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), MESSAGE_SIZE);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 200 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.maxCapacity = 200 * DATA.BIT;

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        // no nack reasons because the default transmission mode is UDP
        assertEquals(0, rtiInteractionsSent.size());

        assertEquals(Long.MAX_VALUE, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(200, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());

    }

    @Test
    public void testProcessMessage_packetLossTcp() throws InternalFederateException {
        // The stream should not be affected by the packet loss, because the assumption is that the packet loss is handled within the stream
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .streaming(EVENT_DURATION, 1000)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), MESSAGE_SIZE);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);
        double lossProbability = 2d; //
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.transmission.lossProbability = lossProbability;

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(2, cellModuleMessages.size());
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);
        checkNotifyOnFinishMessage(notifyOnFinishMessage, EXPECTED_BANDWIDTH, 10 * TIME.SECOND,
                10 * TIME.SECOND + ((long) lossProbability * DELAY_VALUE + EVENT_DURATION));

        CellModuleMessage resultMessage = cellModuleMessages.get(1);
        checkResultMessage(resultMessage, EXPECTED_BANDWIDTH, 10 * TIME.SECOND,
                10 * TIME.SECOND + (long) lossProbability * DELAY_VALUE);

        assertEquals(Long.MAX_VALUE - EXPECTED_BANDWIDTH, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0")
                .getAvailableUplinkBitrate());
        assertEquals(21000 - EXPECTED_BANDWIDTH, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_packetLossUdp() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .streaming(EVENT_DURATION, EXPECTED_BANDWIDTH)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), MESSAGE_SIZE);
        Event event = new Event(10 * TIME.SECOND, upstreamModule, sampleV2XMessage);

        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.transmission.lossProbability = 3d;

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(2, cellModuleMessages.size());
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);

        checkNotifyOnFinishMessage(notifyOnFinishMessage, EXPECTED_BANDWIDTH, 10 * TIME.SECOND,
                10 * TIME.SECOND + (2 * DELAY_VALUE + EVENT_DURATION));

        CellModuleMessage resultMessage = cellModuleMessages.get(1);
        checkResultMessage(resultMessage, EXPECTED_BANDWIDTH, 10 * TIME.SECOND,
                10 * TIME.SECOND + 2 * DELAY_VALUE);

        assertEquals(Long.MAX_VALUE - EXPECTED_BANDWIDTH, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0")
                .getAvailableUplinkBitrate());
        assertEquals(21000 - EXPECTED_BANDWIDTH, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_nodeCapacityExceededRegionUnlimited() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .streaming(EVENT_DURATION, 10)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5);
        Event event = new Event(10, upstreamModule, sampleV2XMessage);
        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0");
        cellConfiguration.consumeUplink(Long.MAX_VALUE);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.NODE_CAPACITY_EXCEEDED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(0, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(21000, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_nodeCapacityExceededRegionLimited() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .streaming(EVENT_DURATION, 10)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0");
        cellConfiguration.consumeUplink(Long.MAX_VALUE);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = EXPECTED_BANDWIDTH;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.maxCapacity = 2 * EXPECTED_BANDWIDTH;

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5);
        Event event = new Event(10, upstreamModule, sampleV2XMessage);
        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.NODE_CAPACITY_EXCEEDED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(0, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(EXPECTED_BANDWIDTH, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_channelCapacityExceededTcp() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .streaming(EVENT_DURATION, 10)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5);
        Event event = new Event(10, upstreamModule, sampleV2XMessage);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 0L;

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.CHANNEL_CAPACITY_EXCEEDED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(Long.MAX_VALUE, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
        assertEquals(0, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_channelNodeCapacityExceededTcp() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .streaming(EVENT_DURATION, 10)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5);
        Event event = new Event(10, upstreamModule, sampleV2XMessage);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity = 0L;

        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0");
        cellConfiguration.consumeUplink(Long.MAX_VALUE);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons =
                Arrays.asList(NegativeAckReason.CHANNEL_CAPACITY_EXCEEDED, NegativeAckReason.NODE_CAPACITY_EXCEEDED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(0, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
        assertEquals(0, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
    }

    @Test
    public void testProcessMessage_nodeDeactivatedTcp() throws InternalFederateException {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .streaming(EVENT_DURATION, 10)
                        .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5);
        Event event = new Event(10, upstreamModule, sampleV2XMessage);

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

        assertEquals(21000, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
        assertEquals(Long.MAX_VALUE, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
    }

    @Test
    public void testProcessMessage_nodeDeactivatedUdp() throws InternalFederateException {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .streaming(EVENT_DURATION, EXPECTED_BANDWIDTH)
                .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5);
        Event event = new Event(10, upstreamModule, sampleV2XMessage);

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
        when(destinationAddressContainer.getRoutingType()).thenReturn(RoutingType.CELL_TOPOCAST);
        routing.set(new MessageStreamRouting(destinationAddressContainer, sourceAddressContainerMock, EVENT_DURATION, EXPECTED_BANDWIDTH));

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5);
        Event event = new Event(10, upstreamModule, sampleV2XMessage);

        when(sourceAddressContainerMock.getSourceName()).thenReturn("veh_1");

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());
        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.NODE_DEACTIVATED);
        checkRtiMessages(nackReasons, sampleV2XMessage);

        assertEquals(21000, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.uplink.capacity.longValue());
        assertEquals(Long.MAX_VALUE, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableUplinkBitrate());
    }

    @Test
    public void testProcessMessage_nodeNotRegisteredUdp() {
        // SETUP
        // UDP
        IpResolver.getSingleton().registerHost("veh_1");
        routing.set(new CellMessageRoutingBuilder("veh_1", null)
                .streaming(EVENT_DURATION, EXPECTED_BANDWIDTH)
                .destination(new byte[]{10, 2, 0, 0}).topological().build()
        );

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5);
        Event event = new Event(10, upstreamModule, sampleV2XMessage);

        // RUN
        upstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());
        assertEquals(0, rtiInteractionsSent.size());
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