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

import org.eclipse.mosaic.fed.cell.chain.ChainManager;
import org.eclipse.mosaic.fed.cell.chain.SampleV2xMessage;
import org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties;
import org.eclipse.mosaic.fed.cell.config.model.TransmissionMode;
import org.eclipse.mosaic.fed.cell.data.ConfigurationData;
import org.eclipse.mosaic.fed.cell.data.SimulationData;
import org.eclipse.mosaic.fed.cell.junit.CellConfigurationRule;
import org.eclipse.mosaic.fed.cell.junit.CellSimulationRule;
import org.eclipse.mosaic.fed.cell.message.CellModuleMessage;
import org.eclipse.mosaic.fed.cell.message.GeocasterResult;
import org.eclipse.mosaic.fed.cell.message.StreamResult;
import org.eclipse.mosaic.fed.cell.module.streammodules.DownstreamModule;
import org.eclipse.mosaic.fed.cell.utility.NodeCapacityUtility;
import org.eclipse.mosaic.fed.cell.utility.RegionUtility;
import org.eclipse.mosaic.interactions.communication.V2xMessageAcknowledgement;
import org.eclipse.mosaic.interactions.communication.V2xMessageReception;
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
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;
import org.eclipse.mosaic.lib.objects.communication.CellConfiguration;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.objects.v2x.V2xReceiverInformation;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.DATA;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.RtiAmbassador;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class DownstreamModuleTest {

    private static final long HEADER_UDP = 8 * DATA.BYTE;
    private static final long HEADER_IP = 20 * DATA.BYTE;
    private static final long HEADER_TCP = 20 * DATA.BYTE;
    private static final long HEADER_CELLULAR = 18 * DATA.BYTE;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    @Mock
    public RtiAmbassador rti;

    private AmbassadorParameter ambassadorParameter;

    @Rule
    public IpResolverRule ipResolverRule = new IpResolverRule();

    private final GeoProjectionRule transformationRule = new GeoProjectionRule(
            UtmPoint.eastNorth(UtmZone.from(GeoPoint.lonLat(13.3856, 52.5415)), 388405.53, 5820063.64)
    );

    private final CellConfigurationRule configRule = new CellConfigurationRule()
            .withNetworkConfig("configs/network_for_moduletest.json")
            .withRegionConfig("configs/regions_for_moduletest.json");

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(transformationRule).around(configRule);

    @Rule
    public CellSimulationRule simulationRule = new CellSimulationRule();

    private final static long SEED = 182931861823L;
    private final RandomNumberGenerator rng = new DefaultRandomNumberGenerator(SEED);
    private final List<CellModuleMessage> cellModuleMessages = new ArrayList<>();
    private final List<Interaction> rtiInteractionsSent = new ArrayList<>();
    private final List<Interaction> rtiV2xReceptionsSent = new ArrayList<>();
    private final List<Interaction> rtiV2xAcknowledgementsSent = new ArrayList<>();
    private final AtomicReference<MessageRouting> routing = new AtomicReference<>();
    private static final long DELAY_VALUE_IN_MS = 50 * TIME.MILLI_SECOND;


    private DownstreamModule downstreamModule;

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
                    if (interaction.getTypeId().equals(V2xMessageReception.TYPE_ID)) {
                        rtiV2xReceptionsSent.add((V2xMessageReception) invocationOnMock.getArguments()[0]);
                    } else if (interaction.getTypeId().equals(V2xMessageAcknowledgement.TYPE_ID)) {
                        rtiV2xAcknowledgementsSent.add((V2xMessageAcknowledgement) invocationOnMock.getArguments()[0]);
                    }
                    return null;
                }
        ).when(rti).triggerInteraction(ArgumentMatchers.isA(Interaction.class));

        downstreamModule = new DownstreamModule(chainManager);

        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);

        IpResolver.getSingleton().registerHost("veh_0");
    }

    @Test
    public void testProcessMessage_regularMessageMulticast() throws Exception {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{1, 2, 3, 4})
                .topological()
                .build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        final Multimap<CNetworkProperties, String> receiverMap = ArrayListMultimap.create();
        String[] receivers = {"veh_0", "veh_1", "veh_2"};
        for (String receiver : receivers) {
            CNetworkProperties region = RegionUtility.getRegionForNode(receiver);
            receiverMap.put(region, receiver);
            CellConfiguration cellConfiguration = new CellConfiguration(receiver, true);
            SimulationData.INSTANCE.setCellConfigurationOfNode(receiver, cellConfiguration);
        }

        Event event = createMulticastEvent(receiverMap, sampleV2XMessage);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(3, rtiInteractionsSent.size());
        assertEquals(3, rtiV2xReceptionsSent.size());
        assertEquals(1, cellModuleMessages.size());
        long endTime = 10 * TIME.SECOND + DELAY_VALUE_IN_MS;
        checkNotifyOnFinishMessage(0, GLOBAL_NETWORK_ID, 8960 * DATA.BIT, 10 * TIME.SECOND, endTime);
        for (int i = 0; i < receivers.length; i++) {
            testRtiV2xMessages(i, receivers[i], endTime, sampleV2XMessage.getId());
            assertEquals(
                    Long.MAX_VALUE * DATA.BIT,
                    SimulationData.INSTANCE.getCellConfigurationOfNode(receivers[i]).getAvailableDownlinkBitrate()
            );
        }

        assertEquals((42000 - 8960) * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageMulticast_NodeLimited() throws Exception {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{1, 2, 3, 4})
                .topological()
                .build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        final Multimap<CNetworkProperties, String> receiverMap = ArrayListMultimap.create();
        String[] receivers = {"veh_0", "veh_1", "veh_2"};
        for (String receiver : receivers) {
            CNetworkProperties region = RegionUtility.getRegionForNode(receiver);
            receiverMap.put(region, receiver);
            CellConfiguration cellConfiguration = new CellConfiguration(receiver, true, 10000 * DATA.BIT, 10000 * DATA.BIT);
            SimulationData.INSTANCE.setCellConfigurationOfNode(receiver, cellConfiguration);
        }

        Event event = createMulticastEvent(receiverMap, sampleV2XMessage);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(3, rtiInteractionsSent.size());
        assertEquals(3, rtiV2xReceptionsSent.size());
        assertEquals(1, cellModuleMessages.size());
        long endTime = 10 * TIME.SECOND + DELAY_VALUE_IN_MS;
        checkNotifyOnFinishMessage(0, GLOBAL_NETWORK_ID, 8960 * DATA.BIT, 10 * TIME.SECOND, endTime);
        for (int i = 0; i < receivers.length; i++) {
            testRtiV2xMessages(i, receivers[i], endTime, sampleV2XMessage.getId());
            assertEquals(10000 * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode(receivers[i]).getAvailableDownlinkBitrate());
        }

        assertEquals((42000 - 8960) * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageMulticast_NodeLimitedRegionLimited() throws Exception {
        // SETUP
        // UDP
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity = 1120 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.maxCapacity = 1120 * DATA.BIT;

        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{1, 2, 3, 4})
                .topological()
                .build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        final Multimap<CNetworkProperties, String> receiverMap = ArrayListMultimap.create();
        String[] receivers = {"veh_0", "veh_1", "veh_2"};
        for (String receiver : receivers) {
            CNetworkProperties region = RegionUtility.getRegionForNode(receiver);
            receiverMap.put(region, receiver);
            CellConfiguration cellConfiguration = new CellConfiguration(receiver, true, 2240 * DATA.BIT, 2240 * DATA.BIT);
            SimulationData.INSTANCE.setCellConfigurationOfNode(receiver, cellConfiguration);
        }

        Event event = createMulticastEvent(receiverMap, sampleV2XMessage);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(3, rtiInteractionsSent.size());
        assertEquals(3, rtiV2xReceptionsSent.size());
        assertEquals(1, cellModuleMessages.size());
        //640 bps of capacity remain, since only 50% of the full capacity is usable for the Multicast
        long endTime = 10 * TIME.SECOND + 800 * TIME.MILLI_SECOND;
        checkNotifyOnFinishMessage(0, GLOBAL_NETWORK_ID, 560 * DATA.BIT, 10 * TIME.SECOND, endTime);
        assertEquals(560 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());

        for (int i = 0; i < receivers.length; i++) {
            testRtiV2xMessages(i, receivers[i], endTime, sampleV2XMessage.getId());
            assertEquals(2240 * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode(receivers[i]).getAvailableDownlinkBitrate());
        }
    }

    @Test
    public void testProcessMessage_regularMessageMulticast_MultipleRegions() throws Exception {
        // SETUP
        // UDP
        // Moritzplatz
        GeoPoint moritzplatzKreuzberg = GeoPoint.lonLat(13.423233032226562, 52.50007194840628);

        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity = 1120 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.maxCapacity = 1120 * DATA.BIT;

        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{1, 2, 3, 4})
                .topological()
                .build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        final Multimap<CNetworkProperties, String> receiverMap = ArrayListMultimap.create();
        String[] receivers = {"veh_0", "veh_1", "veh_2"};
        SimulationData.INSTANCE.setPositionOfNode("veh_2", moritzplatzKreuzberg.toCartesian());
        for (String receiver : receivers) {
            CNetworkProperties region = RegionUtility.getRegionForNode(receiver);
            receiverMap.put(region, receiver);
            CellConfiguration cellConfiguration = new CellConfiguration(receiver, true, 2240 * DATA.BIT, 2240 * DATA.BIT);
            SimulationData.INSTANCE.setCellConfigurationOfNode(receiver, cellConfiguration);
        }


        Event event = createMulticastEvent(receiverMap, sampleV2XMessage);
        long startTime = (long) 10 * TIME.SECOND;
        long endTimeGlobaleRegion = (long) (10.8 * TIME.SECOND);
        long endTimeKreuzberg = (long) (10.1 * TIME.SECOND);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(3, rtiInteractionsSent.size());
        assertEquals(3, rtiV2xReceptionsSent.size());
        assertEquals(2, cellModuleMessages.size());
        //120 bps remain, since not the full capacity is usable for the Multicast
        containsNotifyOnFinishMessage(
                "kreuzberg",
                4480 * DATA.BIT,
                startTime, endTimeKreuzberg,
                TransmissionMode.DownlinkMulticast,
                null,
                sampleV2XMessage
        );
        containsNotifyOnFinishMessage(
                GLOBAL_NETWORK_ID,
                560 * DATA.BIT,
                startTime,
                endTimeGlobaleRegion,
                TransmissionMode.DownlinkMulticast,
                null,
                sampleV2XMessage
        );

        assertEquals(560 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());

        for (int i = 1; i < receivers.length; i++) {
            assertEquals(2240 * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode(receivers[i]).getAvailableDownlinkBitrate());
        }
        containsRtiV2xMessages("veh_0", startTime, endTimeGlobaleRegion, sampleV2XMessage.getId(), 560 * DATA.BIT);
        containsRtiV2xMessages("veh_1", startTime, endTimeGlobaleRegion, sampleV2XMessage.getId(), 560 * DATA.BIT);
        containsRtiV2xMessages("veh_2", startTime, endTimeKreuzberg, sampleV2XMessage.getId(), 4480 * DATA.BIT);
    }

    @Test
    public void testProcessMessage_regularMessageNodeLimited() throws Exception {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{1, 2, 3, 4})
                .topological()
                .build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        CellConfiguration cellConfiguration = new CellConfiguration(receiverName, true, 2240 * DATA.BIT, 2240 * DATA.BIT);
        SimulationData.INSTANCE.setCellConfigurationOfNode(receiverName, cellConfiguration);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xReceptionsSent.size());
        assertEquals(1, cellModuleMessages.size());
        long endTime = 10 * TIME.SECOND + 200 * TIME.MILLI_SECOND;
        checkNotifyOnFinishMessage(0, GLOBAL_NETWORK_ID, 2240 * DATA.BIT, 10 * TIME.SECOND, endTime);
        testRtiV2xMessages(0, receiverName, endTime, sampleV2XMessage.getId());

        assertEquals(0L, SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate());
        assertEquals((42000 - 2240) * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeLimitedRegionLessLimited() throws Exception {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{1, 2, 3, 4})
                .topological()
                .build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        CellConfiguration cellConfiguration = new CellConfiguration(receiverName, true, 2240 * DATA.BIT, 2240 * DATA.BIT);
        SimulationData.INSTANCE.setCellConfigurationOfNode(receiverName, cellConfiguration);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity = 3360 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.maxCapacity = 3360 * DATA.BIT;

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xReceptionsSent.size());
        assertEquals(1, cellModuleMessages.size());
        long endTime = 10 * TIME.SECOND + 200 * TIME.MILLI_SECOND;
        checkNotifyOnFinishMessage(0, GLOBAL_NETWORK_ID, 2240 * DATA.BIT, 10 * TIME.SECOND, endTime);
        testRtiV2xMessages(0, receiverName, endTime, sampleV2XMessage.getId());

        assertEquals(0, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableDownlinkBitrate());
        assertEquals((3360 - 2240) * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeLimitedRegionMoreLimited() throws Exception {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{1, 2, 3, 4})
                .topological()
                .build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true, 2240 * DATA.BIT, 2240 * DATA.BIT);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity = 1120 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.maxCapacity = 1120 * DATA.BIT;

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xReceptionsSent.size());
        assertEquals(1, cellModuleMessages.size());
        long endTime = 10 * TIME.SECOND + 400 * TIME.MILLI_SECOND;
        checkNotifyOnFinishMessage(0, GLOBAL_NETWORK_ID, 1120, 10 * TIME.SECOND, endTime);
        testRtiV2xMessages(0, receiverName, endTime, sampleV2XMessage.getId());

        assertEquals((2240 - 1120) * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableDownlinkBitrate());
        assertEquals(0L, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeLimitedRegionBlocked() throws Exception {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{1, 2, 3, 4}).topological().build()
        );
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 10 * DATA.BYTE);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true, 2240 * DATA.BIT, 400 * DATA.BIT);
        SimulationData.INSTANCE.setCellConfigurationOfNode(receiverName, cellConfiguration);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity = 3360 * DATA.BIT;

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.CHANNEL_CAPACITY_EXCEEDED);
        testRtiAckMessages(0, nackReasons, sampleV2XMessage);

        assertEquals(2240 * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate());
        assertEquals(3360 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_regularMessageNodeUnlimited_plusFreeBandwidth() throws Exception {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null).destination(new byte[]{1, 2, 3, 4}).topological().build());
        long messageLength = 10 * DATA.BYTE;
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), messageLength);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);
        long endTime = 10 * TIME.SECOND + DELAY_VALUE_IN_MS;

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xReceptionsSent.size());
        testRtiV2xMessages(0, receiverName, endTime, sampleV2XMessage.getId());

        long actualMessageLength = messageLength + HEADER_UDP + HEADER_IP + HEADER_CELLULAR;
        long expectedBandwidthInBps = (long) (actualMessageLength / (DELAY_VALUE_IN_MS / (double) TIME.SECOND));

        assertEquals(1, cellModuleMessages.size());
        checkNotifyOnFinishMessage(0, GLOBAL_NETWORK_ID, expectedBandwidthInBps, 10 * TIME.SECOND, endTime);

        assertEquals(
                (Long.MAX_VALUE - expectedBandwidthInBps) * DATA.BIT,
                SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate()
        );
        assertEquals(
                (42000 - expectedBandwidthInBps) * DATA.BIT,
                ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue())
        ;

        // FREE
        // SETUP
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(0);
        Event freeEvent = new Event(notifyOnFinishMessage.getEndTime(), downstreamModule, notifyOnFinishMessage);

        // RUN
        downstreamModule.processEvent(freeEvent);

        // ASSERT
        assertEquals(Long.MAX_VALUE * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate());
        assertEquals(42000 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());


    }

    @Test
    public void testProcessMessage_regularMessageNodeUnlimitedRegionLimited() throws Exception {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{1, 2, 3, 4})
                .topological()
                .build());
        long messageLength = 10 * DATA.BYTE;
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), messageLength);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity = 1120 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.maxCapacity = 1120 * DATA.BIT;
        long expectedBandwidthInBps = 1120 * DATA.BIT;
        long endTime = (long) (10.4 * TIME.SECOND);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xReceptionsSent.size());
        testRtiV2xMessages(0, receiverName, endTime, sampleV2XMessage.getId());

        assertEquals(1, cellModuleMessages.size());
        checkNotifyOnFinishMessage(0, GLOBAL_NETWORK_ID, expectedBandwidthInBps, 10 * TIME.SECOND,
                endTime);

        assertEquals(
                (Long.MAX_VALUE - expectedBandwidthInBps) * DATA.BIT,
                SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate()
        );
        assertEquals(0L, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_packetLossTcp() throws Exception {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{1, 2, 3, 4}).topological().build()
        );
        long messageLength = 10 * DATA.BYTE;
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), messageLength);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.unicast.transmission.lossProbability = 2d;
        double delayInS = ((ConstantDelay) ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.unicast.delay).delay
                / (double) TIME.SECOND;
        long actualMessageLength = messageLength + HEADER_TCP + HEADER_IP + HEADER_CELLULAR;
        long expectedBandwidth = (long) (actualMessageLength / delayInS);
        long endTime = 10 * TIME.SECOND + DELAY_VALUE_IN_MS;

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(1, cellModuleMessages.size());
        checkNotifyOnFinishMessage(0, GLOBAL_NETWORK_ID, expectedBandwidth, 10 * TIME.SECOND,
                endTime);

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.PACKET_LOSS);
        testRtiAckMessages(0, nackReasons, sampleV2XMessage);

        assertEquals(
                (Long.MAX_VALUE - expectedBandwidth) * DATA.BIT,
                SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate()
        );
        assertEquals((42000 - expectedBandwidth) * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_packetLossUdp() throws Exception {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null).destination(new byte[]{1, 2, 3, 4}).topological().build());
        long messageLength = 10 * DATA.BYTE;
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), messageLength);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.unicast.transmission.lossProbability = 2d;
        ConstantDelay delay = (ConstantDelay) ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.unicast.delay;
        double delayValue = (double) delay.delay / TIME.MILLI_SECOND;
        long actualMessageLength = messageLength + HEADER_UDP + HEADER_IP + HEADER_CELLULAR;
        long expectedBandwidth = (long) (actualMessageLength / ((delayValue / (double) TIME.MICRO_SECOND)));

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, rtiInteractionsSent.size());

        assertEquals(1, cellModuleMessages.size());
        checkNotifyOnFinishMessage(0, GLOBAL_NETWORK_ID, expectedBandwidth, 10 * TIME.SECOND,
                10 * TIME.SECOND + DELAY_VALUE_IN_MS);

        assertEquals(
                (Long.MAX_VALUE - expectedBandwidth) * DATA.BIT,
                SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate()
        );
        assertEquals((42000 - expectedBandwidth) * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_nodeCapacityExceededRegionUnlimited() throws Exception {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{1, 2, 3, 4}).topological().build()
        );
        long messageLength = 10 * DATA.BYTE;
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), messageLength);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName);
        cellConfiguration.consumeDownlink(Long.MAX_VALUE * DATA.BIT);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.NODE_CAPACITY_EXCEEDED);
        testRtiAckMessages(0, nackReasons, sampleV2XMessage);

        assertEquals(0L, SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate());
        assertEquals(42000 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_nodeCapacityExceededRegionLimited() throws Exception {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{1, 2, 3, 4}).topological().build()
        );
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName);
        cellConfiguration.consumeDownlink(Long.MAX_VALUE * DATA.BIT);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity = 1140 * DATA.BIT;
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.maxCapacity = 1140 * DATA.BIT;

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.NODE_CAPACITY_EXCEEDED);
        testRtiAckMessages(0, nackReasons, sampleV2XMessage);

        assertEquals(0L, SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate());
        assertEquals(1140 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_channelCapacityExceededTcp() throws Exception {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{1, 2, 3, 4}).topological().build()
        );
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity = 0L;

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Arrays.asList(NegativeAckReason.CHANNEL_CAPACITY_EXCEEDED);
        testRtiAckMessages(0, nackReasons, sampleV2XMessage);

        assertEquals(Long.MAX_VALUE * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate());
        assertEquals(0L, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_channelNodeCapacityExceeded() throws Exception {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{1, 2, 3, 4}).topological().build()
        );
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);


        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity = 0L;
        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName);
        cellConfiguration.consumeDownlink(Long.MAX_VALUE * DATA.BIT);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons =
                Arrays.asList(NegativeAckReason.CHANNEL_CAPACITY_EXCEEDED, NegativeAckReason.NODE_CAPACITY_EXCEEDED);
        testRtiAckMessages(0, nackReasons, sampleV2XMessage);

        assertEquals(0L, SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate());
        assertEquals(0L, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    @Test
    public void testProcessMessage_nodeDeactivatedTcp() throws Exception {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{1, 2, 3, 4}).topological().build()
        );
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName);
        cellConfiguration.setEnabled(false);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());

        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Arrays.asList(NegativeAckReason.NODE_DEACTIVATED);
        testRtiAckMessages(0, nackReasons, sampleV2XMessage);

        assertEquals(42000 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
        assertEquals(Long.MAX_VALUE * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName).getAvailableDownlinkBitrate());
    }

    @Test
    public void testProcessMessage_nodeDeactivatedUdp() throws Exception {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null).destination(new byte[]{1, 2, 3, 4}).topological().build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        String receiverName = "veh_0";
        Event event = createEvent(receiverName, sampleV2XMessage);

        CellConfiguration cellConfiguration = SimulationData.INSTANCE.getCellConfigurationOfNode(receiverName);
        cellConfiguration.setEnabled(false);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());
        assertEquals(0, rtiInteractionsSent.size());
    }

    @Test
    public void testProcessMessage_nodeNotRegisteredTcp() throws Exception {
        // SETUP
        // TCP
        routing.set(
                new CellMessageRoutingBuilder("veh_0", null)
                        .protocol(ProtocolType.TCP)
                        .destination(new byte[]{1, 2, 3, 4}).topological().build()
        );
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        Event event = createEvent("rsu_4", sampleV2XMessage);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());
        assertEquals(1, rtiInteractionsSent.size());
        assertEquals(1, rtiV2xAcknowledgementsSent.size());
        List<NegativeAckReason> nackReasons = Collections.singletonList(NegativeAckReason.NODE_DEACTIVATED);
        testRtiAckMessages(0, nackReasons, sampleV2XMessage);

        assertEquals(42000 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
        assertEquals(Long.MAX_VALUE * DATA.BIT, SimulationData.INSTANCE.getCellConfigurationOfNode("veh_0").getAvailableDownlinkBitrate());
    }

    @Test
    public void testProcessMessage_nodeNotRegisteredUdp() throws Exception {
        // SETUP
        // UDP
        routing.set(new CellMessageRoutingBuilder("veh_0", null).destination(new byte[]{1, 2, 3, 4}).topological().build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        Event event = createEvent("rsu_4", sampleV2XMessage);

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(0, cellModuleMessages.size());
        assertEquals(0, rtiInteractionsSent.size());
    }

    @Test
    public void testFreeBandwidth_regularMessage() throws Exception {
        // SETUP
        String receiverName = "veh_0";
        CellConfiguration cellConfiguration = new CellConfiguration(receiverName, true, 400 * DATA.BIT, 400 * DATA.BIT);
        NodeCapacityUtility.consumeCapacityDown(cellConfiguration, 200 * DATA.BIT);
        SimulationData.INSTANCE.setCellConfigurationOfNode(receiverName, cellConfiguration);
        ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity = (42000 - 200) * DATA.BIT;
        routing.set(new CellMessageRoutingBuilder("veh_0", null).destination(new byte[]{1, 2, 3, 4}).topological().build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        StreamResult streamResult =
                new StreamResult(GLOBAL_NETWORK_ID, 200 * DATA.BIT, TransmissionMode.DownlinkUnicast, receiverName, sampleV2XMessage);
        CellModuleMessage freeMessage = new CellModuleMessage.Builder("Downstream", "Downstream").resource(streamResult).build();

        Event event = new Event(10 * TIME.NANO_SECOND, downstreamModule, freeMessage);

        // ASSERT
        assertEquals(200 * DATA.BIT, cellConfiguration.getAvailableDownlinkBitrate());

        // RUN
        downstreamModule.processEvent(event);

        // ASSERT
        assertEquals(400 * DATA.BIT, cellConfiguration.getAvailableDownlinkBitrate());
        assertEquals(42000 * DATA.BIT, ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork.downlink.capacity.longValue());
    }

    private void testRtiAckMessages(int index, List<NegativeAckReason> negativeAckReasons, SampleV2xMessage sampleV2XMessage) {
        assertTrue(rtiInteractionsSent.get(index) instanceof V2xMessageAcknowledgement);
        V2xMessageAcknowledgement ackV2xMessage = (V2xMessageAcknowledgement) rtiInteractionsSent.get(0);
        assertEquals(sampleV2XMessage.getId(), ackV2xMessage.getOriginatingMessageId());
        assertEquals(negativeAckReasons, ackV2xMessage.getNegativeReasons());
        assertFalse(ackV2xMessage.isAcknowledged());
    }

    private void testRtiV2xMessages(int index, String receiverName, long endTime, int id) {
        assertTrue(rtiInteractionsSent.get(index) instanceof V2xMessageReception);
        V2xMessageReception v2xMessageReception = (V2xMessageReception) rtiInteractionsSent.get(index);
        assertEquals(receiverName, v2xMessageReception.getReceiverName());
        assertEquals(endTime, v2xMessageReception.getTime());
        assertEquals(id, v2xMessageReception.getMessageId());
    }

    private void containsRtiV2xMessages(String receiverName, long startTime, long endTime, int id, long neededBandwidth) {
        V2xReceiverInformation receiverInformation = new V2xReceiverInformation(endTime)
                .sendTime(startTime).neededBandwidth(neededBandwidth);
        V2xMessageReception v2xMessageReception = new V2xMessageReception(endTime, receiverName, id, receiverInformation);
        assertTrue(rtiInteractionsSent.contains(v2xMessageReception));
    }


    private void checkNotifyOnFinishMessage(int index, String regionId, long bandwidth, long startTime, long endTime) {
        CellModuleMessage notifyOnFinishMessage = cellModuleMessages.get(index);
        assertEquals("Downstream", notifyOnFinishMessage.getEmittingModule());
        assertEquals("Downstream", notifyOnFinishMessage.getNextModule());
        assertTrue(notifyOnFinishMessage.getResource() instanceof StreamResult);
        StreamResult streamResultMessage = notifyOnFinishMessage.getResource();
        assertNotNull(streamResultMessage.getV2xMessage());
        assertEquals(regionId, streamResultMessage.getRegionId());
        assertEquals(bandwidth, streamResultMessage.getConsumedBandwidth());
        assertEquals(startTime, notifyOnFinishMessage.getStartTime());
        assertEquals(endTime, notifyOnFinishMessage.getEndTime());
    }

    private void containsNotifyOnFinishMessage(String regionId,
                                               long bandwidth,
                                               long startTime,
                                               long endTime,
                                               TransmissionMode mode,
                                               String involvedNode,
                                               V2xMessage message) {
        StreamResult streamResult = new StreamResult(regionId, bandwidth, mode, involvedNode, message);
        CellModuleMessage containsMessage = new CellModuleMessage.Builder("Downstream", "Downstream")
                .startTime(startTime)
                .endTime(endTime)
                .resource(streamResult)
                .build();
        assertTrue(cellModuleMessages.contains(containsMessage));
    }


    private Event createEvent(String receiverName, V2xMessage v2XMessage) {
        CNetworkProperties region = RegionUtility.getRegionForNode(receiverName);
        final Multimap<CNetworkProperties, String> receivers = ArrayListMultimap.create();
        receivers.put(region, receiverName);
        GeocasterResult geocasterResult = new GeocasterResult(receivers, TransmissionMode.DownlinkUnicast, v2XMessage, false);
        CellModuleMessage cellModuleMessage = new CellModuleMessage.Builder("Geocaster", "Downstream").resource(geocasterResult).build();

        return new Event(10 * TIME.SECOND, downstreamModule, cellModuleMessage);
    }

    private Event createMulticastEvent(Multimap<CNetworkProperties, String> receiverMap, V2xMessage v2XMessage) {
        GeocasterResult geocasterResult = new GeocasterResult(receiverMap, TransmissionMode.DownlinkMulticast, v2XMessage, false);
        CellModuleMessage cellModuleMessage = new CellModuleMessage.Builder("Geocaster", "Downstream").resource(geocasterResult).build();

        return new Event(10 * TIME.SECOND, downstreamModule, cellModuleMessage);
    }


}
