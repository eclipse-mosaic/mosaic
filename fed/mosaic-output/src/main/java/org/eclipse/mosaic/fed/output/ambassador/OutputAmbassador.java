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

import org.eclipse.mosaic.lib.math.MathUtils;
import org.eclipse.mosaic.lib.util.XmlUtils;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.AbstractFederateAmbassador;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.api.parameters.FederatePriority;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ambassador/federate to generate outputs of the simulation.
 */
public class OutputAmbassador extends AbstractFederateAmbassador {

    /**
     * Contains all required information about the running output generators.
     */
    @VisibleForTesting
    final Map<String, GeneratorInformation> generators = new HashMap<>();

    /**
     * interval after which data will be sent to all registered {@link AbstractOutputGenerator}s.
     */
    private long globalUpdateInterval;

    /**
     * next time at which data will be sent to all registered {@link AbstractOutputGenerator}s.
     */
    private long nextTimestep;

    /**
     * Flush every FLUSH_THRESHOLD interaction.
     */
    static final int FLUSH_THRESHOLD = 1024;

    /**
     * configuration directory and configuration file name.
     */
    private static final String XML_TAG_OUTPUT = "output";

    public OutputAmbassador(AmbassadorParameter ambassadorParameter) {
        super(ambassadorParameter);
    }

    @Override
    public boolean isTimeConstrained() {
        return true;
    }

    @Override
    public boolean isTimeRegulating() {
        return false;
    }

    @Override
    public void initialize(long startTime, long endTime) throws InternalFederateException {
        try {
            createOutputGenerator(loadConfiguration());

            this.nextTimestep = startTime + this.globalUpdateInterval;
            this.rti.requestAdvanceTime(this.nextTimestep, this.globalUpdateInterval, FederatePriority.LOWEST);
        } catch (IllegalValueException e) {
            throw new InternalFederateException(e);
        }
    }

    private Collection<OutputGeneratorLoader> loadConfiguration() {
        try {
            log.info("Initialize configuration of OutputAmbassador");

            final XMLConfiguration xml = XmlUtils.readXmlFromFile(ambassadorParameter.configuration);
            final int defaultUpdateInterval = 1;

            int globalUpdateIntervalInSeconds = 0;

            final List<HierarchicalConfiguration<ImmutableNode>> generatorXmlConfigurations = xml.configurationsAt(XML_TAG_OUTPUT);

            final Map<String, OutputGeneratorLoader> generatorLoader = new HashMap<>();

            for (HierarchicalConfiguration<ImmutableNode> generatorXmlConfiguration : generatorXmlConfigurations) {
                if (!ConfigHelper.isEnabled(generatorXmlConfiguration)) {
                    continue;
                }

                // create the output generator loader
                OutputGeneratorLoader loader;
                try {
                    final String loaderClassName = ConfigHelper.getConfigLoader(generatorXmlConfiguration);

                    @SuppressWarnings("unchecked")
                    Class<? extends OutputGeneratorLoader> loaderClass = (Class<? extends OutputGeneratorLoader>)
                            ClassLoader.getSystemClassLoader().loadClass(loaderClassName);

                    // create a new instance of the generator loader
                    loader = loaderClass.getDeclaredConstructor().newInstance();

                    loader.initialize(rti, generatorXmlConfiguration, ambassadorParameter.configuration.getParentFile());
                } catch (InternalFederateException | IllegalArgumentException | IllegalStateException e) {
                    throw e;
                } catch (Exception e) {
                    LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME).warn("Loading output generator configuration failed: " + e.getMessage());
                    log.error("Loading output generator configuration failed.", e);
                    continue;
                }

                if (generatorLoader.putIfAbsent(loader.getId(), loader) != null) {
                    // putIfAbsent returns the _previous_ value
                    log.error("Could not add output generator due to duplicate id={}", loader.getId());
                    throw new IllegalArgumentException("Could not add output generator due to duplicate id=" + loader.getId());
                }

                globalUpdateIntervalInSeconds = MathUtils.gcd(loader.getUpdateIntervalInSeconds(), globalUpdateIntervalInSeconds);
            }

            // if there are no output generators defined in in the configuration
            if (globalUpdateIntervalInSeconds == 0) {
                globalUpdateIntervalInSeconds = defaultUpdateInterval;
            }

            this.globalUpdateInterval = globalUpdateIntervalInSeconds * TIME.SECOND;

            // do not yet create output generators. This is done when the ambassadors initialize method is called
            return generatorLoader.values();
        } catch (InternalFederateException | IllegalArgumentException | IllegalStateException | IOException e) {
            throw new RuntimeException(e);
        } catch (Exception ex) {
            log.error("Error while initializing OutputAmbassador", ex);
            return Lists.newArrayList();
        }
    }

    @Override
    public void processTimeAdvanceGrant(long time) throws InternalFederateException {
        try {
            if (time != this.nextTimestep || this.globalUpdateInterval <= 0) {
                return;
            }

            for (GeneratorInformation generator : this.generators.values()) {
                log.trace("process time advance grant for id '{}' with count '{}'.", generator.getId(), generator.getUpdateUnitCount());

                int updateUnitCount = generator.decrementUpdateUnitCount();

                if (updateUnitCount == 0) {
                    flushInteractionsForOutputGenerator(generator);

                    generator.reloadUpdateUnitCount();
                }
            }

            this.nextTimestep += this.globalUpdateInterval;
            this.rti.requestAdvanceTime(this.nextTimestep, this.globalUpdateInterval, FederatePriority.LOWEST);
        } catch (Exception e) {
            throw new InternalFederateException(e);
        }
    }

    @Override
    public void processInteraction(Interaction interaction) {
        String type = interaction.getTypeId();
        long interactionTime = interaction.getTime();
        log.trace("Process interaction with type '{}' at time: {}", type, interactionTime);

        // Add interaction to the temporary storage of all the generators,
        // which accept this kind of interaction.
        for (GeneratorInformation generator : this.generators.values()) {
            // check if values are inside desired time interval or a valid ADDED* interaction
            if (generator.isInteractionRelevant(type, interactionTime)) {
                if (generator.getUpdateUnitCount() == 0) {
                    //handle interaction immediately if no update interval is set
                    generator.getGenerator().handleInteraction(interaction);
                } else {
                    generator.addInteraction(interaction);

                    if (generator.needsFlush()) {
                        flushInteractionsForOutputGenerator(generator);
                    }
                }
            }
        }
    }

    @Override
    public void finishSimulation() throws InternalFederateException {
        try {

            for (GeneratorInformation generator : this.generators.values()) {
                flushInteractionsForOutputGenerator(generator);
                generator.getGenerator().finish();
            }
        } catch (Exception e) {
            throw new InternalFederateException(e);
        }
    }

    private void flushInteractionsForOutputGenerator(GeneratorInformation generatorInformation) {
        try {
            log.trace("Flush interactions for generator with id {}", generatorInformation.getId());

            while (generatorInformation.hasNextInteraction()) {
                generatorInformation.processNextInteraction();
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not handle interactions: " + e.getMessage());
        }
    }

    /**
     * Create output generators according to incoming loaders..
     *
     * @param loaders the loaders to create output generators.
     */
    private void createOutputGenerator(Collection<OutputGeneratorLoader> loaders) {
        for (OutputGeneratorLoader config : loaders) {
            final AbstractOutputGenerator generator;
            try {
                generator = Validate.notNull(config.createOutputGenerator());
            } catch (Throwable e) {
                log.error("Could not create output generator", e);
                continue;
            }

            String id = config.getId();

            if (this.generators.containsKey(id)) {
                log.warn("Duplicated output generator \"" + id + "\" will be ignored.");
                continue;
            }

            this.generators.put(id, new GeneratorInformation(config, generator, (int) (this.globalUpdateInterval / TIME.SECOND)));

            log.info("Registered output generator '{}' for messages {}. Update interval={}", id, config.getInteractionTypes(), config.getUpdateIntervalInSeconds());
        }
    }

}
