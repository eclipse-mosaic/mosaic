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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import org.eclipse.mosaic.fed.cell.utility.RegionUtility;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoRectangle;
import org.eclipse.mosaic.lib.geo.UtmPoint;
import org.eclipse.mosaic.lib.geo.UtmZone;
import org.eclipse.mosaic.lib.junit.GeoProjectionRule;
import org.eclipse.mosaic.lib.junit.IpResolverRule;
import org.eclipse.mosaic.lib.math.DefaultRandomNumberGenerator;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.objects.addressing.CellMessageRoutingBuilder;
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;
import org.eclipse.mosaic.lib.objects.communication.CellConfiguration;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.DATA;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.RtiAmbassador;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class GeocasterModuleTest {

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
            .withCellConfig("configs/sample_cell.json")
            .withNetworkConfig("configs/network_for_moduletest.json")
            .withRegionConfig("configs/regions_for_moduletest.json");

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(transformationRule).around(configRule);


    @Rule
    public CellSimulationRule simulationRule = new CellSimulationRule();

    private final static long SEED = 182931861823L;
    private final RandomNumberGenerator rng = new DefaultRandomNumberGenerator(SEED);
    private final List<CellModuleMessage> cellModuleMessages = new ArrayList<>();
    private final AtomicReference<MessageRouting> routing = new AtomicReference<>();

    private GeocasterModule geocasterModule;

    @Before
    public void setup() {
        File ambassadorConfiguration = new File("cell_config.json");
        ambassadorParameter = new AmbassadorParameter(null, ambassadorConfiguration);
        ChainManager chainManager = new ChainManager(rti, rng, ambassadorParameter) {
            @Override
            public void finishEvent(CellModuleMessage cellModuleMessage) {
                cellModuleMessages.add(cellModuleMessage);
            }
        };

        geocasterModule = new GeocasterModule(chainManager);

        CellConfiguration cellConfiguration = new CellConfiguration("veh_0", true);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_0", cellConfiguration);

        IpResolver.getSingleton().registerHost("veh_0");
    }

    @Test
    public void testCellTopocast() {
        // SETUP
        CellConfiguration cellModuleConfiguration = new CellConfiguration("veh_2", true);
        SimulationData.INSTANCE.setCellConfigurationOfNode("veh_2", cellModuleConfiguration);
        IpResolver.getSingleton().registerHost("veh_2");

        routing.set(new CellMessageRoutingBuilder("veh_0", null)
                .destination(new byte[]{10, 0, 0, 3})
                .topological()
                .build());
        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        StreamResult streamResult =
                new StreamResult(GLOBAL_NETWORK_ID, 200 * DATA.BIT, TransmissionMode.UplinkUnicast, "rsu_0", sampleV2XMessage);
        CellModuleMessage cellModuleMessage = new CellModuleMessage.Builder("Upstream", "Downstream")
                .resource(streamResult)
                .build();
        Event event = new Event(10 * TIME.SECOND, geocasterModule, cellModuleMessage);

        // RUN
        geocasterModule.processEvent(event);

        // ASSERT
        assertEquals(1, cellModuleMessages.size());
        CellModuleMessage resultModuleMessage = cellModuleMessages.get(0);
        assertEquals(10 * TIME.SECOND, resultModuleMessage.getStartTime());
        assertEquals(10 * TIME.SECOND, resultModuleMessage.getEndTime());
        assertEquals("Geocaster", resultModuleMessage.getEmittingModule());
        assertEquals("Downstream", resultModuleMessage.getNextModule());
        assertTrue(resultModuleMessage.getResource() instanceof GeocasterResult);
        GeocasterResult geocasterResult = resultModuleMessage.getResource();
        assertEquals(TransmissionMode.DownlinkUnicast, geocasterResult.getDownstreamMode());
        assertNotNull(geocasterResult.getReceivers());
        assertTrue(geocasterResult.getReceivers().containsValue("veh_2"));
        assertEquals(1, geocasterResult.getReceivers().size());
        assertTrue(geocasterResult.getReceivers().containsKey(ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork));
    }

    @Test
    public void testCellGeoUnicast() {
        // SETUP
        GeoPoint nw = GeoPoint.lonLat(13.333625793457031, 52.51563064800963);
        GeoPoint se = GeoPoint.lonLat(13.421859741210938, 52.5053554452214);
        GeoRectangle geoRectangle = new GeoRectangle(nw, se);
        routing.set(new CellMessageRoutingBuilder("veh_0", null).broadcast().geographical(geoRectangle).build());
        GeoPoint inRectangleKreuzberg = GeoPoint.lonLat(13.404693603515625, 52.50838549553871);
        GeoPoint inRectangleGrosserStern = GeoPoint.lonLat(13.349933624267578, 52.51388868388495);
        GeoPoint offRectangleKreuzberg = GeoPoint.lonLat(13.404006958007812, 52.498111211481216);
        GeoPoint offRectangleTempelhof = GeoPoint.lonLat(13.386154174804688, 52.47065170749479);

        final Multimap<CNetworkProperties, String> receiverMap = ArrayListMultimap.create();

        String[] receivers = {"veh_0", "veh_1", "veh_2", "veh_3"};
        SimulationData.INSTANCE.setPositionOfNode(receivers[0], inRectangleKreuzberg.toCartesian());
        SimulationData.INSTANCE.setPositionOfNode(receivers[1], inRectangleGrosserStern.toCartesian());
        SimulationData.INSTANCE.setPositionOfNode(receivers[2], offRectangleKreuzberg.toCartesian());
        SimulationData.INSTANCE.setPositionOfNode(receivers[3], offRectangleTempelhof.toCartesian());

        for (String receiver : receivers) {
            CellConfiguration cellConfiguration = new CellConfiguration(receiver, true, 400 * DATA.BIT, 400 * DATA.BIT);
            SimulationData.INSTANCE.setCellConfigurationOfNode(receiver, cellConfiguration);
        }

        CNetworkProperties region0 = RegionUtility.getRegionForNode("veh_0");
        receiverMap.put(region0, "veh_0");
        CNetworkProperties region1 = RegionUtility.getRegionForNode("veh_1");
        receiverMap.put(region1, "veh_1");

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        StreamResult streamResult =
                new StreamResult(GLOBAL_NETWORK_ID, 200 * DATA.BIT, TransmissionMode.UplinkUnicast, "rsu_0", sampleV2XMessage);
        CellModuleMessage cellModuleMessage = new CellModuleMessage.Builder("Upstream", "Geocaster")
                .resource(streamResult)
                .build();
        long startTime = 10 * TIME.SECOND;
        Event event = new Event(startTime, geocasterModule, cellModuleMessage);

        // RUN
        geocasterModule.processEvent(event);

        // ASSERT
        assertEquals(1, cellModuleMessages.size());
        CellModuleMessage resultMessage = cellModuleMessages.get(0);
        assertEquals("Geocaster", resultMessage.getEmittingModule());
        assertEquals("Downstream", resultMessage.getNextModule());
        assertEquals(startTime, resultMessage.getStartTime());
        assertEquals(startTime, resultMessage.getEndTime());
        assertTrue(resultMessage.getResource() instanceof GeocasterResult);
        GeocasterResult geocasterResult = resultMessage.getResource();
        assertEquals(TransmissionMode.DownlinkUnicast, geocasterResult.getDownstreamMode());
        assertEquals(receiverMap, geocasterResult.getReceivers());
    }

    @Test
    public void testCellGeoBroadcastMbms() {
        // SETUP
        GeoPoint nw = GeoPoint.lonLat(13.333625793457031, 52.51563064800963);
        GeoPoint se = GeoPoint.lonLat(13.421859741210938, 52.5053554452214);
        GeoRectangle geoRectangle = new GeoRectangle(nw, se);
        routing.set(new CellMessageRoutingBuilder("veh_0", null).broadcast().mbs().geographical(geoRectangle).build());
        GeoPoint inRectangleKreuzberg = GeoPoint.lonLat(13.404693603515625, 52.50838549553871);
        GeoPoint inRectangleGrosserStern = GeoPoint.lonLat(13.349933624267578, 52.51388868388495);
        GeoPoint offRectangleKreuzberg = GeoPoint.lonLat(13.404006958007812, 52.498111211481216);
        GeoPoint offRectangleTempelhof = GeoPoint.lonLat(13.386154174804688, 52.47065170749479);

        final Multimap<CNetworkProperties, String> receiverMap = ArrayListMultimap.create();
        String[] receivers = {"veh_0", "veh_1", "veh_2", "veh_3"};
        SimulationData.INSTANCE.setPositionOfNode(receivers[0], inRectangleKreuzberg.toCartesian());
        SimulationData.INSTANCE.setPositionOfNode(receivers[1], inRectangleGrosserStern.toCartesian());
        SimulationData.INSTANCE.setPositionOfNode(receivers[2], offRectangleKreuzberg.toCartesian());
        SimulationData.INSTANCE.setPositionOfNode(receivers[3], offRectangleTempelhof.toCartesian());

        for (String receiver : receivers) {
            CellConfiguration cellConfiguration = new CellConfiguration(receiver, true, 400 * DATA.BIT, 400 * DATA.BIT);
            SimulationData.INSTANCE.setCellConfigurationOfNode(receiver, cellConfiguration);
        }

        CNetworkProperties region0 = RegionUtility.getRegionForNode("veh_0");
        receiverMap.put(region0, "veh_0");
        CNetworkProperties region1 = RegionUtility.getRegionForNode("veh_1");
        receiverMap.put(region1, "veh_1");
        CNetworkProperties region2 = RegionUtility.getRegionForNode("veh_2");
        receiverMap.put(region2, "veh_2");

        SampleV2xMessage sampleV2XMessage = new SampleV2xMessage(routing.get(), 5 * DATA.BYTE);
        StreamResult streamResult =
                new StreamResult(GLOBAL_NETWORK_ID, 200 * DATA.BIT, TransmissionMode.UplinkUnicast, "rsu_0", sampleV2XMessage);
        CellModuleMessage cellModuleMessage = new CellModuleMessage.Builder("Upstream", "Geocaster")
                .resource(streamResult)
                .build();
        long startTime = 10 * TIME.SECOND;
        Event event = new Event(startTime, geocasterModule, cellModuleMessage);

        // RUN
        geocasterModule.processEvent(event);

        // ASSERT
        assertEquals(1, cellModuleMessages.size());
        CellModuleMessage resultMessage = cellModuleMessages.get(0);
        assertEquals("Geocaster", resultMessage.getEmittingModule());
        assertEquals("Downstream", resultMessage.getNextModule());
        assertEquals(startTime, resultMessage.getStartTime());
        assertEquals(startTime, resultMessage.getEndTime());
        assertTrue(resultMessage.getResource() instanceof GeocasterResult);
        GeocasterResult geocasterResult = resultMessage.getResource();
        assertEquals(TransmissionMode.DownlinkMulticast, geocasterResult.getDownstreamMode());
        assertTrue(areReceiversSimilar(receiverMap, geocasterResult.getReceivers()));
    }

    private boolean areReceiversSimilar(Multimap<CNetworkProperties, String> expectedReceivers,
                                        Multimap<CNetworkProperties, String> actualReceivers) {
        if (expectedReceivers == null || actualReceivers == null) {
            return expectedReceivers == actualReceivers;
        }

        Set<CNetworkProperties> expectedRegions = expectedReceivers.keySet();
        Set<CNetworkProperties> actualRegions = actualReceivers.keySet();
        if (expectedRegions.size() != actualRegions.size()) {
            return false;
        }
        if (!expectedRegions.containsAll(actualRegions)) {
            return false;
        }

        for (CNetworkProperties region : actualReceivers.keySet()) {
            Collection<String> actualReceiversInRegion = actualReceivers.get(region);
            Collection<String> expectedReceiversInRegion = expectedReceivers.get(region);
            if (actualReceiversInRegion.size() != expectedReceiversInRegion.size()) {
                return false;
            }
            if (!actualReceiversInRegion.containsAll(expectedReceiversInRegion)) {
                return false;
            }
        }
        return true;
    }
}