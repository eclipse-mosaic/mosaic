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

package org.eclipse.mosaic.fed.output.ambassador;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.eclipse.mosaic.interactions.mapping.RsuRegistration;
import org.eclipse.mosaic.interactions.mapping.VehicleRegistration;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleDeparture;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;
import org.eclipse.mosaic.lib.util.junit.TestFileRule;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.RtiAmbassador;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Test suite for {@link OutputAmbassador}.
 */
public class VisualizationAmbassadorTest {

    @Rule
    public TestFileRule testFileRule = new TestFileRule()
            .with("/testconfiguration.xml")
            .with("/testconfiguration_faulty.xml")
            .with("/testconfiguration_sameId.xml");

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    public RtiAmbassador rtiMock;

    @Mock
    public VehicleDeparture vehicleDepartureMock;

    @Mock
    public VehicleType vehicleTypeMock;

    private OutputAmbassador output;
    private OutputAmbassador outputFaulty;

    @Before
    public void setup() throws IOException {
        AmbassadorParameter ambassadorParameter = new AmbassadorParameter("output", testFileRule.get("testconfiguration.xml"));
        output = new OutputAmbassador(ambassadorParameter);
        output.setRtiAmbassador(rtiMock);

        ambassadorParameter = new AmbassadorParameter("output", testFileRule.get("testconfiguration_faulty.xml"));
        outputFaulty = new OutputAmbassador(ambassadorParameter);
        outputFaulty.setRtiAmbassador(rtiMock);
    }

    @Test
    public void readConfiguration() throws InternalFederateException {
        //RUN
        output.connectToFederate("", 0);

        //generator should not be created yet
        assertNull(output.generators.get("generator1"));

        //RUN (now generators are created)
        output.initialize(0, 100);

        //ASSERT
        assertEquals(3, output.generators.size());

        GeneratorInformation generatorInformation = output.generators.get("generator1");

        assertNotNull(generatorInformation);
        assertTrue(generatorInformation.isInteractionTypeRelevant("VehicleUpdates"));
        assertTrue(generatorInformation.isInteractionTypeRelevant("V2xMessageTransmission"));
        assertFalse(generatorInformation.isInteractionTypeRelevant("V2xMessageReception"));
        assertEquals(5, generatorInformation.getUpdateUnitCount());
        // testing default values for visualization start and end time
        assertEquals(0L, generatorInformation.getHandleStartTime());
        assertEquals((long) Integer.MAX_VALUE * TIME.SECOND, generatorInformation.getHandleEndTime());

        generatorInformation = output.generators.get("generator2");

        assertNotNull(generatorInformation);
        assertTrue(generatorInformation.isInteractionTypeRelevant("VehicleUpdates"));
        assertFalse(generatorInformation.isInteractionTypeRelevant("V2xMessageTransmission"));
        assertEquals(1, generatorInformation.getUpdateUnitCount());

        generatorInformation = output.generators.get("generator3");
        assertNull(generatorInformation);
        generatorInformation = output.generators.get("generator4");

        assertNotNull(generatorInformation);
        assertTrue(generatorInformation.isInteractionTypeRelevant("VehicleUpdates"));
        assertEquals(2 * TIME.SECOND, generatorInformation.getHandleStartTime());
        assertEquals(6 * TIME.SECOND, generatorInformation.getHandleEndTime());
        assertEquals(1, generatorInformation.getUpdateUnitCount());
    }

    @Test
    public void visualizeIntearction() throws InternalFederateException {
        //PREPARE
        output.initialize(0, Long.MAX_VALUE);

        AbstractOutputGenerator generator1 = output.generators.get("generator1").getGenerator();
        AbstractOutputGenerator generator2 = output.generators.get("generator2").getGenerator();

        //RUN
        output.processInteraction(new VehicleUpdates(0, Lists.newArrayList(), Lists.newArrayList(), Lists.newArrayList()));

        //RUN+ASSERT
        verify(generator1, never()).handleUnregisteredInteraction(isA(VehicleUpdates.class));
        verify(generator2, never()).handleUnregisteredInteraction(isA(VehicleUpdates.class));

        output.processTimeAdvanceGrant(TIME.SECOND);
        verify(generator1, never()).handleUnregisteredInteraction(isA(VehicleUpdates.class));
        verify(generator2, never()).handleUnregisteredInteraction(isA(VehicleUpdates.class));

        output.processTimeAdvanceGrant(2 * TIME.SECOND); //at 2 seconds, the second generator gets the message due to its update interval of 2
        verify(generator1, never()).handleUnregisteredInteraction(isA(VehicleUpdates.class));
        verify(generator2, times(1)).handleUnregisteredInteraction(isA(VehicleUpdates.class));

        output.processTimeAdvanceGrant(4 * TIME.SECOND);
        verify(generator1, never()).handleUnregisteredInteraction(isA(VehicleUpdates.class));

        output.processTimeAdvanceGrant(6 * TIME.SECOND);
        verify(generator1, never()).handleUnregisteredInteraction(isA(VehicleUpdates.class));

        output.processTimeAdvanceGrant(8 * TIME.SECOND);
        verify(generator1, never()).handleUnregisteredInteraction(isA(VehicleUpdates.class));

        //at 10 seconds, the first generator gets the message as well due to its update interval of 10
        output.processTimeAdvanceGrant(10 * TIME.SECOND);
        verify(generator1, times(1)).handleUnregisteredInteraction(isA(VehicleUpdates.class));
        verify(generator2, times(1)).handleUnregisteredInteraction(isA(VehicleUpdates.class));
    }

    @Test
    public void visualizeMessageInInterval() throws InternalFederateException {
        //PREPARE
        output.initialize(0, Long.MAX_VALUE);

        AbstractOutputGenerator generator4 = output.generators.get("generator4").getGenerator();

        //RUN
        // send one VehicleRegistration and RsuRegistration message outside of visualization interval
        output.processInteraction(new VehicleRegistration(
                0 * TIME.SECOND,
                "",
                "",
                Lists.newArrayList(),
                vehicleDepartureMock,
                vehicleTypeMock
        ));
        output.processInteraction(new RsuRegistration(0 * TIME.SECOND, "", "", Lists.newArrayList(), GeoPoint.ORIGO));

        // send 6 VehicleUpdates messages at 0, 2, 4, ... seconds
        for (int i = 0; i < 6; i++) {
            output.processInteraction(new VehicleUpdates(i * 2 * TIME.SECOND, Lists.newArrayList(), Lists.newArrayList(), Lists.newArrayList()));
        }

        //RUN+ASSERT
        verify(generator4, never()).handleUnregisteredInteraction(isA(VehicleUpdates.class));
        for (int i = 1; i <= 8; i++) {
            output.processTimeAdvanceGrant(i * TIME.SECOND);
        }

        //ASSERT
        // should only receive 3 messages in handle interval
        verify(generator4, times(3)).handleUnregisteredInteraction(isA(VehicleUpdates.class));
        // should have received the VehicleRegistration Interaction even though it is not in handle interval
        verify(generator4, times(1)).handleUnregisteredInteraction(isA(VehicleRegistration.class));
        //should NOT have received RsuRegistration interaction as it was not declared in the output_config.xml
        verify(generator4, never()).handleUnregisteredInteraction(isA(RsuRegistration.class));
    }

    /**
     * A test to check if faulty start and end values are handled correct.
     * Checks log output to verify that Exceptions were logged.
     */
    @Test
    public void testFaultyConfig() throws InternalFederateException {
        // PREPARE
        // adding an appender to the log, to check log messages
        ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(OutputAmbassador.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.getContext();
        listAppender.start();

        log.addAppender(listAppender);

        // RUN
        log.info("4 ERRORS are expected:");
        outputFaulty.initialize(0, 100);

        List<ILoggingEvent> logList = listAppender.list;

        // filter out all thrown proxies
        StringBuilder allThrowableProxies = new StringBuilder();
        for (ILoggingEvent loggingEvent : logList) {
            if (loggingEvent.getThrowableProxy() != null) {
                allThrowableProxies.append(loggingEvent.getThrowableProxy().getMessage());
            }

        }
        // ASSERT
        // see if right exception were thrown
        assertThat(allThrowableProxies.toString(), containsString("The value for start time can't be higher than the value for end time."));
        assertThat(allThrowableProxies.toString(), containsString("The overwriting update value couldn't be a non-positive value."));
        assertThat(allThrowableProxies.toString(), containsString("The value for start time can't be negative."));
        assertThat(allThrowableProxies.toString(), containsString("non.existent.generator"));

        // AFTER
        // remove appender
        ((Logger) LoggerFactory.getLogger(OutputAmbassador.class)).detachAppender(listAppender);
    }

    /**
     * A test to check if faulty start of file `_sameId` is handled correctly.
     */
    @Test
    public void testFaultyConfig_sameId() throws InternalFederateException {
        AmbassadorParameter ambassadorParameter = new AmbassadorParameter("output", testFileRule.get("testconfiguration_sameId.xml"));
        outputFaulty = new OutputAmbassador(ambassadorParameter);
        outputFaulty.setRtiAmbassador(rtiMock);
        
        assertThrows(RuntimeException.class, () -> outputFaulty.initialize(0, 100));
        try {
            outputFaulty.initialize(0, 100);
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("due to duplicate id"));
        }
    }

}