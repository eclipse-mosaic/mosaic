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

package org.eclipse.mosaic.test.junit;

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.sumo.ambassador.SumoGuiAmbassador;
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;
import org.eclipse.mosaic.lib.objects.v2x.etsi.EtsiPayloadConfiguration;
import org.eclipse.mosaic.lib.transform.GeoProjection;
import org.eclipse.mosaic.lib.util.junit.TestUtils;
import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;
import org.eclipse.mosaic.rti.MosaicComponentProvider;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.config.CHosts;
import org.eclipse.mosaic.rti.config.CLocalHost;
import org.eclipse.mosaic.starter.MosaicSimulation;
import org.eclipse.mosaic.starter.config.CRuntime;
import org.eclipse.mosaic.starter.config.CScenario;

import com.google.common.base.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MosaicSimulationRule extends TemporaryFolder {

    private static final Logger LOG = LoggerFactory.getLogger(MosaicSimulationRule.class);

    protected CHosts hostsConfiguration;
    protected CRuntime runtimeConfiguration;
    protected MosaicSimulation.ComponentProviderFactory componentProviderFactory = MosaicComponentProvider::new;
    protected Path logDirectory;

    protected String logLevelOverride = null;
    protected int watchdogInterval = 20;
    protected Consumer<CScenario> scenarioConfigManipulator = c -> {
    };
    protected List<Consumer<Path>> scenarioDirectoryManipulator = new ArrayList<>();
    protected Map<String, Consumer<CRuntime.CFederate>> federateManipulators = new HashMap<>();

    protected long timeout = 5 * TIME.MINUTE;

    /**
     * Create an environment for a MOSAIC Simulation which uses system default temporary-file
     * directory to create temporary resources.
     */
    public MosaicSimulationRule() {
        super();
    }

    public MosaicSimulationRule(String tempDirName) {
        this(new File(tempDirName));
    }

    public MosaicSimulationRule(File tempDir) {
        super(tempDir);
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            LOG.warn("Could not create temporary directory at {}", tempDir.getAbsolutePath());
        }
    }

    @Override
    protected void before() throws Throwable {
        super.before();

        hostsConfiguration = prepareHostsConfiguration();
        runtimeConfiguration = prepareRuntimeConfiguration();
    }

    public MosaicSimulationRule logLevelOverride(String logLevelOverride) {
        this.logLevelOverride = logLevelOverride;
        return this;
    }

    public MosaicSimulationRule federateConfigurationManipulator(String federate, Consumer<CRuntime.CFederate> federateManipulator) {
        this.federateManipulators.put(federate, federateManipulator);
        return this;
    }

    public MosaicSimulationRule scenarioConfigurationManipulator(Consumer<CScenario> manipulator) {
        this.scenarioConfigManipulator = manipulator;
        return this;
    }

    public MosaicSimulationRule addScenarioDirectoryManipulator(Consumer<Path> manipulator) {
        this.scenarioDirectoryManipulator.add(manipulator);
        return this;
    }

    public MosaicSimulationRule componentProviderFactory(MosaicSimulation.ComponentProviderFactory factory) {
        this.componentProviderFactory = factory;
        return this;
    }

    public MosaicSimulationRule watchdog(int watchdogInterval) {
        this.watchdogInterval = watchdogInterval;
        return this;
    }

    public MosaicSimulationRule timeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Debug feature: activate SUMO GUI to visualize vehicle movements.
     * DO NOT commit test with having this activated.
     */
    public MosaicSimulationRule activateSumoGui() {
        watchdog(0); // when using GUI watchdog pretty much has to be disabled
        getRuntimeConfiguration().federates.stream().filter(s -> s.id.equals("sumo")).forEach(
                s -> s.classname = SumoGuiAmbassador.class.getCanonicalName()
        );
        return this;
    }

    protected CHosts prepareHostsConfiguration() throws IOException {
        Path tmpDirectory = newFolder("tmp").toPath();
        CHosts hostsConfiguration = new CHosts();
        hostsConfiguration.localHosts.add(new CLocalHost(tmpDirectory.toAbsolutePath().toString()));
        return hostsConfiguration;
    }

    protected CRuntime prepareRuntimeConfiguration() throws IOException {
        try (InputStream resource = Objects.requireNonNull(getClass().getResourceAsStream("/runtime.json"),
                "Could not find runtime.json in classpath."
        )) {
            return new ObjectInstantiation<>(CRuntime.class).read(resource);
        } catch (InstantiationException e) {
            throw new IOException(e);
        }
    }

    public Path getLogDirectory() {
        return logDirectory;
    }

    public CRuntime getRuntimeConfiguration() {
        return runtimeConfiguration;
    }

    public MosaicSimulation.SimulationResult executeTestScenario(String folder) {
        return executeSimulation(Paths.get("..", "..", "scenarios", "test", folder));
    }

    public MosaicSimulation.SimulationResult executeTestScenario(String folder, String config) {
        return executeSimulation(Paths.get("..", "..", "scenarios", "test", folder), config);
    }

    public MosaicSimulation.SimulationResult executeWorkshopScenario(String folder) {
        return executeSimulation(Paths.get("..", "..", "scenarios", "workshop", folder));
    }

    public MosaicSimulation.SimulationResult executeReleaseScenario(String folder) {
        return executeSimulation(Paths.get("..", "..", "scenarios", "bundle", folder));
    }

    public MosaicSimulation.SimulationResult executeSimulation(Path scenarioDirectory) {
        return executeSimulation(scenarioDirectory, "scenario_config.json");
    }

    public MosaicSimulation.SimulationResult executeSimulation(Path scenarioDirectory, String config) {
        try {
            File f = scenarioDirectory.resolve(config).toFile();
            if (!f.exists() || !f.isFile()) {
                throw new FileNotFoundException("Scenario config file not found.");
            }
            return executeSimulation(
                    scenarioDirectory,
                    new ObjectInstantiation<>(CScenario.class).readFile(f)
            );
        } catch (InstantiationException | FileNotFoundException e) {
            LOG.error("", e);

            MosaicSimulation.SimulationResult result = new MosaicSimulation.SimulationResult();
            result.exception = e;
            result.success = false;
            return result;
        }
    }

    private MosaicSimulation.SimulationResult executeSimulation(Path scenarioDirectory, CScenario scenarioConfiguration) {
        MosaicSimulation simulation = null;
        try {
            final Path scenarioExecutionDirectory;
            if (!scenarioDirectoryManipulator.isEmpty()) {
                // if a test needs to manipulate a config file inside the scenario, we need to copy the scenario first to a temporary folder
                scenarioExecutionDirectory = super.newFolder(scenarioDirectory.getFileName().toString()).toPath();
                FileUtils.copyDirectory(scenarioDirectory.toFile(), scenarioExecutionDirectory.toFile());
                scenarioDirectoryManipulator.forEach(p -> p.accept(scenarioExecutionDirectory));
            } else {
                scenarioExecutionDirectory = scenarioDirectory;
            }
            scenarioConfigManipulator.accept(scenarioConfiguration);
            for (CRuntime.CFederate federate : runtimeConfiguration.federates) {
                federateManipulators.getOrDefault(federate.id, f -> {
                }).accept(federate);
            }

            String testName = getNameOfCallingTest(scenarioConfiguration.simulation.id);
            logDirectory = Paths.get("./log").resolve(testName);
            final Path logConfiguration = prepareLogConfiguration(logDirectory);

            simulation = new MosaicSimulation()
                    .setWatchdogInterval(watchdogInterval)
                    .setRuntimeConfiguration(runtimeConfiguration)
                    .setHostsConfiguration(hostsConfiguration)
                    .setLogbackConfigurationFile(logConfiguration)
                    .setLogLevelOverride(logLevelOverride)
                    .setComponentProviderFactory(componentProviderFactory);

            final MosaicSimulation simulationToRun = simulation; //lambda expression below requires final variable
            final MosaicSimulation.SimulationResult result = timeout(
                    () -> simulationToRun.runSimulation(scenarioExecutionDirectory, scenarioConfiguration)
            );
            return logResult(simulation != null && simulation.getLogger() != null ? simulation.getLogger() : LOG, result);
        } catch (Throwable e) {
            MosaicSimulation.SimulationResult result = new MosaicSimulation.SimulationResult();
            result.exception = e;
            result.success = false;
            return logResult(simulation != null && simulation.getLogger() != null ? simulation.getLogger() : LOG, result);
        } finally {
            resetSingletons();
        }
    }

    private String getNameOfCallingTest(String fallbackName) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 2; i < stackTrace.length; i++) {
            if (!stackTrace[i].getClassName().endsWith("Rule")) {
                return StringUtils.substringAfterLast(stackTrace[i].getClassName(), ".");
            }
        }
        return fallbackName;
    }

    private MosaicSimulation.SimulationResult logResult(Logger logger, MosaicSimulation.SimulationResult result) {
        if (!result.success && result.exception != null) {
            logger.error("Error during test execution.", result.exception);
        }
        return result;
    }

    protected Path prepareLogConfiguration(Path logDirectory) throws IOException {
        FileUtils.deleteQuietly(logDirectory.toFile());

        final Path logConfiguration = newFile("logback.xml").toPath();

        final InputStream logConfigurationResource = Objects.requireNonNull(getClass().getResourceAsStream("/logback.xml"),
                "Could not find logback.xml in classpath."
        );
        try (BufferedReader resource = new BufferedReader(new InputStreamReader(logConfigurationResource, Charsets.UTF_8));
             Writer writer = new OutputStreamWriter(Files.newOutputStream(logConfiguration), StandardCharsets.UTF_8)) {
            String line;
            while ((line = resource.readLine()) != null) {
                writer.write(StringUtils.replace(line, "${logDirectory}", logDirectory.toAbsolutePath().toString()));
            }
        }
        return logConfiguration;
    }

    protected void resetSingletons() {
        TestUtils.setPrivateField(GeoProjection.class, "instance", null);
        TestUtils.setPrivateField(IpResolver.class, "singleton", null);
        TestUtils.setPrivateField(EtsiPayloadConfiguration.class, "globalConfiguration", null);

        TestUtils.setPrivateField(SimulationKernel.SimulationKernel, "eventManager", null);
        TestUtils.setPrivateField(SimulationKernel.SimulationKernel, "interactable", null);
        TestUtils.setPrivateField(SimulationKernel.SimulationKernel, "navigation", null);
        TestUtils.setPrivateField(SimulationKernel.SimulationKernel, "centralPerceptionComponent", null);
        TestUtils.setPrivateField(SimulationKernel.SimulationKernel, "classLoader", null);
        TestUtils.setPrivateField(SimulationKernel.SimulationKernel, "randomNumberGenerator", null);
        TestUtils.setPrivateField(SimulationKernel.SimulationKernel, "configuration", null);
        TestUtils.setPrivateField(SimulationKernel.SimulationKernel, "configurationPath", null);
    }

    /**
     * Executes the given {@link Callable} and throws an {@link AssertionError} if
     * the callable could not be executed within the given timeout period.
     *
     * @param execution the code to be executed
     * @return the object the given {@link Callable} produced if the timeout has not been exceeded.
     * @throws AssertionError if the timeout has exceeded.
     */
    private MosaicSimulation.SimulationResult timeout(Supplier<MosaicSimulation.SimulationResult> execution) {
        ExecutorService executor = Executors.newCachedThreadPool();
        Future<MosaicSimulation.SimulationResult> future = executor.submit(execution::get);
        try {
            return future.get(this.timeout, TimeUnit.NANOSECONDS);
        } catch (Throwable e) {
            MosaicSimulation.SimulationResult result = new MosaicSimulation.SimulationResult();
            result.success = false;
            result.exception = e;
            return result;
        }
    }
}
