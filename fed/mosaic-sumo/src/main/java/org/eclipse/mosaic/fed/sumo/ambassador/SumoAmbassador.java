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

package org.eclipse.mosaic.fed.sumo.ambassador;

import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.SIM_TRAFFIC;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.SUMO_TRACI_BYTE_ARRAY_MESSAGE;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.UNKNOWN_INTERACTION;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.SumoVersion;
import org.eclipse.mosaic.fed.sumo.bridge.TraciClientBridge;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.TraciSimulationStepResult;
import org.eclipse.mosaic.fed.sumo.config.CSumo;
import org.eclipse.mosaic.fed.sumo.util.SumoVehicleTypesWriter;
import org.eclipse.mosaic.interactions.application.SumoTraciRequest;
import org.eclipse.mosaic.interactions.application.SumoTraciResponse;
import org.eclipse.mosaic.interactions.application.TaxiDispatch;
import org.eclipse.mosaic.interactions.mapping.AgentRegistration;
import org.eclipse.mosaic.interactions.mapping.VehicleRegistration;
import org.eclipse.mosaic.interactions.traffic.InductionLoopDetectorSubscription;
import org.eclipse.mosaic.interactions.traffic.LaneAreaDetectorSubscription;
import org.eclipse.mosaic.interactions.traffic.LanePropertyChange;
import org.eclipse.mosaic.interactions.traffic.TrafficLightStateChange;
import org.eclipse.mosaic.interactions.traffic.TrafficLightSubscription;
import org.eclipse.mosaic.interactions.traffic.VehicleRoutesInitialization;
import org.eclipse.mosaic.interactions.traffic.VehicleTypesInitialization;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignLaneAssignmentChange;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignRegistration;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignSpeedLimitChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleFederateAssignment;
import org.eclipse.mosaic.interactions.vehicle.VehicleLaneChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleParametersChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleResume;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteRegistration;
import org.eclipse.mosaic.interactions.vehicle.VehicleSensorActivation;
import org.eclipse.mosaic.interactions.vehicle.VehicleSightDistanceConfiguration;
import org.eclipse.mosaic.interactions.vehicle.VehicleSlowDown;
import org.eclipse.mosaic.interactions.vehicle.VehicleSpeedChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleStop;
import org.eclipse.mosaic.lib.objects.traffic.SumoTraciResult;
import org.eclipse.mosaic.lib.util.FileUtils;
import org.eclipse.mosaic.lib.util.ProcessLoggingThread;
import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;
import org.eclipse.mosaic.lib.util.scheduling.DefaultEventScheduler;
import org.eclipse.mosaic.lib.util.scheduling.EventScheduler;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.AbstractFederateAmbassador;
import org.eclipse.mosaic.rti.api.FederateExecutor;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.federatestarter.ExecutableFederateExecutor;
import org.eclipse.mosaic.rti.api.federatestarter.NopFederateExecutor;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.api.parameters.FederatePriority;
import org.eclipse.mosaic.rti.config.CLocalHost;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Implementation of a {@link AbstractFederateAmbassador} for the traffic simulator
 * SUMO. It allows to control the progress of the traffic simulation and
 * publishes {@link VehicleUpdates}.
 */
@NotThreadSafe
public class SumoAmbassador extends AbstractFederateAmbassador {

    /**
     * Configuration object.
     */
    CSumo sumoConfig;

    /**
     * Simulation time at which the positions are published next.
     */
    long nextTimeStep;

    /**
     * Connection to SUMO.
     */
    Bridge bridge;

    /**
     * Socket with which data is exchanged with SUMO.
     */
    Socket socket;

    /**
     * Indicates whether advance time is called for the first time.
     */
    private boolean firstAdvanceTime = true;

    /**
     * List of {@link Interaction}s which will be cached till a time advance occurs.
     */
    private final List<Interaction> interactionList = new ArrayList<>();


    /**
     * Sleep after each connection try. Unit: [ms].
     */
    private final static long SLEEP_AFTER_ATTEMPT = 1000L;

    /**
     * Command used to start Sumo.
     */
    FederateExecutor federateExecutor = null;

    /**
     * Last time of a call to advance time.
     */
    private long lastAdvanceTime = -1;


    /**
     * An event scheduler which is currently used to change the speed to
     * a given value after slowing down the vehicle.
     */
    private final EventScheduler eventScheduler = new DefaultEventScheduler();

    /**
     * Maximum amount of attempts to connect to SUMO.
     */
    private int connectionAttempts = 5;

    /**
     * Caches the {@link VehicleRoutesInitialization}-interaction until the {@link VehicleTypesInitialization}-interaction
     * is received.
     */
    private VehicleRoutesInitialization cachedVehicleRoutesInitialization;

    /**
     * Caches the {@link VehicleTypesInitialization}-interaction if it is received before
     * the {@link VehicleRoutesInitialization}-interaction.
     */
    private VehicleTypesInitialization cachedVehicleTypesInitialization;

    private final SumoRoutesHandler routesHandler = new SumoRoutesHandler();
    private final SumoVehiclesHandler vehiclesHandler = new SumoVehiclesHandler(routesHandler);
    private final SumoVehicleActionsHandler vehicleActionsHandler = new SumoVehicleActionsHandler(vehiclesHandler);
    private final SumoInfrastructureHandler infrastructureHandler = new SumoInfrastructureHandler();
    private final SumoTrafficLightsHandler trafficLightsHandler = new SumoTrafficLightsHandler();
    private final SumoPersonsHandler personsHandler = new SumoPersonsHandler();

    /**
     * Creates a new {@link SumoAmbassador} object.
     *
     * @param ambassadorParameter includes parameters for the sumo ambassador.
     */
    public SumoAmbassador(AmbassadorParameter ambassadorParameter) {
        super(ambassadorParameter);

        try {
            sumoConfig = new ObjectInstantiation<>(CSumo.class, log)
                    .readFile(ambassadorParameter.configuration);
        } catch (InstantiationException e) {
            log.error("Configuration object could not be instantiated. Using default ", e);
            sumoConfig = new CSumo();
        }

        log.info("sumoConfig.updateInterval: {}", sumoConfig.updateInterval);

        if (!findSumoConfigurationFile()) {
            log.error(LogStatements.MISSING_SUMO_CONFIG);
            throw new RuntimeException(LogStatements.MISSING_SUMO_CONFIG);
        }
        checkConfiguration();
        log.info("sumoConfig.sumoConfigurationFile: {}", sumoConfig.sumoConfigurationFile);
    }

    private void checkConfiguration() {
        if (sumoConfig.updateInterval <= 0) {
            throw new RuntimeException("Invalid sumo interval, should be >0");
        }
    }

    /**
     * Creates and sets new federate executor.
     *
     * @param host name of the host (as specified in /etc/hosts.json)
     * @param port port number to be used by this federate
     * @param os   operating system enum
     * @return FederateExecutor.
     */
    @Nonnull
    @Override
    public FederateExecutor createFederateExecutor(String host, int port, CLocalHost.OperatingSystem os) {
        // SUMO needs to start the federate by itself, therefore we need to store the federate starter locally and use it later
        federateExecutor = new ExecutableFederateExecutor(descriptor, getFromSumoHome("sumo"), getProgramArguments(port));
        return new NopFederateExecutor();
    }

    static String getFromSumoHome(String executable) {
        String sumoHome = System.getenv("SUMO_HOME");
        if (StringUtils.isNotBlank(sumoHome)) {
            return sumoHome + File.separator + "bin" + File.separator + executable;
        }
        return executable;
    }

    /**
     * Connects to SUMO using the given host with input stream.
     *
     * @param host The host on which the simulator is running.
     * @param in   This input stream is connected to the output stream of the
     *             started simulator process. The stream is only valid during
     *             this method call.
     * @param err  Error by connecting to federate.
     * @throws InternalFederateException Exception if an error occurred while starting SUMO.
     */
    @Override
    public void connectToFederate(String host, InputStream in, InputStream err) throws InternalFederateException {
        int port = -1;
        try {
            log.debug("connectToFederate(String host, InputStream in, InputStream err)");
            final String portTag = "port";
            final String success = "Starting server on port";
            final String error = "Error";

            BufferedReader sumoInputReader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            // hold the thread for a second to allow sumo to print possible error message to the error stream
            Thread.sleep(1000);
            while (((line = sumoInputReader.readLine()) != null)) {
                if (!line.isEmpty()) {
                    if (log.isDebugEnabled()) {
                        log.debug(line);
                    }
                    // SUMO is started, and the port is extracted from its
                    // output
                    if (line.contains(success)) {
                        String[] split = StringUtils.split(line, ' ');
                        for (int i = 0; i < split.length; i++) {
                            if (split[i].equals(portTag)) {
                                port = Integer.parseInt(split[i + 1]);
                                connectToFederate(host, port);
                            }
                        }
                        break;
                    }
                    // an error occurred while starting SUMO
                    if (line.contains(error)) {
                        log.error(line);
                        break;
                    }
                }
            }

            // Print errors, if socket was not created
            if (socket == null) {
                String myError = "Could not connect to socket: host: " + host + " port: " + port;
                log.error(myError);
                BufferedReader sumoErrorReader = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8));
                while (((line = sumoErrorReader.readLine()) != null)) {
                    if (!line.isEmpty()) {
                        log.error(line);
                    }
                }

                // specialized error message for missing port
                if (port == -1) {
                    throw new InternalFederateException("Could not read port from SUMO. SUMO seems to be crashed.");
                }

                throw new InternalFederateException(myError);
            }
        } catch (InterruptedException | IOException | RuntimeException e) {
            throw new InternalFederateException(e);
        }
    }

    /**
     * Connects to SUMO using the given host and port.
     *
     * @param host host on which SUMO is running.
     * @param port port on which TraCI is listening.
     */
    @Override
    public void connectToFederate(String host, int port) {
        try {
            log.debug("connectToFederate(String host, int port) method");
            socket = new Socket(host, port);

            // set performance preference to lowest latency
            socket.setPerformancePreferences(0, 100, 10);

            // disable Nagle's algorithm (TcpNoDelay Flag) to decrease latency even further
            socket.setTcpNoDelay(true);
            log.debug("Created TraCI Connection with the following specs: ");
            log.debug("    Receive Buffer Size: {}", socket.getReceiveBufferSize());
            log.debug("    Send Buffer Size: {}", socket.getSendBufferSize());
            log.debug("    Keep alive: {}", socket.getKeepAlive());
            log.debug("    TCP NoDelay: {}", socket.getTcpNoDelay());
        } catch (UnknownHostException ex) {
            log.error("Unknown host: {}", ex.getMessage());
        } catch (IOException ex) {
            log.warn("Error while connecting to SUMO. Retrying.");
            if (connectionAttempts-- > 0) {
                try {
                    Thread.sleep(SLEEP_AFTER_ATTEMPT);
                } catch (InterruptedException e) {
                    log.error("Could not execute Thread.sleep({}). Reason: {}", SLEEP_AFTER_ATTEMPT, e.getMessage());
                }
                connectToFederate(host, port);
            }
        }
    }

    /**
     * This method is called to tell the federate the start time and the end time.
     *
     * @param startTime Start time of the simulation run in nanoseconds.
     * @param endTime   End time of the simulation run in nanoseconds.
     * @throws InternalFederateException Exception is thrown if an error is occurred while execute of a federate.
     */
    @Override
    public void initialize(long startTime, long endTime) throws InternalFederateException {
        super.initialize(startTime, endTime);

        nextTimeStep = startTime;

        try {
            rti.requestAdvanceTime(nextTimeStep, 0, FederatePriority.higher(descriptor.getPriority()));
        } catch (IllegalValueException e) {
            log.error("Error during advanceTime request", e);
            throw new InternalFederateException(e);
        }
    }

    /**
     * Initializes the TraciClient. Does nothing if the {@link Bridge} is already initialized.
     *
     * @throws InternalFederateException Exception is thrown if an error is occurred while execution of a federate.
     */
    protected void initSumoConnection() throws InternalFederateException {
        if (bridge != null) {
            return;
        }

        try {
            // whenever initTraci is called the cached paths SHOULD be available
            // just to be sure make a failsafe
            bridge = new TraciClientBridge(sumoConfig, socket);

            if (bridge.getCurrentVersion().getApiVersion() < SumoVersion.LOWEST.getApiVersion()) {
                throw new InternalFederateException(
                        String.format("The installed version of SUMO ( <= %s) is not compatible with Eclipse MOSAIC."
                                        + " SUMO version >= %s is required.",
                                bridge.getCurrentVersion().getSumoVersion(),
                                SumoVersion.LOWEST.getSumoVersion())
                );
            }
            log.info("Current API version of SUMO is {} (=SUMO {})", bridge.getCurrentVersion().getApiVersion(),
                    bridge.getCurrentVersion().getSumoVersion());
        } catch (IOException e) {
            log.error("Error while trying to initialize SUMO ambassador.", e);
            throw new InternalFederateException("Could not initialize SUMO ambassador. Please see Traffic.log for details.", e);
        }
    }

    /**
     * This method processes the interactions.
     *
     * @param interaction The interaction that can be processed
     * @throws InternalFederateException Exception is thrown if an error is occurred while execute of a federate.
     */
    @Override
    public void processInteraction(Interaction interaction) throws InternalFederateException {

        if (interaction.getTypeId().equals(VehicleRoutesInitialization.TYPE_ID)) {
            handleRoutesInitialization((VehicleRoutesInitialization) interaction);
        } else if (interaction.getTypeId().equals(VehicleTypesInitialization.TYPE_ID)) {
            handleTypesInitialization((VehicleTypesInitialization) interaction);
        } else {
            interactionList.add(interaction);

            if (log.isTraceEnabled()) {
                log.trace("Got new interaction {} with time {} ns", interaction.getTypeId(), interaction.getTime());
            }
        }

    }


    /**
     * Extract data from the {@link VehicleRoutesInitialization} to SUMO.
     *
     * @param vehicleRoutesInitialization interaction containing vehicle departures and pre calculated routes for change route requests.
     * @throws InternalFederateException if something goes wrong in startSumoLocal(), initTraci(), completeRoutes() or InRouteFromTraci()
     */
    private void handleRoutesInitialization(VehicleRoutesInitialization vehicleRoutesInitialization) throws InternalFederateException {
        log.debug("Received VehicleRoutesInitialization: {}", vehicleRoutesInitialization.getTime());

        cachedVehicleRoutesInitialization = vehicleRoutesInitialization;
        if (cachedVehicleTypesInitialization != null) {
            vehiclesHandler.handleVehicleTypesInitialization(cachedVehicleTypesInitialization);
            routesHandler.addInitialRoutesFromRti(cachedVehicleRoutesInitialization);
        }
    }

    /**
     * Extract data from the {@link VehicleTypesInitialization} and forward to SUMO.
     *
     * @param vehicleTypesInitialization interaction containing vehicle types
     * @throws InternalFederateException if something goes wrong in startSumoLocal(), initTraci() or completeRoutes()
     */
    private void handleTypesInitialization(VehicleTypesInitialization vehicleTypesInitialization) throws InternalFederateException {
        log.debug("Received VehicleTypesInitialization");

        cachedVehicleTypesInitialization = vehicleTypesInitialization;
        sumoStartupProcedure();
    }

    private void sumoStartupProcedure() throws InternalFederateException {
        writeTypesFromRti(cachedVehicleTypesInitialization);
        startSumoLocal();
        initSumoConnection();
        initHandlers();
        routesHandler.readInitialRoutesFromTraci(nextTimeStep);
        if (cachedVehicleRoutesInitialization != null) {
            vehiclesHandler.handleVehicleTypesInitialization(cachedVehicleTypesInitialization);
            routesHandler.addInitialRoutesFromRti(cachedVehicleRoutesInitialization);
        }
    }

    private void initHandlers() {
        for (AbstractHandler handler : new AbstractHandler[]{
                routesHandler,
                vehiclesHandler,
                vehicleActionsHandler,
                trafficLightsHandler,
                infrastructureHandler,
                personsHandler
        }) {
            handler.initialize(this, descriptor, eventScheduler, sumoConfig, bridge, rti);
        }
    }

    /**
     * This processes all other types of interactions as part of {@link #processTimeAdvanceGrant}.
     *
     * @param interaction The interaction to process.
     * @param time        The time of the processed interaction.
     * @throws InternalFederateException Exception if the interaction time is not correct.
     */
    protected void processInteractionAdvanced(Interaction interaction, long time) throws InternalFederateException {
        // make sure the interaction is not in the future
        if (interaction.getTime() > time) {
            throw new InternalFederateException("Interaction time lies in the future:" + interaction.getTime() + ", current time:" + time);
        }

        if (interaction.getTypeId().equals(VehicleRegistration.TYPE_ID)) {
            vehiclesHandler.handleRegistration((VehicleRegistration) interaction);
        } else if (interaction.getTypeId().equals(VehicleRouteRegistration.TYPE_ID)) {
            routesHandler.handleRouteRegistration((VehicleRouteRegistration) interaction);
        } else if (interaction.getTypeId().equals(VehicleFederateAssignment.TYPE_ID)) {
            vehiclesHandler.handleFederateAssignment((VehicleFederateAssignment) interaction);
        } else if (interaction.getTypeId().equals(VehicleUpdates.TYPE_ID)) {
            vehiclesHandler.handleExternalVehicleUpdates((VehicleUpdates) interaction);
        } else if (interaction.getTypeId().equals(VehicleSlowDown.TYPE_ID)) {
            vehicleActionsHandler.handleSlowDown((VehicleSlowDown) interaction);
        } else if (interaction.getTypeId().equals(VehicleRouteChange.TYPE_ID)) {
            vehicleActionsHandler.handleRouteChange((VehicleRouteChange) interaction);
        } else if (interaction.getTypeId().equals(TrafficLightStateChange.TYPE_ID)) {
            trafficLightsHandler.handleStateChange((TrafficLightStateChange) interaction);
        } else if (interaction.getTypeId().equals(SumoTraciRequest.TYPE_ID)) {
            receiveInteraction((SumoTraciRequest) interaction);
        } else if (interaction.getTypeId().equals(VehicleLaneChange.TYPE_ID)) {
            vehicleActionsHandler.handleLaneChange((VehicleLaneChange) interaction);
        } else if (interaction.getTypeId().equals(VehicleStop.TYPE_ID)) {
            vehicleActionsHandler.handleStop((VehicleStop) interaction);
        } else if (interaction.getTypeId().equals(VehicleResume.TYPE_ID)) {
            vehicleActionsHandler.handleResume((VehicleResume) interaction);
        } else if (interaction.getTypeId().equals(VehicleParametersChange.TYPE_ID)) {
            vehicleActionsHandler.handleParametersChange((VehicleParametersChange) interaction);
        } else if (interaction.getTypeId().equals(VehicleSensorActivation.TYPE_ID)) {
            vehicleActionsHandler.handleSensorActivation((VehicleSensorActivation) interaction);
        } else if (interaction.getTypeId().equals(VehicleSpeedChange.TYPE_ID)) {
            vehicleActionsHandler.handleSpeedChange((VehicleSpeedChange) interaction);
        } else if (interaction.getTypeId().equals(VehicleSightDistanceConfiguration.TYPE_ID)) {
            vehicleActionsHandler.handleSightDistanceConfiguration((VehicleSightDistanceConfiguration) interaction);
        } else if(interaction.getTypeId().equals(TaxiDispatch.TYPE_ID)) {
			vehicleActionsHandler.handleTaxiDispatch((TaxiDispatch) interaction);
		} else if (interaction.getTypeId().equals(InductionLoopDetectorSubscription.TYPE_ID)) {
            infrastructureHandler.handleDetectorSubscription((InductionLoopDetectorSubscription) interaction);
        } else if (interaction.getTypeId().equals(LaneAreaDetectorSubscription.TYPE_ID)) {
            infrastructureHandler.handleDetectorSubscription((LaneAreaDetectorSubscription) interaction);
        } else if (interaction.getTypeId().equals(TrafficLightSubscription.TYPE_ID)) {
            trafficLightsHandler.handleSubscription((TrafficLightSubscription) interaction);
        } else if (interaction.getTypeId().equals(LanePropertyChange.TYPE_ID)) {
            infrastructureHandler.handleLanePropertyChange((LanePropertyChange) interaction);
        } else if (interaction.getTypeId().equals(TrafficSignRegistration.TYPE_ID)) {
            infrastructureHandler.handleTrafficSignRegistration((TrafficSignRegistration) interaction);
        } else if (interaction.getTypeId().equals(TrafficSignSpeedLimitChange.TYPE_ID)) {
            infrastructureHandler.handleSpeedLimitChange((TrafficSignSpeedLimitChange) interaction);
        } else if (interaction.getTypeId().equals(TrafficSignLaneAssignmentChange.TYPE_ID)) {
            infrastructureHandler.handleLaneAssignmentChange((TrafficSignLaneAssignmentChange) interaction);
        } else if (interaction.getTypeId().equals(AgentRegistration.TYPE_ID)) {
            personsHandler.handleRegistration((AgentRegistration) interaction);
        } else {
            log.warn(UNKNOWN_INTERACTION, interaction.getTypeId());
        }
    }

    /**
     * Forwards a {@link SumoTraciRequest}.
     *
     * @param sumoTraciRequest The {@link SumoTraciRequest}
     */
    private synchronized void receiveInteraction(SumoTraciRequest sumoTraciRequest) throws InternalFederateException {
        try {
            if (bridge instanceof TraciClientBridge traci) {
                log.info(
                        "{} at simulation time {}: " + "length=\"{}\", id=\"{}\" data={}",
                        SUMO_TRACI_BYTE_ARRAY_MESSAGE,
                        TIME.format(sumoTraciRequest.getTime()),
                        sumoTraciRequest.getCommandLength(),
                        sumoTraciRequest.getRequestId(),
                        sumoTraciRequest.getCommand()
                );

                SumoTraciResult sumoTraciResult =
                        traci.writeByteArrayMessage(sumoTraciRequest.getRequestId(), sumoTraciRequest.getCommand());
                rti.triggerInteraction(new SumoTraciResponse(sumoTraciRequest.getTime(), sumoTraciResult));
            } else {
                log.warn("SumoTraciRequests are not supported.");
            }
        } catch (InternalFederateException | IllegalValueException e) {
            throw new InternalFederateException(e);
        }
    }

    /**
     * Starts the SUMO binary locally.
     */
    void startSumoLocal() throws InternalFederateException {
        if (!descriptor.isToStartAndStop()) {
            return;
        }

        File dir = new File(descriptor.getHost().workingDirectory, descriptor.getId());

        log.info("Start Federate local");
        log.info("Directory: {}", dir);

        try {
            Process p = federateExecutor.startLocalFederate(dir);

            connectToFederate("localhost", p.getInputStream(), p.getErrorStream());
            // read error output of process in an extra thread
            new ProcessLoggingThread("sumo", p.getInputStream(), log::info).start();
            new ProcessLoggingThread("sumo", p.getErrorStream(), log::error).start();

        } catch (FederateExecutor.FederateStarterException e) {
            log.error("Error while executing command: {}", federateExecutor.toString());
            throw new InternalFederateException("Error while starting Sumo: " + e.getLocalizedMessage());
        }
    }

    @Override
    public synchronized void processTimeAdvanceGrant(long time) throws InternalFederateException {
        if (bridge instanceof TraciClientBridge && socket == null) {
            throw new InternalFederateException("Error during advance time (" + time + "): Sumo not yet ready.");
        }

        // send cached interactions
        for (Interaction interaction : interactionList) {
            processInteractionAdvanced(interaction, time);
        }
        interactionList.clear();

        if (time < nextTimeStep) {
            // process time advance only if time is equal or greater than the next simulation time step
            return;
        }

        if (time > lastAdvanceTime) {
            // actually add vehicles in sumo, before we reach the next advance time
            vehiclesHandler.flushNotYetAddedVehicles(lastAdvanceTime);
        }

        // schedule events, e.g. change speed events
        int scheduled = eventScheduler.scheduleEvents(time);
        log.debug("scheduled {} events at time {}", scheduled, TIME.format(time));

        try {
            if (log.isTraceEnabled()) {
                log.trace(SIM_TRAFFIC, time);
            }

            if (firstAdvanceTime) {
                // if SUMO was not started by the RTI but manually, the connection to SUMO is initiated now
                initSumoConnection();
                initHandlers();
                trafficLightsHandler.initializeTrafficLights(time);
                firstAdvanceTime = false;
            }

            vehiclesHandler.setExternalVehiclesToLatestPositions(time);
            TraciSimulationStepResult simulationStepResult = bridge.getSimulationControl().simulateUntil(time);
            log.trace("Stepped simulation until {} ns", time);

            VehicleUpdates vehicleUpdates = simulationStepResult.vehicleUpdates();
            vehiclesHandler.removeExternalVehiclesFromUpdates(vehicleUpdates);
            routesHandler.propagateNewRoutes(vehicleUpdates, time);
            vehiclesHandler.propagateSumoVehiclesToRti(time);
            personsHandler.propagatePersonsToRti(time);

            nextTimeStep += sumoConfig.updateInterval * TIME.MILLI_SECOND;
            simulationStepResult.vehicleUpdates().setNextUpdate(nextTimeStep);

            rti.triggerInteraction(vehicleUpdates);
            // person updates will be sent in the form of AgentUpdates
            rti.triggerInteraction(simulationStepResult.personUpdates());
            rti.triggerInteraction(simulationStepResult.trafficDetectorUpdates());
            rti.triggerInteraction(simulationStepResult.trafficLightUpdates());
			rti.triggerInteraction(simulationStepResult.taxiUpdates());

            rti.requestAdvanceTime(nextTimeStep, 0, FederatePriority.higher(descriptor.getPriority()));

            lastAdvanceTime = time;
        } catch (InternalFederateException | IOException | IllegalValueException e) {
            log.error("Error during advanceTime({})", time, e);
            throw new InternalFederateException(e);
        }
    }

    @Override
    public void finishSimulation() {
        log.info("Closing SUMO connection");
        if (bridge != null) {
            bridge.close();
        }
        if (federateExecutor != null) {
            try {
                federateExecutor.stopLocalFederate();
            } catch (FederateExecutor.FederateStarterException e) {
                log.warn("Could not properly stop federate");
            }
        }
        log.info("Finished simulation");
    }


    /**
     * Find the first configuration file.
     *
     * @param path The path to find a configuration file.
     * @return The first found file path.
     */
    private static File findLocalConfigurationFilename(String path) {
        Collection<File> matchingFileSet = FileUtils.searchForFilesOfType(new File(path), ".sumocfg");
        return Iterables.getFirst(matchingFileSet, null);
    }

    /**
     * Check if a {@code sumoConfigurationFile} is available. If not, try to find and
     * set the {@code sumoConfigurationFile}.
     *
     * @return True if a file set or could be found and set.
     */
    private boolean findSumoConfigurationFile() {
        if (sumoConfig.sumoConfigurationFile == null) {
            final String cfgDir = ambassadorParameter.configuration.getParent();
            log.debug("Try to find configuration file");
            File foundFile = findLocalConfigurationFilename(cfgDir);
            if (foundFile != null) {
                log.info("Found a SUMO configuration file: {}", foundFile);
                sumoConfig.sumoConfigurationFile = foundFile.getName();
            } else {
                log.error("No SUMO configuration file found.");
                return false;
            }
        }
        return true;
    }

    List<String> getProgramArguments(int port) {
        double stepSize = (double) sumoConfig.updateInterval / 1000.0;
        log.info("Simulation step size is {} sec.", stepSize);

        List<String> args = Lists.newArrayList(
                "-c", sumoConfig.sumoConfigurationFile,
                "-v",
                "--remote-port", Integer.toString(port),
                "--step-length", String.format(Locale.ENGLISH, "%.2f", stepSize)
        );

        // if SUMO_HOME is not set, the XML input validation in SUMO might fail as no XSDs are available.
        // Therefore, we disable XML validation if SUMO_HOME is not set.
        if (StringUtils.isBlank(System.getenv("SUMO_HOME"))) {
            args.add("--xml-validation");
            args.add("never");
        }

        if (sumoConfig.additionalSumoParameters != null) {
            args.addAll(Arrays.asList(StringUtils.split(sumoConfig.additionalSumoParameters.trim(), " ")));
        }
        return args;
    }



    /**
     * Writes a new SUMO additional-file based on the registered vehicle types.
     *
     * @param typesInit Interaction contains predefined vehicle types.
     */
    private void writeTypesFromRti(VehicleTypesInitialization typesInit) {
        File dir = new File(descriptor.getHost().workingDirectory, descriptor.getId());
        String subDir = new File(sumoConfig.sumoConfigurationFile).getParent();
        if (StringUtils.isNotBlank(subDir)) {
            dir = new File(dir, subDir);
        }
        SumoVehicleTypesWriter sumoVehicleTypesWriter = new SumoVehicleTypesWriter(dir, sumoConfig);
        // stores the *.add.xml file to the working directory. this file is required for SUMO to run
        sumoVehicleTypesWriter
                .addVehicleTypes(typesInit.getTypes())
                .store();
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
