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

package org.eclipse.mosaic.fed.mapping.ambassador;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import org.eclipse.mosaic.interactions.mapping.AgentRegistration;
import org.eclipse.mosaic.interactions.mapping.TrafficLightRegistration;
import org.eclipse.mosaic.interactions.mapping.VehicleRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioTrafficLightRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioVehicleRegistration;
import org.eclipse.mosaic.interactions.traffic.VehicleTypesInitialization;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.math.DefaultRandomNumberGenerator;
import org.eclipse.mosaic.lib.math.SpeedUtils;
import org.eclipse.mosaic.lib.objects.mapping.AgentMapping;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLight;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightProgram;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.RtiAmbassador;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MappingAmbassadorTest {

    private Interaction lastReceivedInteraction;
    private long lastTimeAdvanceGrant;

    @Mock
    private RtiAmbassador rtiMock;

    @Test
    public void initializeWithMappingFile_assertSpawningInteractions() throws Exception {
        final MappingAmbassador ambassador = createMappingAmbassadorWithMappingFile("mapping_config.json");
        ambassador.initialize(0, 100 * TIME.SECOND);

        assertNotNull(lastReceivedInteraction);
        assertTrue(lastReceivedInteraction instanceof VehicleTypesInitialization);
        lastReceivedInteraction = null;

        ambassador.advanceTime(0);
        assertEquals(5 * TIME.SECOND, lastTimeAdvanceGrant);

        ambassador.advanceTime(lastTimeAdvanceGrant);
        assertVehicleRegistration("org.eclipse.mosaic.app.tutorials.barnim.WeatherWarningApp");

        assertEquals(8 * TIME.SECOND, lastTimeAdvanceGrant);

        ambassador.advanceTime(lastTimeAdvanceGrant);
        assertVehicleRegistration("org.eclipse.mosaic.app.tutorials.barnim.WeatherWarningApp");

        assertEquals(11 * TIME.SECOND, lastTimeAdvanceGrant);

        ambassador.advanceTime(lastTimeAdvanceGrant);
        assertVehicleRegistration();
    }

    @Test
    public void initializeWithMappingFile_assertSpawningInteractions_scaleTraffic() throws Exception {
        final MappingAmbassador ambassador = createMappingAmbassadorWithMappingFile("mapping_config_scale.json");
        ambassador.initialize(0, 100 * TIME.SECOND);

        assertNotNull(lastReceivedInteraction);
        assertTrue(lastReceivedInteraction instanceof VehicleTypesInitialization);
        lastReceivedInteraction = null;

        ambassador.advanceTime(0);
        ambassador.advanceTime(5 * TIME.SECOND);
        assertVehicleRegistration("app1");

        ambassador.advanceTime(10 * TIME.SECOND);
        assertVehicleRegistration("app2");

        ambassador.advanceTime(13 * TIME.SECOND);
        assertVehicleRegistration("app2");

        ambassador.advanceTime(20 * TIME.SECOND);
        assertVehicleRegistration("app3");

        ambassador.advanceTime(25 * TIME.SECOND);
        assertVehicleRegistration("app3");

        ambassador.advanceTime(30 * TIME.SECOND);
        assertVehicleRegistration("app4");

        ambassador.advanceTime(33 * TIME.SECOND);
        assertVehicleRegistration("app4");

        ambassador.advanceTime(36 * TIME.SECOND);
        assertVehicleRegistration("app4");

        ambassador.advanceTime(39 * TIME.SECOND);
        assertVehicleRegistration("app4");

        ambassador.advanceTime(42 * TIME.SECOND);
        assertVehicleRegistration("app4");

        ambassador.advanceTime(46 * TIME.SECOND);
        assertVehicleRegistration("app5");

        ambassador.advanceTime(49 * TIME.SECOND);
        assertVehicleRegistration("app5");

        ambassador.advanceTime(52 * TIME.SECOND);
        assertVehicleRegistration("app5");

        ambassador.advanceTime(55 * TIME.SECOND);
        assertVehicleRegistration("app5");

        ambassador.advanceTime(58 * TIME.SECOND);
        assertVehicleRegistration("app5");
    }

    @Test
    public void initializeWithMappingFile_weightInPrototype() throws Exception {
        //SETUP
        final MappingAmbassador ambassador = createMappingAmbassadorWithMappingFile("mapping_config_weights_in_prototype.json");

        //RUN
        Pair<Integer, Integer> counts = countVehicleSpawners(ambassador);

        //ASSERT
        assertEquals(2, counts.getLeft().intValue());
        assertEquals(8, counts.getRight().intValue());
    }

    @Test
    public void initializeWithMappingFile_typeDistributionReference() throws Exception {
        //SETUP
        final MappingAmbassador ambassador = createMappingAmbassadorWithMappingFile("mapping_config_reference.json");

        //RUN
        Pair<Integer, Integer> counts = countVehicleSpawners(ambassador);

        //ASSERT
        assertEquals(2, counts.getLeft().intValue());
        assertEquals(8, counts.getRight().intValue());
    }

    @Test
    public void initializeWithMappingFile_timeSpanConfigured() throws Exception {
        //SETUP
        final MappingAmbassador ambassador = createMappingAmbassadorWithMappingFile("mapping_config_timespan.json");
        ambassador.initialize(0, 100 * TIME.SECOND);
        lastReceivedInteraction = null;

        //ASSERT
        ambassador.advanceTime(0);

        ambassador.advanceTime(5 * TIME.SECOND);
        assertNull(lastReceivedInteraction);

        ambassador.advanceTime(8 * TIME.SECOND);
        assertNull(lastReceivedInteraction);

        ambassador.advanceTime(11 * TIME.SECOND);
        assertVehicleRegistration("Car2App");

        ambassador.advanceTime(14 * TIME.SECOND);
        assertVehicleRegistration("Car2App");

        ambassador.advanceTime(17 * TIME.SECOND);
        assertVehicleRegistration("Car1App");

        ambassador.advanceTime(20 * TIME.SECOND);
        assertVehicleRegistration("Car2App");

        ambassador.advanceTime(23 * TIME.SECOND);
        assertVehicleRegistration("Car1App");

        ambassador.advanceTime(26 * TIME.SECOND);
        assertNull(lastReceivedInteraction);
    }

    private Pair<Integer, Integer> countVehicleSpawners(final MappingAmbassador ambassador) throws InternalFederateException {
        ambassador.initialize(0, 100 * TIME.SECOND);

        assertNotNull(lastReceivedInteraction);
        assertTrue(lastReceivedInteraction instanceof VehicleTypesInitialization);
        lastReceivedInteraction = null;

        ambassador.advanceTime(0);

        int countCar1App = 0;
        int countCar2App = 0;

        for (int i = 0; i < 10; i++) {
            ambassador.advanceTime((i * 3 + 5) * TIME.SECOND);

            if (lastReceivedInteraction == null) {
                continue;
            }
            assertTrue(lastReceivedInteraction instanceof VehicleRegistration);

            if (((VehicleRegistration) lastReceivedInteraction).getMapping().getApplications().get(0).equals("Car1App")) {
                countCar1App++;
            }
            if (((VehicleRegistration) lastReceivedInteraction).getMapping().getApplications().get(0).equals("Car2App")) {
                countCar2App++;
            }

            lastReceivedInteraction = null;
        }

        return Pair.of(countCar1App, countCar2App);
    }

    @Test
    public void initializeWithMappingFile_ScenarioTrafficLightRegistrations() throws Exception {
        final MappingAmbassador ambassador = createMappingAmbassadorWithMappingFile("mapping_config.json");
        ambassador.initialize(0, 100 * TIME.SECOND);

        final ScenarioTrafficLightRegistration scenarioTrafficLightRegistrationMsg = createScenarioTrafficLightRegistration();

        ambassador.processInteraction(scenarioTrafficLightRegistrationMsg);
        assertNotNull(lastReceivedInteraction);
        assertTrue(lastReceivedInteraction instanceof VehicleTypesInitialization);
        lastReceivedInteraction = null;

        ambassador.advanceTime(0);
        assertNotNull(lastReceivedInteraction);
        assertTrue(lastReceivedInteraction instanceof TrafficLightRegistration);
        lastReceivedInteraction = null;
    }

    public static ScenarioTrafficLightRegistration createScenarioTrafficLightRegistration() {
        Map<String, Collection<String>> tlgLanes = Maps.newHashMap();
        tlgLanes.put("0", Lists.newArrayList("0_1_2_3"));
        Map<String, TrafficLightProgram> programs = new HashMap<>();
        List<TrafficLight> trafficLights = Lists.newArrayList(new TrafficLight(0, GeoPoint.ORIGO, null, null, TrafficLightState.OFF));

        TrafficLightGroup tlg = new TrafficLightGroup("0", programs, trafficLights);
        List<TrafficLightGroup> lights = Lists.newArrayList(tlg);
        return new ScenarioTrafficLightRegistration(0, lights, tlgLanes);
    }


    private void assertVehicleRegistration(String... applications) {
        assertNotNull(lastReceivedInteraction);
        assertTrue(lastReceivedInteraction instanceof VehicleRegistration);
        assertEquals(
                StringUtils.join(Lists.newArrayList(applications)),
                StringUtils.join(((VehicleRegistration) lastReceivedInteraction).getMapping().getApplications())
        );
        lastReceivedInteraction = null;
    }

    @Test
    public void initializeWithMappingFile_scenarioVehicleRegistration() throws Exception {
        final MappingAmbassador ambassador = createMappingAmbassadorWithMappingFile("mapping_config.json");
        ambassador.initialize(0, 100 * TIME.SECOND);

        ambassador.processInteraction(new ScenarioVehicleRegistration(0, "veh_0", new VehicleType("PKW")));
        assertVehicleRegistration();

        ambassador.processInteraction(new ScenarioVehicleRegistration(0, "veh_0", new VehicleType("electricPKW")));
        assertVehicleRegistration(
                "org.eclipse.mosaic.app.examples.eventprocessing.sampling.HelloWorldApp",
                "org.eclipse.mosaic.app.examples.eventprocessing.sampling.IntervalSamplingApp"
        );

        ambassador.processInteraction(new ScenarioVehicleRegistration(0, "veh_0", new VehicleType("UNKNOWN")));
        assertNull(lastReceivedInteraction);
    }

    @Test
    public void initializeWithMappingFile_scenarioVehicleRegistrationWithTypeDistribution() throws Exception {
        final MappingAmbassador ambassador = createMappingAmbassadorWithMappingFile("mapping_config.json");
        ambassador.initialize(0, 100 * TIME.SECOND);

        ambassador.processInteraction(new ScenarioVehicleRegistration(0, "veh_0", new VehicleType("myCarDistribution")));
        assertVehicleRegistration(
                "package.appA"
        );

        ambassador.processInteraction(new ScenarioVehicleRegistration(0, "veh_0", new VehicleType("myCarDistribution")));
        assertVehicleRegistration(
                "package.appA"
        );

        ambassador.processInteraction(new ScenarioVehicleRegistration(0, "veh_0", new VehicleType("myCarDistribution")));
        assertVehicleRegistration(
                "package.appB"
        );
    }

    @Test
    public void initializeWithMappingFile_agentRegistration() throws Exception {
        final MappingAmbassador ambassador = createMappingAmbassadorWithMappingFile("mapping_config_agent.json");
        ambassador.initialize(0, 100 * TIME.SECOND);

        assertNotNull(lastReceivedInteraction);
        assertTrue(lastReceivedInteraction instanceof VehicleTypesInitialization);
        lastReceivedInteraction = null;

        ambassador.advanceTime(0);
        assertNull(lastReceivedInteraction);

        ambassador.advanceTime(5 * TIME.SECOND);
        assertAgentRegistration("agent_0", SpeedUtils.kmh2ms(4), "org.eclipse.mosaic.fed.application.app.TestAgentApplication");

        ambassador.advanceTime(7 * TIME.SECOND);
        assertNull(lastReceivedInteraction);

        ambassador.advanceTime(10 * TIME.SECOND);
        assertAgentRegistration("agent_1", SpeedUtils.kmh2ms(2), "org.eclipse.mosaic.fed.application.app.SlowAgentApp");

        ambassador.advanceTime(11 * TIME.SECOND);
        assertNull(lastReceivedInteraction);
    }

    private void assertAgentRegistration(String name, double walkingSpeed, String application) {
        assertTrue(lastReceivedInteraction instanceof AgentRegistration);
        AgentMapping agentMapping = ((AgentRegistration) lastReceivedInteraction).getMapping();
        assertEquals(name, agentMapping.getName());
        assertTrue(agentMapping.getApplications().contains(application));
        assertEquals(walkingSpeed, agentMapping.getWalkingSpeed(), 0.001);
        lastReceivedInteraction = null;
    }

    @Before
    public void setup() throws Throwable {
        when(rtiMock.createRandomNumberGenerator()).thenReturn(new DefaultRandomNumberGenerator(989123));

        doAnswer(invocation -> {
            lastReceivedInteraction = invocation.getArgument(0);
            return null;
        }).when(rtiMock).triggerInteraction(ArgumentMatchers.any(Interaction.class));

        doAnswer(invocation -> {
            lastTimeAdvanceGrant = invocation.getArgument(0);
            return null;
        }).when(rtiMock).requestAdvanceTime(ArgumentMatchers.anyLong());
    }

    private MappingAmbassador createMappingAmbassadorWithMappingFile(String fileName) throws URISyntaxException {
        File mappingFile = new File(getClass().getClassLoader().getResource(fileName).toURI());
        AmbassadorParameter ambassadorParameter = new AmbassadorParameter("mapping", mappingFile);
        MappingAmbassador mappingAmbassador = new MappingAmbassador(ambassadorParameter);

        mappingAmbassador.setRtiAmbassador(rtiMock);
        return mappingAmbassador;
    }

}
