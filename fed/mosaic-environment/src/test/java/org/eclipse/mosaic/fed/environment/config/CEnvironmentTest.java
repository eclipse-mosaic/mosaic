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

package org.eclipse.mosaic.fed.environment.config;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.eclipse.mosaic.lib.enums.EnvironmentEventCause;
import org.eclipse.mosaic.lib.geo.GeoArea;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoPolygon;
import org.eclipse.mosaic.lib.geo.GeoRectangle;
import org.eclipse.mosaic.lib.objects.environment.Sensor;
import org.eclipse.mosaic.lib.enums.TractionHazard;
import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;
import org.eclipse.mosaic.rti.TIME;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

public class CEnvironmentTest {

    private URI VALID_CONFIG;
    private URI INVALID_CONFIG;

    @Before
    public void setUp() throws URISyntaxException {
        VALID_CONFIG = Objects.requireNonNull(this.getClass().getClassLoader().getResource("valid_config.json")).toURI();
        INVALID_CONFIG = Objects.requireNonNull(this.getClass().getClassLoader().getResource("invalid_configurations/invalid_config.json")).toURI();
    }

    /**
     * Test case using a properly formed json configuration and asserting, that all values
     * were properly deserialized.
     *
     * @throws InstantiationException if configuration couldn't be properly deserialized, under normal circumstances this should not occur
     */
    @Test
    public void readValidConfig_assertProperties() throws InstantiationException {
        // SETUP + RUN
        CEnvironment eventserverConfiguration = getEventserverConfiguration(VALID_CONFIG);
        assertNotNull(eventserverConfiguration);  // assert that configuration is created
        List<CEvent> events = eventserverConfiguration.events;
        assertEquals("4 events should be deserialized", 4, eventserverConfiguration.events.size());
        // make sure all events have their values deserialized
        for (CEvent event : events) {
            assertNotNull(event);
            assertNotNull(event.location);
            assertNotNull(event.time);
        }
        CEvent eventGeoRectangle = eventserverConfiguration.events.get(0);
        CEvent eventGeoCircle = eventserverConfiguration.events.get(1);
        CEvent eventGeoPolygon = eventserverConfiguration.events.get(2);
        CEvent eventParkingLot = eventserverConfiguration.events.get(3);
        GeoRectangle eventGeoRectangleExpectedLocation = new GeoRectangle(
                GeoPoint.latLon(52.51456075310111, 13.325493335723877),
                GeoPoint.latLon(52.511596424357414, 13.337509632110596)
        );
        GeoCircle eventGeoCircleExpectedLocation = new GeoCircle(
                GeoPoint.latLon(52.51456075310111, 13.325493335723877),
                10000
        );
        GeoPolygon eventGeoPolygonExpectedLocation = new GeoPolygon(
                GeoPoint.latLon(52.51456075310111, 13.325493335723877),
                GeoPoint.latLon(52.71456075310111, 13.525493335723877),
                GeoPoint.latLon(52.31456075310111, 13.025493335723877)
        );
        assertEventDeserialization(
                eventGeoRectangle,
                Sensor.EVENT,
                EnvironmentEventCause.OBSTACLE_ON_ROAD,
                eventGeoRectangleExpectedLocation,
                null,
                0,
                60 * TIME.SECOND
        );
        assertEventDeserialization(
                eventGeoCircle,
                Sensor.TRACTION_HAZARD,
                TractionHazard.ICE,
                eventGeoCircleExpectedLocation,
                null,
                0,
                60 * TIME.SECOND
        );
        assertEventDeserialization(
                eventGeoPolygon,
                Sensor.EVENT,
                EnvironmentEventCause.ROADWORKS,
                eventGeoPolygonExpectedLocation,
                null,
                0,
                60 * TIME.SECOND
        );
        assertEventDeserialization(
                eventParkingLot,
                Sensor.TEMPERATURE,
                10.0,
                null,
                "seg0",
                0,
                60 * TIME.SECOND
        );
    }

    /**
     * Simple test case using an environment configuration, which has missing time values. Asserting
     * for thrown exception and proper error message.
     * TODO: Add additional invalid configurations for better coverage of json-schema
     */
    @Test
    public void readInvalidConfig_assertExceptions() {
        try {
            // SETUP + RUN
            getEventserverConfiguration(INVALID_CONFIG);
            fail("Expected InstantiationException");
        } catch (InstantiationException instantiationException) {
            // ASSERT
            assertThat(
                    instantiationException.getMessage(),
                    startsWith("The CEnvironment config is not valid: $.events[0]: required property 'time' not found")
            );  // checking that proper Exception is thrown
        }
    }

    /**
     * Small helper class, which returns the instantiated object of a json-configuration.
     *
     * @param path the path to the configuration
     * @return the instantiated object
     * @throws InstantiationException if there was an error during deserialization/instantiation
     */
    private CEnvironment getEventserverConfiguration(URI path) throws InstantiationException {
        return new ObjectInstantiation<>(CEnvironment.class).readFile(new File(path));
    }

    private <T> void assertEventDeserialization(
            CEvent event,
            Sensor<T> expectedSensor,
            T expectedEventValue,
            @Nullable GeoArea expectedArea,
            @Nullable String expectedConnectionId,
            long expectedStartTime,
            long expectedEndTime) {

        assertEquals(expectedSensor.getName(), event.event.type);
        assertEquals(expectedEventValue, expectedSensor.translate(event.event.value));
        assertEquals(expectedArea, event.location.area);
        assertEquals(expectedConnectionId, event.location.connectionId);
        assertEquals(expectedStartTime, event.time.start);
        assertEquals(expectedEndTime, event.time.end);
    }
}