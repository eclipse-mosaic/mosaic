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

package org.eclipse.mosaic.starter;

import org.eclipse.mosaic.lib.geo.UtmPoint;
import org.eclipse.mosaic.lib.geo.UtmZone;
import org.eclipse.mosaic.lib.objects.UnitNameGenerator;
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;
import org.eclipse.mosaic.lib.transform.GeoProjection;
import org.eclipse.mosaic.lib.transform.UtmGeoCalculator;
import org.eclipse.mosaic.lib.transform.Wgs84Projection;
import org.eclipse.mosaic.lib.util.SocketUtils;
import org.eclipse.mosaic.lib.util.XmlUtils;
import org.eclipse.mosaic.rti.MosaicComponentParameters;
import org.eclipse.mosaic.rti.MosaicComponentProvider;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.ComponentProvider;
import org.eclipse.mosaic.rti.api.FederateAmbassador;
import org.eclipse.mosaic.rti.api.FederationManagement;
import org.eclipse.mosaic.rti.api.MosaicVersion;
import org.eclipse.mosaic.rti.api.TimeManagement;
import org.eclipse.mosaic.rti.api.WatchDog;
import org.eclipse.mosaic.rti.api.federatestarter.DockerFederateExecutor;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.api.parameters.FederateDescriptor;
import org.eclipse.mosaic.rti.api.parameters.FederatePriority;
import org.eclipse.mosaic.rti.api.parameters.InteractionDescriptor;
import org.eclipse.mosaic.rti.api.parameters.JavaFederateParameters;
import org.eclipse.mosaic.rti.config.CHosts;
import org.eclipse.mosaic.rti.config.CIpResolver;
import org.eclipse.mosaic.rti.config.CLocalHost;
import org.eclipse.mosaic.rti.config.CProjection;
import org.eclipse.mosaic.starter.config.CRuntime;
import org.eclipse.mosaic.starter.config.CScenario;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MosaicSimulation {

    private static final Path LOG_DIRECTORY = Paths.get("logs");

    private static final Path FEDERATE_DIRECTORY = Paths.get("bin", "fed");

    private ComponentProviderFactory componentProviderFactory = MosaicComponentProvider::new;
    private CRuntime runtimeConfiguration;
    private CHosts hostsConfiguration;

    private Path logbackConfigurationFile;
    private String logLevelOverride;

    private double realtimeBrake = 0;
    private int watchdogInterval = 0;
    private int externalWatchdogPort = 0;

    private Logger log = null;
    private ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    private String federationId;
    private String simulationId;

    public MosaicSimulation setRuntimeConfiguration(CRuntime runtimeConfiguration) {
        this.runtimeConfiguration = runtimeConfiguration;
        return this;
    }

    public MosaicSimulation setHostsConfiguration(CHosts hostsConfiguration) {
        this.hostsConfiguration = hostsConfiguration;
        return this;
    }

    public MosaicSimulation setLogbackConfigurationFile(Path logbackConfigurationFile) {
        this.logbackConfigurationFile = logbackConfigurationFile;
        return this;
    }

    public MosaicSimulation setLogLevelOverride(String logLevelOverride) {
        this.logLevelOverride = logLevelOverride;
        return this;
    }

    public MosaicSimulation setWatchdogInterval(int watchdogInterval) {
        this.watchdogInterval = watchdogInterval;
        return this;
    }

    public MosaicSimulation setExternalWatchdogPort(int externalWatchdogPort) {
        this.externalWatchdogPort = externalWatchdogPort;
        return this;
    }

    public MosaicSimulation setComponentProviderFactory(ComponentProviderFactory componentProviderFactory) {
        this.componentProviderFactory = componentProviderFactory;
        return this;
    }

    public MosaicSimulation setRealtimeBrake(double realtimeBrake) {
        this.realtimeBrake = realtimeBrake;
        return this;
    }

    public MosaicSimulation setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    /**
     * Executes the simulation based on the given scenarioConfiguration.
     *
     * @param scenarioDirectory     the directory of the scenario files
     * @param scenarioConfiguration the scenario configuration descriptor
     * @return the result of the simulation
     */
    public SimulationResult runSimulation(Path scenarioDirectory, CScenario scenarioConfiguration) {
        Thread.currentThread().setContextClassLoader(classLoader);

        // in almost all cases, mapping needs to be activated
        scenarioConfiguration.federates.putIfAbsent("mapping", true);

        final SimulationResult simulationResult = new SimulationResult();
        ComponentProvider federation = null;
        try {
            Validate.notNull(scenarioConfiguration, "Scenario configuration must not be null.");
            Validate.notNull(runtimeConfiguration, "Runtime configuration must not be null.");
            Validate.notNull(hostsConfiguration, "Hosts configuration must not be null.");

            Validate.isTrue(Files.exists(scenarioDirectory), "Scenario directory at '" + scenarioDirectory + "' does not exist.");

            federationId = Validate.notBlank(scenarioConfiguration.simulation.id,
                    "No simulation id given in scenario configuration file"
            );
            final Calendar cal = Calendar.getInstance();
            final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
            simulationId = dateFormat.format(cal.getTime()) + "-" + federationId;
            prepareLogging(simulationId);
            printMosaicVersion();

            final MosaicComponentParameters simParams = readSimulationParameters(scenarioConfiguration)
                    .setNumberOfThreads(runtimeConfiguration.threads);

            initializeSingletons(scenarioConfiguration);

            federation = createFederation(simParams);

            final List<FederateDescriptor> federates = loadFederates(
                    scenarioDirectory,
                    scenarioConfiguration
            );
            addFederates(federation, federates);

            federation.getTimeManagement().runSimulation();

            stopFederation(federation);

            simulationResult.success = true;

        } catch (Throwable e) {
            if (federation != null) {
                try {
                    federation.getTimeManagement().finishSimulationRun(-1);
                } catch (Throwable e2) {
                    log.error("Could not finish simulation after error.", e2);
                }
            }

            stopFederation(federation);
            simulationResult.exception = e;
        }
        return simulationResult;
    }

    private void initializeSingletons(CScenario scenarioConfiguration) {
        GeoProjection.initialize(createTransformation(scenarioConfiguration));
        GeoProjection.getInstance().setGeoCalculator(new UtmGeoCalculator());
        IpResolver.setSingleton(createIpResolver(scenarioConfiguration));
        UnitNameGenerator.reset();
    }

    protected void printMosaicVersion() {
        LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME).info("Running Eclipse MOSAIC {} on Java JRE v{} ({})",
                MosaicVersion.get(),
                System.getProperty("java.version"),
                System.getProperty("java.vendor")
        );
    }


    /**
     * Reads simulation parameters from the scenario configuration and creates a
     * simulation parameters object. Additionally, the projection and IP resolver
     * is initialized.
     *
     * @param scenarioConfiguration the scenario configuration
     * @throws IllegalArgumentException if wrong parameters have been passed
     */
    private MosaicComponentParameters readSimulationParameters(CScenario scenarioConfiguration) throws IllegalArgumentException {

        Validate.notNull(scenarioConfiguration.simulation, "Missing tag <simulation> in configuration file");
        Validate.notNull(scenarioConfiguration.simulation.id, "Missing simulation.id in configuration file");
        Validate.isTrue(scenarioConfiguration.simulation.duration > 0, "Missing end time in configuration file.");

        return new MosaicComponentParameters()
                .setRealTimeBreak(realtimeBrake)
                .setFederationId(federationId)
                .setEndTime(scenarioConfiguration.simulation.duration * TIME.SECOND)
                .setRandomSeed(scenarioConfiguration.simulation.randomSeed);
    }

    private GeoProjection createTransformation(CScenario scenarioConfiguration) {
        final CProjection projectionConfig = scenarioConfiguration.simulation.projectionConfig;
        try {
            Validate.notNull(projectionConfig, "No projection configuration given.");
            Validate.notNull(projectionConfig.centerCoordinates,
                    "Invalid Wgs84UtmTransform configuration: no center coordinates given");
            Validate.notNull(projectionConfig.cartesianOffset,
                    "Invalid Wgs84UtmTransform configuration: no cartesian offset given");

            UtmPoint origin = UtmPoint.eastNorth(
                    UtmZone.from(projectionConfig.centerCoordinates),
                    -projectionConfig.cartesianOffset.getX(),
                    -projectionConfig.cartesianOffset.getY()
            );
            return new Wgs84Projection(origin)
                    .failIfOutsideWorld()
                    .useZoneOfUtmOrigin();
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while processing the Projection configuration.", e);
        }
    }

    private IpResolver createIpResolver(CScenario scenarioConfiguration) {
        CIpResolver ipResolverConfig = scenarioConfiguration.simulation.networkConfig;
        Validate.notNull(ipResolverConfig, "No network configuration given.");
        try {
            return new IpResolver(ipResolverConfig);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while processing the IPResolverConfig.", e);
        }
    }

    /**
     * Initializes all federates required for the simulation based on the given scenario configuration.
     *
     * @param scenarioDirectory     the path to the directory of the scenario
     * @param scenarioConfiguration the configuration of the scenario
     * @return list of descriptors for all federates that have to be joined
     */
    private List<FederateDescriptor> loadFederates(Path scenarioDirectory, CScenario scenarioConfiguration) throws Exception {

        // get list of federates which have been activated in the scenario configuration
        final Set<String> activeFederates = scenarioConfiguration.federates.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        final List<FederateDescriptor> federates = new ArrayList<>();

        // load all federates skipping inactive ones
        for (CRuntime.CFederate federate : this.runtimeConfiguration.federates) {
            Validate.notNull(federate.id, "federate.id must not be empty");
            Validate.notNull(federate.classname, "federate.classname must not be empty");

            if (!activeFederates.remove(federate.id)) {
                continue;
            }

            final FederateDescriptor descriptor = loadFederate(scenarioDirectory, federate);

            initializeFederate(federate, descriptor);

            federates.add(descriptor);
        }

        if (!activeFederates.isEmpty()) {
            throw new IllegalArgumentException(
                    "The following federates have been activated but are not known: ["
                            + StringUtils.join(activeFederates, ",")
                            + "]. Please check your configuration files.");
        }

        return federates;
    }

    private FederateDescriptor loadFederate(Path scenarioDirectory, CRuntime.CFederate federate) throws Exception {
        final Path configurationDirectory = scenarioDirectory.resolve(federate.id);

        final Path configurationFile = StringUtils.isNotBlank(federate.configuration)
                ? configurationDirectory.resolve(federate.configuration)
                : configurationDirectory;

        Class<? extends FederateAmbassador> ambassadorClass =
                (Class<? extends FederateAmbassador>) classLoader.loadClass(federate.classname);

        final AmbassadorParameter ambassadorParameter = new AmbassadorParameter(federate.id, configurationFile.toFile());

        // instantiate ambassador from class name
        final FederateAmbassador ambassador;

        // Obtain the constructor of the specialized ambassador class (xxxAmbassador())
        Constructor<? extends FederateAmbassador> declaredConstructor = ambassadorClass.getDeclaredConstructor(AmbassadorParameter.class);
        ambassador = declaredConstructor.newInstance(ambassadorParameter);

        // create new descriptor and set common properties
        final int readPriority = federate.priority;
        if (FederatePriority.isInRange(readPriority)) {
            throw new IllegalArgumentException("Provided priority " + readPriority + " lies out of allowed range: "
                    + FederatePriority.LOWEST + " - " + FederatePriority.HIGHEST + " (lowest priority - highest priority)");
        }
        final FederateDescriptor descriptor = new FederateDescriptor(federate.id, ambassador, (byte) readPriority);
        ambassador.setFederateDescriptor(descriptor);

        descriptor.setJavaFederateParameters(readJavaFederateParameters(federate));
        descriptor.setInteractions(getInteractionDescriptors(federate));

        descriptor.setDeployAndUndeploy(federate.deploy);
        if (descriptor.isToDeployAndUndeploy()) {
            descriptor.setBinariesDir(FEDERATE_DIRECTORY.resolve(federate.id).toFile());
            descriptor.setConfigDir(configurationDirectory.toFile());

            if (federate.configurationDeployPath != null) {
                descriptor.setConfigTargetPath(Paths.get(federate.configurationDeployPath));
            }
        }
        descriptor.setStartAndStop(federate.start);

        final CLocalHost host = Validate.notNull(
                hostsConfiguration.getHostById(federate.host), "No suitable host found for federate " + federate.id
        );
        descriptor.setHost(host);

        return descriptor;
    }

    private void initializeFederate(CRuntime.CFederate federate, FederateDescriptor descriptor) {

        final FederateAmbassador ambassador = descriptor.getAmbassador();
        final CLocalHost host = descriptor.getHost();

        if (descriptor.isToStartAndStop()) {

            if (StringUtils.isNotEmpty(federate.dockerImage)) {
                final String container = federate.id + '-' + simulationId;
                DockerFederateExecutor dockerFederateExecutor = descriptor.getAmbassador()
                        .createDockerFederateExecutor(federate.dockerImage, host.operatingSystem).setContainerName(container);
                descriptor.setFederateExecutor(dockerFederateExecutor);
            } else {
                int port = federate.port;
                if (port == 0) {
                    port = SocketUtils.findFreePort();
                    log.info("Federate {}: No port given. Using free port: {}", descriptor.getId(), port);
                }

                descriptor.setFederateExecutor(descriptor.getAmbassador().createFederateExecutor(host.address, port, host.operatingSystem));
            }

        } else {
            // connect only, if address and port are given
            if (federate.port > 0) {
                ambassador.connectToFederate(host.address, federate.port);
            }
        }
    }

    private List<InteractionDescriptor> getInteractionDescriptors(CRuntime.CFederate federateConfiguration) {
        final List<InteractionDescriptor> interactions = new ArrayList<>();
        federateConfiguration.subscriptions.forEach(entry -> interactions.add(new InteractionDescriptor(entry)));
        return interactions;
    }

    private JavaFederateParameters readJavaFederateParameters(CRuntime.CFederate federateConfiguration) {
        final JavaFederateParameters defaultParameters = JavaFederateParameters.defaultParameters();
        final int javaMemorySizeXmx =
                ObjectUtils.defaultIfNull(federateConfiguration.javaMemorySizeXmx, defaultParameters.getJavaMaxmimumMemoryMb());
        final String customJavaArgument =
                StringUtils.defaultIfBlank(federateConfiguration.javaCustomArgument, defaultParameters.getCustomJavaArgument());

        final JavaFederateParameters javaFederateParameters = new JavaFederateParameters(
                javaMemorySizeXmx, customJavaArgument
        );
        federateConfiguration.javaClasspathEntries.forEach(javaFederateParameters::addJavaClasspathEntry);
        return javaFederateParameters;
    }

    /**
     * Creates a federation based on the given parameters.
     *
     * @param simulationParams the simulation parameters
     * @return a fully initialized {@link ComponentProvider} instance ready for simulation
     */
    private ComponentProvider createFederation(final MosaicComponentParameters simulationParams) {
        final ComponentProvider componentProvider = componentProviderFactory.createComponentProvider(simulationParams);

        FederationManagement federation = componentProvider.getFederationManagement();
        federation.createFederation();

        TimeManagement time = componentProvider.getTimeManagement();

        if (watchdogInterval > 0) {
            final WatchDog watchDogThread = time.startWatchDog(federationId, watchdogInterval);
            federation.setWatchdog(watchDogThread);
        }

        if (externalWatchdogPort > 0) {
            log.debug("External watchdog port: " + externalWatchdogPort);
            time.startExternalWatchDog(federationId, externalWatchdogPort);
        }
        return componentProvider;
    }

    /**
     * Adds the list of {@link FederateDescriptor}s to the given federation.
     */
    private void addFederates(ComponentProvider federation, List<FederateDescriptor> federates) throws Exception {
        for (FederateDescriptor descriptor : federates) {
            federation.getFederationManagement().addFederate(descriptor);
            federation.getInteractionManagement().subscribeInteractions(descriptor.getId(), descriptor.getInteractions());
            federation.getTimeManagement().updateWatchDog();
        }
    }

    private void stopFederation(ComponentProvider federation) {
        try {
            if (federation != null) {
                federation.getFederationManagement().stopFederation();
            }
        } catch (Throwable e2) {
            if (log != null) {
                log.error("Could not close federation", e2);
            }
        }
    }

    /**
     * Initialize logback and set properties used in logback.xml
     *
     * @param simulationId simulation id is used for creating an according log folder
     */
    private void prepareLogging(String simulationId) {
        // prepare logging directory
        String logDirectoryValue = readLogFolderFromLogback(logbackConfigurationFile);

        //if the read value is the standard value, then it should be one scenario we are about to simulate,
        //so we just go the standard way
        Path logDirectory;
        if ("${logDirectory}".equals(logDirectoryValue) || logDirectoryValue == null) {
            logDirectory = LOG_DIRECTORY.resolve("log-" + simulationId);
        } else {
            logDirectory = Paths.get(logDirectoryValue);
        }

        // actually apply directory to context and init configuration
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator jc = new JoranConfigurator();
        jc.setContext(loggerContext);

        loggerContext.reset(); // throw away default configuration
        loggerContext.putProperty("logDirectory", logDirectory.toString());

        // do config based on logback xml
        try (InputStream loggerInput = loadResource(logbackConfigurationFile)) {
            jc.doConfigure(loggerInput);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not load logger configuration from " + logbackConfigurationFile, e);
        }

        // override each logger to given loglevel:
        if (this.logLevelOverride != null) {
            Level overrideLevel = Level.toLevel(logLevelOverride);
            if (overrideLevel != null) {
                for (ch.qos.logback.classic.Logger logger : loggerContext.getLoggerList()) {
                    logger.setLevel(overrideLevel);
                }
            }
        }

        // and finally read possibly overwritten directory from a FileAppender
        // -> first try to find the FileAppender for MosaicLog (out base logging file)
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("ROOT");
        Appender<ILoggingEvent> mosaicAppender = rootLogger.getAppender("MosaicLog");
        if (mosaicAppender != null) {
            // now try to find out if the logging directory was changed in the logback.xml by
            // removing the saved logDirectory and checking for an expected file separator!
            Path actualLoggingPath = Paths.get(((FileAppender<ILoggingEvent>) mosaicAppender).getFile());
            String suffix = actualLoggingPath.toString().replace(logDirectory.toString(), "");

            // we have to check both possible separators individually as we don't know what a user has configured
            if (!suffix.startsWith("\\") && !suffix.startsWith("/")) {
                // try to guess the correct first occurrence of a separator
                int index = suffix.indexOf("\\");
                int alternIndex = suffix.indexOf("/");
                if (index < 0 || (alternIndex >= 0 && alternIndex < index)) {
                    index = alternIndex;
                }

                if (index >= 0) {
                    // get everything between the removed logDirectory and the first separator ...
                    suffix = suffix.substring(0, index);

                    // ... and concat it with the logDirectory as the new property in the logger context
                    loggerContext.putProperty("logDirectory", logDirectory + suffix);
                }
            }
        }

        // initialize logger first after everything was prepared
        log = LoggerFactory.getLogger("MosaicStarter");

        if (mosaicAppender == null) {
            return;
        }
        // try to avoid a race condition between the exit of the JVM and logbacks attempt to log
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // sleep a bit to make sure all logs are flushed
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    // be quiet
                }
                ((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
            }
        }, "ShutdownHookLogback"));
    }

    /**
     * Reads log directory that is given in the "value" attribute of "property" element in the particular logback configuration.
     *
     * @param logbackConfigPath logback configuration from which the logDirectory property is read from
     * @return path to the log directory as String
     */
    private String readLogFolderFromLogback(Path logbackConfigPath) {

        try (InputStream logbackConfig = loadResource(logbackConfigPath)) {
            return XmlUtils.getValueFromXpath(XmlUtils.readXmlFromStream(logbackConfig),
                    "//property[@name=\"logDirectory\"]/@value", null);
        } catch (Exception e) {
            getLogger().warn("Could not read the log folder from " + logbackConfigPath, e);
            return null;
        }
    }


    private InputStream loadResource(Path path) {
        InputStream resourceStream = this.getClass().getClassLoader().getResourceAsStream(path.toString());
        if (resourceStream != null) {
            return resourceStream;
        }
        try {
            return new FileInputStream(path.toFile());
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Could not load configuration file from " + path);
        }
    }

    public Logger getLogger() {
        return log;
    }

    /**
     * Factory, which creates the {@link ComponentProvider} to be used for the simulation run.
     */
    public interface ComponentProviderFactory {
        ComponentProvider createComponentProvider(MosaicComponentParameters simulationParams);
    }

    /**
     * Provides technical properties of the simulation run, such as the duration
     * or its success status.
     */
    public static class SimulationResult {
        public boolean success;
        public Throwable exception;
    }

}
