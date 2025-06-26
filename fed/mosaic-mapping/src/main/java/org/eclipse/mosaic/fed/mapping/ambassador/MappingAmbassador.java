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

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.eclipse.mosaic.fed.mapping.ambassador.spawning.AgentSpawner.DEFAULT_WALKING_SPEED;

import org.eclipse.mosaic.fed.mapping.ambassador.weighting.FixedOrderSelector;
import org.eclipse.mosaic.fed.mapping.ambassador.weighting.StochasticSelector;
import org.eclipse.mosaic.fed.mapping.ambassador.weighting.WeightedSelector;
import org.eclipse.mosaic.fed.mapping.config.CMappingAmbassador;
import org.eclipse.mosaic.fed.mapping.config.CPrototype;
import org.eclipse.mosaic.interactions.mapping.AgentRegistration;
import org.eclipse.mosaic.interactions.mapping.VehicleRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioAgentRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioTrafficLightRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioVehicleRegistration;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;
import org.eclipse.mosaic.rti.api.AbstractFederateAmbassador;
import org.eclipse.mosaic.rti.api.FederateExecutor;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.config.CLocalHost.OperatingSystem;

import org.apache.commons.lang3.ObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * The Mapping-Ambassador
 * <p/>
 * The main work is done in the SpawningFramework. The CMappingAmbassador is the
 * class against which the JSON-String will be parsed and contains the whole
 * configuration of the mapping.
 * <p/>
 * In the 3.0 version of the mapping the departures in the db are ignored. The
 * only other data being used is the Traffic Light Information, found in the db.
 */
public class MappingAmbassador extends AbstractFederateAmbassador {

    /**
     * The framework doing the actual work.
     */
    private SpawningFramework framework;

    /**
     * Read the <code>CMappingAmbassador</code> from the configuration.
     */
    private final CMappingAmbassador mappingAmbassadorConfiguration;


    /**
     * Pointer to save the {@link ScenarioTrafficLightRegistration} when it arrives too early.
     */
    private ScenarioTrafficLightRegistration scenarioTrafficLightRegistration;

    private RandomNumberGenerator randomNumberGenerator;

    /**
     * Cache stochastic selectors to avoid unnecessary instantiations.
     */
    private final Map<String, WeightedSelector<CPrototype>> typeDistributionSelectors = new HashMap<>();

    /**
     * Constructor for the {@link MappingAmbassador}.
     *
     * @param ambassadorParameter the {@link AmbassadorParameter}s used for the configuration of the ambassador
     */
    public MappingAmbassador(AmbassadorParameter ambassadorParameter) {
        super(ambassadorParameter);

        try {
            if (!ambassadorParameter.configuration.exists()) {
                throw new IllegalStateException(
                        "Mapping configuration could not be found at " + ambassadorParameter.configuration.getPath()
                );
            }
            mappingAmbassadorConfiguration = new ObjectInstantiation<>(CMappingAmbassador.class, log)
                    .readFile(ambassadorParameter.configuration);
        } catch (InstantiationException e) {
            log.error("Configuration object could not be instantiated: ", e);
            throw new IllegalStateException(e);
        }

    }

    @Override
    protected void processInteraction(Interaction interaction) throws InternalFederateException {
        try {
            log.debug("processInteraction(): " + interaction.getTypeId());
            if (interaction.getTypeId().equals(ScenarioTrafficLightRegistration.TYPE_ID)) {
                handleInteraction((ScenarioTrafficLightRegistration) interaction);
            } else if (interaction.getTypeId().equals(ScenarioVehicleRegistration.TYPE_ID)) {
                handleInteraction((ScenarioVehicleRegistration) interaction);
            } else if (interaction.getTypeId().equals(ScenarioAgentRegistration.TYPE_ID)) {
                handleInteraction((ScenarioAgentRegistration) interaction);
            }
        } catch (Exception e) {
            log.error("Exception", e);
            throw new InternalFederateException(e);
        }
    }

    /**
     * Receive the TL-Interaction and pass it on to the framework.
     *
     * @param interaction The received interaction.
     */
    private void handleInteraction(ScenarioTrafficLightRegistration interaction) {
        log.info("Received TL-Interaction");
        this.scenarioTrafficLightRegistration = interaction;
        if (framework != null) {
            framework.setScenarioTrafficLightRegistration(scenarioTrafficLightRegistration);
        }
    }

    /**
     * Extracts the prototype from the {@link ScenarioVehicleRegistration} message and
     * sends a {@link VehicleRegistration} message with the application list configured
     * in the prototype.
     */
    private void handleInteraction(ScenarioVehicleRegistration scenarioVehicle) throws InternalFederateException {
        if (framework != null) {

            final List<CPrototype> typeDistribution = framework.getTypeDistributionByName(scenarioVehicle.getVehicleType().getName());
            if (!typeDistribution.isEmpty()) {
                WeightedSelector<CPrototype> selector = typeDistributionSelectors.get(scenarioVehicle.getVehicleType().getName());
                if (selector == null) {
                    if (mappingAmbassadorConfiguration.config != null && mappingAmbassadorConfiguration.config.fixedOrder) {
                        selector = new FixedOrderSelector<>(typeDistribution);
                    } else {
                        selector = new StochasticSelector<>(typeDistribution, randomNumberGenerator);
                    }
                    typeDistributionSelectors.put(scenarioVehicle.getVehicleType().getName(), selector);
                }
                final CPrototype selected = selector.nextItem();
                final CPrototype predefined = framework.getPrototypeByName(selected.name);
                sendVehicleRegistrationForScenarioVehicle(scenarioVehicle,
                        // use group/application list from predefined type, if not defined in type distribution
                        selected.group == null && predefined != null ? predefined.group : selected.group,
                        selected.applications == null && predefined != null ? predefined.applications : selected.applications
                );
            } else {
                final CPrototype prototype = framework.getPrototypeByName(scenarioVehicle.getVehicleType().getName());
                if (prototype == null) {
                    log.debug(
                            "There is no such prototype \"{}\" configured. No application will be mapped for vehicle \"{}\".",
                            scenarioVehicle.getVehicleType().getName(),
                            scenarioVehicle.getId()
                    );
                    return;
                }

                if (randomNumberGenerator.nextDouble() >= ObjectUtils.defaultIfNull(prototype.weight, 1.0)) {
                    log.debug(
                            "This scenario vehicle \"{}\" of prototype \"{}\" will not be equipped due to a weight condition of {}.",
                            scenarioVehicle.getId(),
                            scenarioVehicle.getVehicleType().getName(),
                            prototype.weight
                    );
                    return;
                }
                sendVehicleRegistrationForScenarioVehicle(scenarioVehicle, prototype.group, prototype.applications);
            }

        } else {
            log.warn("No mapping configuration available. Skipping {}", scenarioVehicle.getClass().getSimpleName());
        }
    }

    private void sendVehicleRegistrationForScenarioVehicle(
            ScenarioVehicleRegistration scenarioVehicle, String group, List<String> applications
    ) throws InternalFederateException {
        final VehicleRegistration vehicleRegistration = new VehicleRegistration(
                scenarioVehicle.getTime(),
                scenarioVehicle.getName(),
                group,
                applications,
                null,
                scenarioVehicle.getVehicleType()
        );
        try {
            log.info("Mapping Scenario Vehicle. time={}, name={}, type={}, apps={}",
                    framework.getTime(), scenarioVehicle.getName(), scenarioVehicle.getVehicleType().getName(), applications);
            rti.triggerInteraction(vehicleRegistration);
        } catch (Exception e) {
            throw new InternalFederateException(e);
        }
    }

    /**
     * Extracts the prototype from the {@link ScenarioAgentRegistration} interaction and
     * sends a {@link AgentRegistration} with the application list configured
     * in the prototype.
     */
    private void handleInteraction(ScenarioAgentRegistration scenarioAgent) throws InternalFederateException {
        if (framework != null) {

            final List<CPrototype> typeDistribution = framework.getTypeDistributionByName(scenarioAgent.getType());
            if (!typeDistribution.isEmpty()) {
                WeightedSelector<CPrototype> selector = typeDistributionSelectors.get(scenarioAgent.getType());
                if (selector == null) {
                    if (mappingAmbassadorConfiguration.config != null && mappingAmbassadorConfiguration.config.fixedOrder) {
                        selector = new FixedOrderSelector<>(typeDistribution);
                    } else {
                        selector = new StochasticSelector<>(typeDistribution, randomNumberGenerator);
                    }
                    typeDistributionSelectors.put(scenarioAgent.getType(), selector);
                }
                final CPrototype selected = selector.nextItem();
                final CPrototype predefined = framework.getPrototypeByName(selected.name);
                sendAgentRegistrationForScenarioAgent(scenarioAgent,
                        // use group/application list from predefined type, if not defined in type distribution
                        selected.group == null && predefined != null ? predefined.group : selected.group,
                        selected.applications == null && predefined != null ? predefined.applications : selected.applications,
                        selected.maxSpeed == null && predefined != null ? predefined.maxSpeed : selected.maxSpeed
                );
            } else {
                final CPrototype prototype = framework.getPrototypeByName(scenarioAgent.getType());
                if (prototype == null) {
                    log.debug(
                            "There is no such prototype \"{}\" configured. No application will be mapped for agent \"{}\".",
                            scenarioAgent.getType(),
                            scenarioAgent.getId()
                    );
                    return;
                }

                if (randomNumberGenerator.nextDouble() >= ObjectUtils.defaultIfNull(prototype.weight, 1.0)) {
                    log.debug(
                            "This scenario agent \"{}\" of prototype \"{}\" will not be equipped due to a weight condition of {}.",
                            scenarioAgent.getId(),
                            scenarioAgent.getType(),
                            prototype.weight
                    );
                    return;
                }
                sendAgentRegistrationForScenarioAgent(scenarioAgent, prototype.group, prototype.applications, prototype.maxSpeed);
            }

        } else {
            log.warn("No mapping configuration available. Skipping {}", scenarioAgent.getClass().getSimpleName());
        }
    }

    private void sendAgentRegistrationForScenarioAgent(
            ScenarioAgentRegistration scenarioAgent, String group, List<String> applications, Double walkingSpeed
    ) throws InternalFederateException {
        final AgentRegistration vehicleRegistration = new AgentRegistration(
                scenarioAgent.getTime(),
                scenarioAgent.getName(),
                group,
                null,
                null,
                applications,
                defaultIfNull(walkingSpeed, DEFAULT_WALKING_SPEED)
        );
        try {
            log.info("Mapping Scenario Agent. time={}, name={}, type={}, apps={}",
                    framework.getTime(), scenarioAgent.getName(), scenarioAgent.getType(), applications);
            rti.triggerInteraction(vehicleRegistration);
        } catch (Exception e) {
            throw new InternalFederateException(e);
        }
    }

    @Override
    protected void processTimeAdvanceGrant(long time) throws InternalFederateException {
        try {
            framework.timeAdvance(time, rti, randomNumberGenerator);
        } catch (InternalFederateException e) {
            InternalFederateException ex = new InternalFederateException("Error while processing timeAdvanceGrant", e);
            log.error("Error while processing timeAdvanceGrant", ex);
            throw ex;
        }
    }

    @Override
    public void initialize(long startTime, long endTime) throws InternalFederateException {
        super.initialize(startTime, endTime);
        try {
            randomNumberGenerator = rti.createRandomNumberGenerator();

            // Create the extended framework (a condensed representation,
            // enriched with functionality)
            framework = new SpawningFramework(mappingAmbassadorConfiguration, scenarioTrafficLightRegistration, rti, randomNumberGenerator);

            typeDistributionSelectors.clear();
            // Send out the VehicleTypesInitialization, publishing information
            // about the different vehicle types in the simulation
            rti.triggerInteraction(framework.generateVehicleTypesInitialization());

            // and register the initial time advance
            rti.requestAdvanceTime(0);
        } catch (IllegalValueException e) {
            InternalFederateException ex = new InternalFederateException(
                    "InvalidValueException while sending out VehicleTypesInitialization(after construction!)", e
            );
            log.error("InvalidValueException while sending out VehicleTypesInitialization(after construction!)", ex);
            throw ex;
        }
    }

    @Nonnull
    @Override
    public FederateExecutor createFederateExecutor(String host, int port, OperatingSystem os) {
        throw new UnsupportedOperationException("Mapping does not support remote startup.");
    }

    @Override
    public void connectToFederate(String host, int port) {
        log.info("connectToFederate()");
    }

    @Override
    public boolean isTimeConstrained() {
        return true;
    }

    @Override
    public boolean isTimeRegulating() {
        return true;
    }
}
