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

import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.INDUCTION_LOOP_DETECTOR_SUBSCRIPTION;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.LANE_AREA_DETECTOR_SUBSCRIPTION;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.LANE_PROPERTY_CHANGE;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.SIM_TRAFFIC;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.SUMO_TRACI_BYTE_ARRAY_MESSAGE;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.TRAFFIC_LIGHTS_STATE_CHANGE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.TRAFFIC_LIGHT_SUBSCRIPTION;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.UNKNOWN_INTERACTION;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_LANE_CHANGE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_PARAM_CHANGE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_RESUME_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_ROUTE_CHANGE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_SIGHT_DISTANCE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_SLOWDOWN_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_SPEED_CHANGE_REQ;
import static org.eclipse.mosaic.fed.sumo.ambassador.LogStatements.VEHICLE_STOP_REQ;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.SumoVersion;
import org.eclipse.mosaic.fed.sumo.bridge.TraciClientBridge;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.SumoLaneChangeMode;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.SumoSpeedMode;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.TraciSimulationStepResult;
import org.eclipse.mosaic.fed.sumo.bridge.traci.VehicleSetRemove;
import org.eclipse.mosaic.fed.sumo.config.CSumo;
import org.eclipse.mosaic.fed.sumo.util.SumoVehicleClassMapping;
import org.eclipse.mosaic.fed.sumo.util.TrafficSignManager;
import org.eclipse.mosaic.interactions.application.SumoTraciRequest;
import org.eclipse.mosaic.interactions.application.SumoTraciResponse;
import org.eclipse.mosaic.interactions.mapping.AgentRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioAgentRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioTrafficLightRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioVehicleRegistration;
import org.eclipse.mosaic.interactions.traffic.InductionLoopDetectorSubscription;
import org.eclipse.mosaic.interactions.traffic.LaneAreaDetectorSubscription;
import org.eclipse.mosaic.interactions.traffic.LanePropertyChange;
import org.eclipse.mosaic.interactions.traffic.TrafficLightStateChange;
import org.eclipse.mosaic.interactions.traffic.TrafficLightSubscription;
import org.eclipse.mosaic.interactions.traffic.TrafficLightUpdates;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignLaneAssignmentChange;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignRegistration;
import org.eclipse.mosaic.interactions.trafficsigns.TrafficSignSpeedLimitChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleFederateAssignment;
import org.eclipse.mosaic.interactions.vehicle.VehicleLaneChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleLaneChange.VehicleLaneChangeMode;
import org.eclipse.mosaic.interactions.vehicle.VehicleParametersChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleResume;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteRegistration;
import org.eclipse.mosaic.interactions.vehicle.VehicleSensorActivation;
import org.eclipse.mosaic.interactions.vehicle.VehicleSensorActivation.SensorType;
import org.eclipse.mosaic.interactions.vehicle.VehicleSightDistanceConfiguration;
import org.eclipse.mosaic.interactions.vehicle.VehicleSlowDown;
import org.eclipse.mosaic.interactions.vehicle.VehicleSpeedChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleStop;
import org.eclipse.mosaic.lib.enums.VehicleClass;
import org.eclipse.mosaic.lib.enums.VehicleStopMode;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.objects.traffic.SumoTraciResult;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroupInfo;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;
import org.eclipse.mosaic.lib.objects.trafficsign.TrafficSignLaneAssignment;
import org.eclipse.mosaic.lib.objects.trafficsign.TrafficSignSpeed;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleParameter;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleRoute;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;
import org.eclipse.mosaic.lib.util.FileUtils;
import org.eclipse.mosaic.lib.util.ProcessLoggingThread;
import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;
import org.eclipse.mosaic.lib.util.scheduling.DefaultEventScheduler;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.util.scheduling.EventProcessor;
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
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.awt.Color;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Implementation of a {@link AbstractFederateAmbassador} for the traffic simulator
 * SUMO. It allows to control the progress of the traffic simulation and
 * publishes {@link VehicleUpdates}.
 */
@NotThreadSafe
public abstract class AbstractSumoAmbassador extends AbstractFederateAmbassador implements EventProcessor {

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
     * List of vehicles that are simulated externally.
     */
    protected final Map<String, ExternalVehicleState> externalVehicles = new HashMap<>();

    /**
     * Set containing all vehicles, that have been added from the RTI e.g. using the Mapping file.
     */
    final Set<String> vehiclesAddedViaRti = new HashSet<>();

    /**
     * Set containing all vehicles, that have been added using the SUMO route file.
     */
    final Set<String> vehiclesAddedViaRouteFile = new HashSet<>();

    /**
     * Manages traffic signs to be added as POIs to SUMO (e.g. for visualization)
     */
    private final TrafficSignManager trafficSignManager;

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
     * This contains references to all {@link VehicleRoute}s that are known to SUMO.
     */
    final HashMap<String, VehicleRoute> routes = new HashMap<>();

    /**
     * Creates a new {@link AbstractSumoAmbassador} object.
     *
     * @param ambassadorParameter includes parameters for the sumo ambassador.
     */
    AbstractSumoAmbassador(AmbassadorParameter ambassadorParameter) {
        super(ambassadorParameter);

        try {
            sumoConfig = new ObjectInstantiation<>(CSumo.class, log)
                    .readFile(ambassadorParameter.configuration);
        } catch (InstantiationException e) {
            log.error("Configuration object could not be instantiated. Using default ", e);
            sumoConfig = new CSumo();
        }

        log.info("sumoConfig.updateInterval: " + sumoConfig.updateInterval);
        log.info("sumoConfig.sumoConfigurationFile: " + sumoConfig.sumoConfigurationFile);

        if (!findSumoConfigurationFile()) {
            log.error(LogStatements.MISSING_SUMO_CONFIG);
            throw new RuntimeException(LogStatements.MISSING_SUMO_CONFIG);
        }
        checkConfiguration();
        log.info("sumoConfig.sumoConfigurationFile: " + sumoConfig.sumoConfigurationFile);

        trafficSignManager = new TrafficSignManager(sumoConfig.trafficSignLaneWidth);

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
            log.debug("    Receive Buffer Size: " + socket.getReceiveBufferSize());
            log.debug("    Send Buffer Size: " + socket.getSendBufferSize());
            log.debug("    Keep alive: " + socket.getKeepAlive());
            log.debug("    TCP NoDelay: " + socket.getTcpNoDelay());
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
     * @param startTime Start time of the simulation run in nano seconds.
     * @param endTime   End time of the simulation run in nano seconds.
     * @throws InternalFederateException Exception is thrown if an error is occurred while execute of a federate.
     */
    @Override
    public void initialize(long startTime, long endTime) throws InternalFederateException {
        super.initialize(startTime, endTime);

        nextTimeStep = startTime;

        // If simulation is to start and stop, do nothing and postpone the thing
        // to do until a VehicleRoutesInitialization interaction was received
        // Else connect to the TraCI server, if Sumo is already started no
        // change of the route file is necessary
        if (descriptor == null) {
            initSumoConnection();
        }

        try {
            rti.requestAdvanceTime(nextTimeStep, 0, FederatePriority.higher(descriptor.getPriority()));
        } catch (IllegalValueException e) {
            log.error("Error during advanceTime request", e);
            throw new InternalFederateException(e);
        }
    }

    /**
     * Initializes the TraciClient.
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

        try {
            File sumoWorkingDir = new File(descriptor.getHost().workingDirectory, descriptor.getId());
            trafficSignManager.configure(bridge, sumoWorkingDir);
        } catch (Exception e) {
            log.error("Could not load TrafficSignManager. No traffic signs will be displayed.");
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
        interactionList.add(interaction);

        if (log.isTraceEnabled()) {
            log.trace("Got new interaction {} with time {} ns", interaction.getTypeId(), interaction.getTime());
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

        if (interaction.getTypeId().equals(VehicleFederateAssignment.TYPE_ID)) {
            receiveInteraction((VehicleFederateAssignment) interaction);
        } else if (interaction.getTypeId().equals(VehicleUpdates.TYPE_ID)) {
            receiveInteraction((VehicleUpdates) interaction);
        } else if (interaction.getTypeId().equals(VehicleSlowDown.TYPE_ID)) {
            receiveInteraction((VehicleSlowDown) interaction);
        } else if (interaction.getTypeId().equals(VehicleRouteChange.TYPE_ID)) {
            receiveInteraction((VehicleRouteChange) interaction);
        } else if (interaction.getTypeId().equals(TrafficLightStateChange.TYPE_ID)) {
            receiveInteraction((TrafficLightStateChange) interaction);
        } else if (interaction.getTypeId().equals(SumoTraciRequest.TYPE_ID)) {
            receiveInteraction((SumoTraciRequest) interaction);
        } else if (interaction.getTypeId().equals(VehicleLaneChange.TYPE_ID)) {
            receiveInteraction((VehicleLaneChange) interaction);
        } else if (interaction.getTypeId().equals(VehicleStop.TYPE_ID)) {
            receiveInteraction((VehicleStop) interaction);
        } else if (interaction.getTypeId().equals(VehicleResume.TYPE_ID)) {
            receiveInteraction((VehicleResume) interaction);
        } else if (interaction.getTypeId().equals(VehicleParametersChange.TYPE_ID)) {
            receiveInteraction((VehicleParametersChange) interaction);
        } else if (interaction.getTypeId().equals(VehicleSensorActivation.TYPE_ID)) {
            receiveInteraction((VehicleSensorActivation) interaction);
        } else if (interaction.getTypeId().equals(VehicleSpeedChange.TYPE_ID)) {
            receiveInteraction((VehicleSpeedChange) interaction);
        } else if (interaction.getTypeId().equals(VehicleSightDistanceConfiguration.TYPE_ID)) {
            receiveInteraction((VehicleSightDistanceConfiguration) interaction);
        } else if (interaction.getTypeId().equals(InductionLoopDetectorSubscription.TYPE_ID)) {
            receiveInteraction((InductionLoopDetectorSubscription) interaction);
        } else if (interaction.getTypeId().equals(LaneAreaDetectorSubscription.TYPE_ID)) {
            receiveInteraction((LaneAreaDetectorSubscription) interaction);
        } else if (interaction.getTypeId().equals(TrafficLightSubscription.TYPE_ID)) {
            this.receiveInteraction((TrafficLightSubscription) interaction);
        } else if (interaction.getTypeId().equals(LanePropertyChange.TYPE_ID)) {
            receiveInteraction((LanePropertyChange) interaction);
        } else if (interaction.getTypeId().equals(TrafficSignRegistration.TYPE_ID)) {
            this.receiveInteraction((TrafficSignRegistration) interaction);
        } else if (interaction.getTypeId().equals(TrafficSignSpeedLimitChange.TYPE_ID)) {
            this.receiveInteraction((TrafficSignSpeedLimitChange) interaction);
        } else if (interaction.getTypeId().equals(TrafficSignLaneAssignmentChange.TYPE_ID)) {
            this.receiveInteraction((TrafficSignLaneAssignmentChange) interaction);
        } else if (interaction.getTypeId().equals(AgentRegistration.TYPE_ID)) {
            this.receiveInteraction((AgentRegistration) interaction);
        } else {
            log.warn(UNKNOWN_INTERACTION + interaction.getTypeId());
        }
    }

    /**
     * Extract data from received {@link VehicleFederateAssignment} interactions and add vehicle to list of externally simulated vehicles.
     *
     * @param vehicleFederateAssignment interaction indicating that a vehicle is simulated externally.
     */
    private synchronized void receiveInteraction(VehicleFederateAssignment vehicleFederateAssignment) {
        if (!vehicleFederateAssignment.getAssignedFederate().equals(getId())
                && !externalVehicles.containsKey(vehicleFederateAssignment.getVehicleId())) {
            externalVehicles.put(vehicleFederateAssignment.getVehicleId(), new ExternalVehicleState());
        }
    }

    /**
     * Extract data from received {@link VehicleUpdates} interaction and apply
     * updates of externally simulated vehicles to SUMO via TraCI calls.
     *
     * @param vehicleUpdates interaction indicating vehicle updates of a simulator
     */
    private synchronized void receiveInteraction(VehicleUpdates vehicleUpdates) throws InternalFederateException {
        if (vehicleUpdates == null || vehicleUpdates.getSenderId().equals(getId())) {
            return;
        }

        for (VehicleData vehicle : Iterables.concat(vehicleUpdates.getAdded(), vehicleUpdates.getUpdated())) {
            ExternalVehicleState externalVehicleState = externalVehicles.get(vehicle.getName());
            if (externalVehicleState != null) {
                externalVehicleState.setLastMovementInfo(vehicle);
            }
        }

        for (String removed : vehicleUpdates.getRemovedNames()) {
            if (externalVehicles.containsKey(removed)) {
                bridge.getSimulationControl().removeVehicle(removed, VehicleSetRemove.Reason.ARRIVED);
            }
        }
    }

    /**
     * Extract data from received {@link VehicleSlowDown} interaction and forward to SUMO.
     *
     * @param vehicleSlowDown interaction indicating that a vehicle has to slow down
     */
    private synchronized void receiveInteraction(VehicleSlowDown vehicleSlowDown) throws InternalFederateException {
        if (externalVehicles.containsKey(vehicleSlowDown.getVehicleId())) {
            return;
        }
        if (log.isInfoEnabled()) {
            log.info(
                    "{} at simulation time {}: vehicleId=\"{}\", targetSpeed={}m/s, interval={}ms",
                    VEHICLE_SLOWDOWN_REQ,
                    TIME.format(vehicleSlowDown.getTime()),
                    vehicleSlowDown.getVehicleId(),
                    vehicleSlowDown.getSpeed(),
                    vehicleSlowDown.getDuration()
            );
        }
        bridge.getVehicleControl()
                .slowDown(vehicleSlowDown.getVehicleId(), vehicleSlowDown.getSpeed(), vehicleSlowDown.getDuration());
    }

    /**
     * Extract data from received {@link VehicleStop} interaction and forward to SUMO.
     *
     * @param vehicleStop interaction indicating that a vehicle has to stop.
     */
    private synchronized void receiveInteraction(VehicleStop vehicleStop) {
        if (externalVehicles.containsKey(vehicleStop.getVehicleId())) {
            return;
        }
        try {
            final IRoadPosition stopPos = vehicleStop.getStopPosition();
            if (log.isInfoEnabled()) {
                log.info(
                        "{} at simulation time {}: vehicleId=\"{}\", edgeId=\"{}\", position=\"{}\", laneIndex={}, duration={}, "
                                + "stopMode={}",
                        VEHICLE_STOP_REQ,
                        TIME.format(vehicleStop.getTime()),
                        vehicleStop.getVehicleId(),
                        stopPos.getConnectionId(),
                        stopPos.getOffset(),
                        stopPos.getLaneIndex(),
                        vehicleStop.getDuration(),
                        vehicleStop.getVehicleStopMode()
                );
            }
            if (vehicleStop.getVehicleStopMode() == VehicleStopMode.NOT_STOPPED) {
                log.warn("Stop mode {} is not supported", vehicleStop.getVehicleStopMode());
            }

            stopVehicleAt(vehicleStop.getVehicleId(), stopPos, vehicleStop.getVehicleStopMode(), vehicleStop.getDuration());
        } catch (InternalFederateException e) {
            log.warn("Vehicle {} could not be stopped", vehicleStop.getVehicleId());
        }
    }

    /**
     * Extract data from received {@link VehicleResume} interaction and forward to SUMO.
     *
     * @param vehicleResume interaction indicating that a stopped vehicle has to resume
     */
    private synchronized void receiveInteraction(VehicleResume vehicleResume) throws InternalFederateException {
        if (externalVehicles.containsKey(vehicleResume.getVehicleId())) {
            return;
        }
        if (log.isInfoEnabled()) {
            log.info("{} at simulation time {}: " + "vehicleId=\"{}\"",
                    VEHICLE_RESUME_REQ, TIME.format(vehicleResume.getTime()), vehicleResume.getVehicleId());
        }

        bridge.getVehicleControl().resume(vehicleResume.getVehicleId());
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
     * Extract data from received {@link VehicleRouteChange} interaction and forward to SUMO.
     *
     * @param vehicleRouteChange interaction indicating that a vehicle has to change its route
     */
    private synchronized void receiveInteraction(VehicleRouteChange vehicleRouteChange) throws InternalFederateException {
        if (log.isInfoEnabled()) {
            VehicleData lastKnownVehicleData = bridge.getSimulationControl().getLastKnownVehicleData(vehicleRouteChange.getVehicleId());
            log.info(
                    "{} at simulation time {}: vehicleId=\"{}\", newRouteId={}, current edge: {}",
                    VEHICLE_ROUTE_CHANGE_REQ, TIME.format(vehicleRouteChange.getTime()),
                    vehicleRouteChange.getVehicleId(), vehicleRouteChange.getRouteId(),
                    lastKnownVehicleData != null ? lastKnownVehicleData.getRoadPosition().getConnectionId() : null
            );
        }

        bridge.getVehicleControl().setRouteById(vehicleRouteChange.getVehicleId(), vehicleRouteChange.getRouteId());

        if (sumoConfig.highlights.contains(CSumo.HIGHLIGHT_CHANGE_ROUTE)) {
            bridge.getVehicleControl().highlight(vehicleRouteChange.getVehicleId(), Color.BLUE);
        }
    }

    /**
     * Extract data from received {@link VehicleLaneChange} interaction and forward to SUMO.
     *
     * @param vehicleLaneChange interaction indicating that a vehicle has to change its lane
     * @throws InternalFederateException Exception is thrown if an error occurred while converting to number.
     */
    private synchronized void receiveInteraction(VehicleLaneChange vehicleLaneChange) throws InternalFederateException {
        if (externalVehicles.containsKey(vehicleLaneChange.getVehicleId())) {
            return;
        }
        try {
            VehicleLaneChange.VehicleLaneChangeMode mode = vehicleLaneChange.getVehicleLaneChangeMode();

            if (log.isInfoEnabled()) {
                log.info("{} at simulation time {}: vehicleId=\"{}\", mode={}, lane={}",
                        VEHICLE_LANE_CHANGE_REQ,
                        TIME.format(vehicleLaneChange.getTime()),
                        vehicleLaneChange.getVehicleId(),
                        mode + (mode == VehicleLaneChangeMode.BY_INDEX ? "(" + vehicleLaneChange.getTargetLaneIndex() + ")" : ""),
                        vehicleLaneChange.getCurrentLaneId()
                );
            }

            int targetLaneId;
            int laneId;

            switch (mode) {
                case BY_INDEX:
                    targetLaneId = vehicleLaneChange.getTargetLaneIndex();
                    break;
                case TO_LEFT:
                    laneId = vehicleLaneChange.getCurrentLaneId();
                    targetLaneId = laneId + 1;
                    break;
                case TO_RIGHT:
                    laneId = vehicleLaneChange.getCurrentLaneId();
                    targetLaneId = laneId - 1;
                    break;
                case TO_RIGHTMOST:
                    targetLaneId = 0;
                    break;
                case STAY:
                    log.info("This lane is in use already - change lane will not be performed ");
                    return;
                default:
                    log.warn("VehicleLaneChange failed: unsupported lane change mode.");
                    return;
            }
            bridge.getVehicleControl()
                    .changeLane(vehicleLaneChange.getVehicleId(), Math.max(0, targetLaneId), vehicleLaneChange.getDuration());

            if (sumoConfig.highlights.contains(CSumo.HIGHLIGHT_CHANGE_LANE)) {
                VehicleData vehicleData = bridge.getSimulationControl().getLastKnownVehicleData(vehicleLaneChange.getVehicleId());
                if (vehicleData.getRoadPosition().getLaneIndex() != targetLaneId) {
                    bridge.getVehicleControl().highlight(vehicleLaneChange.getVehicleId(), Color.RED);
                }
            }

        } catch (NumberFormatException e) {
            throw new InternalFederateException(e);
        }
    }

    /**
     * Extract data from received {@link TrafficLightStateChange} interaction and forward
     * to SUMO.
     *
     * @param trafficLightStateChange Interaction indicates the state of traffic lights.
     * @throws InternalFederateException Exception if a invalid value is used.
     */
    private synchronized void receiveInteraction(TrafficLightStateChange trafficLightStateChange) throws InternalFederateException {
        try {
            log.info(TRAFFIC_LIGHTS_STATE_CHANGE_REQ);

            String trafficLightGroupId = trafficLightStateChange.getTrafficLightGroupId();

            switch (trafficLightStateChange.getParameterType()) {

                case ChangePhase:
                    log.info(
                            "Changing the current phase of traffic light group '{}' to phase with index '{}'",
                            trafficLightGroupId, trafficLightStateChange.getPhaseIndex()
                    );
                    bridge.getTrafficLightControl().setPhaseIndex(trafficLightGroupId, trafficLightStateChange.getPhaseIndex());
                    break;

                case RemainingDuration:
                    double durationInSeconds = trafficLightStateChange.getPhaseRemainingDuration() / 1000; //ms -> s
                    log.info(
                            "Changing remaining phase duration of traffic light group='{}' to '{}' seconds",
                            trafficLightGroupId, durationInSeconds
                    );
                    bridge.getTrafficLightControl().setPhaseRemainingDuration(trafficLightGroupId, durationInSeconds);
                    break;

                case ProgramId:
                    log.info(
                            "Changing program of traffic light group '{}' to program id '{}'",
                            trafficLightGroupId, trafficLightStateChange.getProgramId()
                    );
                    bridge.getTrafficLightControl().setProgramById(trafficLightGroupId, trafficLightStateChange.getProgramId());
                    break;

                case ChangeProgramWithPhase:
                    log.info(
                            "Changing program of traffic light group '{}' to program id '{}' and setting the phase to '{}'",
                            trafficLightGroupId, trafficLightStateChange.getProgramId(), trafficLightStateChange.getPhaseIndex()
                    );
                    bridge.getTrafficLightControl().setProgramById(trafficLightGroupId, trafficLightStateChange.getProgramId());
                    bridge.getTrafficLightControl().setPhaseIndex(trafficLightGroupId, trafficLightStateChange.getPhaseIndex());
                    break;

                case ChangeToCustomState:
                    log.info("Changing to custom states for traffic light group '{}'.", trafficLightGroupId);
                    bridge.getTrafficLightControl().setPhase(trafficLightGroupId, trafficLightStateChange.getCustomStateList());
                    break;
                default:
                    log.warn("Discard this TrafficLightStateChange interaction (paramType={}).",
                            trafficLightStateChange.getParameterType());
                    return;
            }

            String programId = bridge.getTrafficLightControl().getCurrentProgram(trafficLightGroupId);
            int phaseIndex = bridge.getTrafficLightControl().getCurrentPhase(trafficLightGroupId);
            long assumedNextTimeSwitch = (long) (bridge.getTrafficLightControl().getNextSwitchTime(trafficLightGroupId) * TIME.SECOND);
            List<TrafficLightState> currentStates = bridge.getTrafficLightControl().getCurrentStates(trafficLightGroupId);

            Map<String, TrafficLightGroupInfo> changedTrafficLightGroupInfo = new HashMap<>();
            changedTrafficLightGroupInfo.put(trafficLightGroupId, new TrafficLightGroupInfo(
                    trafficLightStateChange.getTrafficLightGroupId(),
                    programId,
                    phaseIndex,
                    assumedNextTimeSwitch,
                    currentStates
            ));

            // now tell the RTI that an update happened so that the update can reach other federates
            this.rti.triggerInteraction(
                    new TrafficLightUpdates(trafficLightStateChange.getTime(), changedTrafficLightGroupInfo)
            );
        } catch (IllegalValueException e) {
            throw new InternalFederateException(e);
        }
    }

    /**
     * Extracts data from received {@link VehicleSpeedChange} interaction and forwards it to SUMO.<br>
     * If an interval is set in VehicleSpeedChange, at first a slowDown will be initiated
     * via TraCI. After the interval has passed, the change speed command
     * is executed via TraCI. If no interval is set, the speed change is
     * initiated immediately.
     *
     * @param vehicleSpeedChange interaction indicating that a vehicle has to change its speed.
     */
    private synchronized void receiveInteraction(VehicleSpeedChange vehicleSpeedChange) throws InternalFederateException {
        if (externalVehicles.containsKey(vehicleSpeedChange.getVehicleId())) {
            return;
        }
        if (log.isInfoEnabled()) {
            log.info(
                    "{} at simulation time {}: " + "vehicleId=\"{}\", targetSpeed={}m/s, interval={}ms",
                    VEHICLE_SPEED_CHANGE_REQ, TIME.format(vehicleSpeedChange.getTime()), vehicleSpeedChange.getVehicleId(),
                    vehicleSpeedChange.getSpeed(), vehicleSpeedChange.getDuration()
            );
        }
        switch (vehicleSpeedChange.getType()) {
            case RESET:
                // reset speed to car-following rules
                bridge.getVehicleControl().setSpeed(vehicleSpeedChange.getVehicleId(), -1.0);
                break;
            case WITH_DURATION:
                if (vehicleSpeedChange.getDuration() > 0) {
                    // set speed smoothly with given interval
                    final long changeSpeedTimeStep = vehicleSpeedChange.getTime() + vehicleSpeedChange.getDuration();
                    log.debug("slow down vehicle {} and schedule change speed event for time step {} ns ",
                            vehicleSpeedChange.getVehicleId(), changeSpeedTimeStep);
                    bridge.getVehicleControl()
                            .slowDown(vehicleSpeedChange.getVehicleId(), vehicleSpeedChange.getSpeed(), vehicleSpeedChange.getDuration());

                    // set speed permanently after given interval (in the future) via the event scheduler
                    long adjustedTime = adjustToSumoTimeStep(changeSpeedTimeStep, sumoConfig.updateInterval * TIME.MILLI_SECOND);
                    eventScheduler.addEvent(new Event(adjustedTime, this, vehicleSpeedChange)
                    );
                } else {
                    // set speed immediately
                    bridge.getVehicleControl().setSpeed(vehicleSpeedChange.getVehicleId(), vehicleSpeedChange.getSpeed());
                }
                break;
            case WITH_FORCED_ACCELERATION:
                log.warn("ChangeSpeed with forced acceleration is not supported yet.");
                break;
            case WITH_PLEASANT_ACCELERATION:
                log.warn("ChangeSpeed with pleasant acceleration is not supported yet.");
                break;
            default:
                // unknown type
                log.warn("Unsupported VehicleSpeedChangeType: {}", vehicleSpeedChange.getType());
        }
    }

    private synchronized void receiveInteraction(VehicleSightDistanceConfiguration vehicleSightDistanceConfiguration) throws InternalFederateException {
        log.info("{} at simulation time {}: vehicleId=\"{}\", range={}, angle={}",
                VEHICLE_SIGHT_DISTANCE_REQ,
                TIME.format(vehicleSightDistanceConfiguration.getTime()),
                vehicleSightDistanceConfiguration.getVehicleId(),
                vehicleSightDistanceConfiguration.getSightDistance(),
                vehicleSightDistanceConfiguration.getOpeningAngle()
        );

        bridge.getSimulationControl().subscribeForVehiclesWithinFieldOfVision(
                vehicleSightDistanceConfiguration.getVehicleId(),
                vehicleSightDistanceConfiguration.getTime(), getEndTime(),
                vehicleSightDistanceConfiguration.getSightDistance(),
                vehicleSightDistanceConfiguration.getOpeningAngle()
        );
    }

    /**
     * Extract data from received {@link InductionLoopDetectorSubscription} interaction and forward to SUMO.
     *
     * @param inductionLoopDetectorSubscription Interaction that is indicating to subscribe for induction loop.
     * @throws InternalFederateException Exception if an error occurred while subscribe to induction loop.
     */
    private synchronized void receiveInteraction(InductionLoopDetectorSubscription inductionLoopDetectorSubscription) throws InternalFederateException {
        log.info(
                INDUCTION_LOOP_DETECTOR_SUBSCRIPTION + " Subscribe to InductionLoop with ID={}",
                inductionLoopDetectorSubscription.getInductionLoopId()
        );

        bridge.getSimulationControl().subscribeForInductionLoop(
                inductionLoopDetectorSubscription.getInductionLoopId(),
                inductionLoopDetectorSubscription.getTime(),
                getEndTime()
        );
    }

    /**
     * Extract data from received {@link LaneAreaDetectorSubscription} interaction and forward to SUMO.
     *
     * @param laneAreaDetectorSubscription Interaction that indicating to subscribe for Lane area detector.
     * @throws InternalFederateException Exception if an error occurred while subscribe to lane area detector.
     */
    private synchronized void receiveInteraction(LaneAreaDetectorSubscription laneAreaDetectorSubscription) throws InternalFederateException {
        log.info(
                LANE_AREA_DETECTOR_SUBSCRIPTION + " Subscribe to LaneArea with ID={}",
                laneAreaDetectorSubscription.getLaneAreaId()
        );

        bridge.getSimulationControl().subscribeForLaneArea(
                laneAreaDetectorSubscription.getLaneAreaId(),
                laneAreaDetectorSubscription.getTime(),
                getEndTime()
        );
    }

    /**
     * Extracts data from received TrafficLightSubscription message and forwards to SUMO.
     *
     * @param trafficLightSubscription Interaction that indicating to subscribe for a traffic light.
     * @throws InternalFederateException Exception if an error occurred while subscribe to the traffic light.
     */
    private synchronized void receiveInteraction(TrafficLightSubscription trafficLightSubscription) throws InternalFederateException {
        log.info("{} at simulation time {}: Subscribe to Traffic light group with ID={}",
                TRAFFIC_LIGHT_SUBSCRIPTION, TIME.format(trafficLightSubscription.getTime()),
                trafficLightSubscription.getTrafficLightGroupId()
        );

        bridge.getSimulationControl().subscribeForTrafficLight(
                trafficLightSubscription.getTrafficLightGroupId(),
                trafficLightSubscription.getTime(),
                this.getEndTime()
        );
    }

    /**
     * Extract data from received {@link LanePropertyChange} interaction and forward to SUMO.
     *
     * @param lanePropertyChange Interaction that indicating to change the lane.
     * @throws InternalFederateException Exception if an error occurred while changing the lane.
     */
    private synchronized void receiveInteraction(LanePropertyChange lanePropertyChange) throws InternalFederateException {

        log.info("{} at simulation time {}", LANE_PROPERTY_CHANGE, TIME.format(lanePropertyChange.getTime()));

        final String laneId = lanePropertyChange.getEdgeId() + "_" + lanePropertyChange.getLaneIndex();

        if (lanePropertyChange.getAllowedVehicleClasses() != null) {
            log.info("Change allowed vehicle classes of lane with ID={}", laneId);

            List<String> allowedVehicleClasses = lanePropertyChange.getAllowedVehicleClasses().stream()
                    .map(SumoVehicleClassMapping::toSumo).toList();
            bridge.getSimulationControl().setLaneAllowedVehicles(laneId, allowedVehicleClasses);
        }

        if (lanePropertyChange.getDisallowedVehicleClasses() != null) {
            log.info("Change disallowed vehicle classes of lane with ID={}", laneId);

            if (lanePropertyChange.getDisallowedVehicleClasses().containsAll(Lists.newArrayList(VehicleClass.values()))) {
                bridge.getSimulationControl().setLaneAllowedVehicles(laneId, Lists.newArrayList());
            } else {
                List<String> disallowedVehicleClasses = lanePropertyChange.getDisallowedVehicleClasses().stream()
                        .map(SumoVehicleClassMapping::toSumo).toList();
                bridge.getSimulationControl().setLaneDisallowedVehicles(laneId, disallowedVehicleClasses);
            }
        }

        if (lanePropertyChange.getMaxSpeed() != null) {
            log.info("Change max speed of lane with ID={}", laneId);
            bridge.getSimulationControl().setLaneMaxSpeed(laneId, lanePropertyChange.getMaxSpeed());
        }
    }

    /**
     * Extract data from received {@link VehicleSensorActivation} interaction and forward to SUMO.
     *
     * @param vehicleSensorActivation Interaction that indicating to enable of the distance sensors for vehicle.
     */
    private void receiveInteraction(VehicleSensorActivation vehicleSensorActivation) {
        log.info(
                "Enabling distance sensors for vehicle \"{}\" at simulation time {}",
                vehicleSensorActivation.getVehicleId(),
                TIME.format(vehicleSensorActivation.getTime())
        );

        if (ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), SensorType.RADAR_LEFT)
                || ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), SensorType.RADAR_RIGHT)) {
            log.warn("Left or right distance sensors for vehicles are not supported.");
            return;
        }

        if (ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), SensorType.RADAR_FRONT)
                || ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), SensorType.RADAR_REAR)) {
            if (!sumoConfig.subscriptions.contains(CSumo.SUBSCRIPTION_LEADER)) {
                log.warn("You tried to configure a front or rear sensor but no leader information is subscribed. "
                        + "Please add \"{}\" to the list of \"subscriptions\" in the sumo_config.json file.", CSumo.SUBSCRIPTION_LEADER);
                return;
            }

            bridge.getSimulationControl().configureDistanceSensors(
                    vehicleSensorActivation.getVehicleId(),
                    vehicleSensorActivation.getMaximumLookahead(),
                    ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), SensorType.RADAR_FRONT),
                    ArrayUtils.contains(vehicleSensorActivation.getSensorTypes(), SensorType.RADAR_REAR)
            );
        }
    }

    /**
     * Extract data from received {@link VehicleParametersChange} interaction and forward to SUMO.
     *
     * @param vehicleParametersChange Interaction that indicating to change of the vehicle parameters.
     * @throws InternalFederateException Exception is thrown if an error occurred while changing of the vehicle parameters.
     */
    private void receiveInteraction(VehicleParametersChange vehicleParametersChange) throws InternalFederateException {
        if (externalVehicles.containsKey(vehicleParametersChange.getVehicleId())) {
            changeExternalParameters(vehicleParametersChange);
            return;
        }

        log.info("{} at simulation time {}", VEHICLE_PARAM_CHANGE_REQ, TIME.format(vehicleParametersChange.getTime()));

        final String veh_id = vehicleParametersChange.getVehicleId();
        for (final VehicleParameter param : vehicleParametersChange.getVehicleParameters()) {
            switch (param.getParameterType()) {
                case MAX_SPEED:
                    bridge.getVehicleControl().setMaxSpeed(veh_id, param.<Double>getValue());
                    break;
                case IMPERFECTION:
                    bridge.getVehicleControl().setImperfection(veh_id, param.<Double>getValue());
                    break;
                case MAX_ACCELERATION:
                    bridge.getVehicleControl().setMaxAcceleration(veh_id, param.<Double>getValue());
                    break;
                case MAX_DECELERATION:
                    bridge.getVehicleControl().setMaxDeceleration(veh_id, param.<Double>getValue());
                    break;
                case MIN_GAP:
                    bridge.getVehicleControl().setMinimumGap(veh_id, param.<Double>getValue());
                    break;
                case REACTION_TIME:
                    bridge.getVehicleControl().setReactionTime(veh_id, param.<Double>getValue() + sumoConfig.timeGapOffset);
                    break;
                case SPEED_FACTOR:
                    bridge.getVehicleControl().setSpeedFactor(veh_id, param.<Double>getValue());
                    break;
                case LANE_CHANGE_MODE:
                    bridge.getVehicleControl().setLaneChangeMode(veh_id, SumoLaneChangeMode.translateFromEnum(param.getValue()));
                    break;
                case SPEED_MODE:
                    bridge.getVehicleControl().setSpeedMode(veh_id, SumoSpeedMode.translateFromEnum(param.getValue()));
                    break;
                case COLOR:
                    final Color color = param.getValue();
                    bridge.getVehicleControl().setColor(veh_id, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                    break;
                default:
                    log.warn("Parameter type {} is not supported by SUMO Ambassador", param.getParameterType().name());
            }
        }
    }

    private void receiveInteraction(TrafficSignRegistration trafficSignRegistration) throws InternalFederateException {
        if (trafficSignRegistration.getTrafficSign() instanceof TrafficSignSpeed trafficSign) {
            trafficSignManager.addSpeedSign(trafficSign);
        } else if (trafficSignRegistration.getTrafficSign() instanceof TrafficSignLaneAssignment trafficSign) {
            trafficSignManager.addLaneAssignmentSign(trafficSign);
        }
    }

    private void receiveInteraction(TrafficSignSpeedLimitChange trafficSignSpeedLimitChange) throws InternalFederateException {
        trafficSignManager.changeVariableSpeedSign(
                trafficSignSpeedLimitChange.getTrafficSignId(),
                trafficSignSpeedLimitChange.getLane(),
                trafficSignSpeedLimitChange.getSpeedLimit()
        );
    }

    private void receiveInteraction(TrafficSignLaneAssignmentChange trafficSignLaneAssignmentChange) throws InternalFederateException {
        trafficSignManager.changeVariableLaneAssignmentSign(
                trafficSignLaneAssignmentChange.getTrafficSignId(),
                trafficSignLaneAssignmentChange.getLane(),
                trafficSignLaneAssignmentChange.getAllowedVehicleClasses()
        );
    }

    /**
     * Tries to stop the vehicle at the given edge and offset. However, if the offset is larger
     * than the edge's length, the stop command will fail. In such cases, the offset will decrease,
     * and the stop is requested again.
     */
    private void stopVehicleAt(final String vehicleId, final IRoadPosition stopPos, final VehicleStopMode stopMode, final long duration)
            throws InternalFederateException {
        double stopPosition = 0;
        if (stopMode != VehicleStopMode.PARK_IN_PARKING_AREA) {
            double lengthOfLane = bridge.getSimulationControl().getLengthOfLane(stopPos.getConnectionId() + "_" + stopPos.getLaneIndex());
            stopPosition = stopPos.getOffset() < 0 ? lengthOfLane + stopPos.getOffset() : stopPos.getOffset();
            stopPosition = Math.min(Math.max(0.1, stopPosition), lengthOfLane);
        }
        bridge.getVehicleControl().stop(vehicleId, stopPos.getConnectionId(), stopPosition, stopPos.getLaneIndex(), duration, stopMode);
    }

    /**
     * Adjusts the given value to a multiple of the configured sumo time step
     * in order to avoid bugs related to sumo timing.
     *
     * @param changeSpeedStep Requested time for change speed in nanoseconds
     * @param sumoIntervalNs  Configured sumo interval in nanoseconds
     * @return The adjusted value which is a multiple of sumo timestep
     */
    static long adjustToSumoTimeStep(long changeSpeedStep, long sumoIntervalNs) {
        final long mod = changeSpeedStep % sumoIntervalNs;
        final long adjustedTimeStep;

        if (mod <= sumoIntervalNs / 2) {
            adjustedTimeStep = changeSpeedStep - mod;
        } else {
            adjustedTimeStep = changeSpeedStep + (sumoIntervalNs - mod);
        }
        return Math.max(adjustedTimeStep, sumoIntervalNs);
    }

    @Override
    public void processEvent(Event event) throws Exception {
        if (event.getResource() instanceof VehicleSpeedChange speedChange) {
            log.debug("Change the speed of vehicle {} at {} ns ", speedChange.getVehicleId(), event.getTime());
            bridge.getVehicleControl().setSpeed(speedChange.getVehicleId(), speedChange.getSpeed());
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
        log.info("Directory: " + dir);

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
            flushNotYetAddedVehicles(lastAdvanceTime);
        }

        // schedule events, e.g. change speed events
        int scheduled = eventScheduler.scheduleEvents(time);
        log.debug("scheduled {} events at time {}", scheduled, TIME.format(time));

        try {
            if (log.isTraceEnabled()) {
                log.trace(SIM_TRAFFIC, time);
            }

            if (firstAdvanceTime) {
                initSumoConnection();
                initializeTrafficLights(time);
                firstAdvanceTime = false;
            }

            setExternalVehiclesToLatestPositions(time);
            TraciSimulationStepResult simulationStepResult = bridge.getSimulationControl().simulateUntil(time);

            VehicleUpdates vehicleUpdates = simulationStepResult.vehicleUpdates();
            log.trace("Leaving advance time: {}", time);
            removeExternalVehiclesFromUpdates(vehicleUpdates);
            propagateNewRoutes(vehicleUpdates, time);
            propagateSumoVehiclesToRti(time);
            propagatePersonsToRti(time);

            nextTimeStep += sumoConfig.updateInterval * TIME.MILLI_SECOND;
            simulationStepResult.vehicleUpdates().setNextUpdate(nextTimeStep);

            rti.triggerInteraction(vehicleUpdates);
            // person updates will be sent in the form of AgentUpdates
            rti.triggerInteraction(simulationStepResult.personUpdates());
            rti.triggerInteraction(simulationStepResult.trafficDetectorUpdates());
            this.rti.triggerInteraction(simulationStepResult.trafficLightUpdates());

            rti.requestAdvanceTime(nextTimeStep, 0, FederatePriority.higher(descriptor.getPriority()));

            lastAdvanceTime = time;
        } catch (InternalFederateException | IOException | IllegalValueException e) {
            log.error("Error during advanceTime(" + time + ")", e);
            throw new InternalFederateException(e);
        }
    }

    private void setExternalVehiclesToLatestPositions(long time) {
        for (Map.Entry<String, ExternalVehicleState> external : externalVehicles.entrySet()) {
            final String externalVehicle = external.getKey();
            final ExternalVehicleState externalState = external.getValue();
            if (externalState.isAdded() && externalState.isRequireUpdate(time)) {
                VehicleData latestVehicleData = externalState.getLastMovementInfo();
                if (latestVehicleData == null) {
                    log.warn("No position data available for external vehicle {}", externalVehicle);
                    latestVehicleData = bridge.getSimulationControl().getLastKnownVehicleData(externalVehicle);
                }
                if (latestVehicleData != null) {
                    try {
                        bridge.getVehicleControl().moveToXY(
                                externalVehicle,
                                latestVehicleData.getPosition().toCartesian(),
                                latestVehicleData.getHeading(),
                                sumoConfig.moveToXyMode
                        );
                        externalState.updatedInSumo(time);
                    } catch (InternalFederateException e) {
                        log.warn("Could not set position of vehicle " + external.getKey(), e);
                    }
                }
            }
        }
    }

    private void removeExternalVehiclesFromUpdates(VehicleUpdates updates) {
        Iterator<VehicleData> updatesAddedIterator = updates.getAdded().iterator();
        while (updatesAddedIterator.hasNext()) {
            VehicleData currentVehicle = updatesAddedIterator.next();
            if (externalVehicles.containsKey(currentVehicle.getName())) {
                externalVehicles.get(currentVehicle.getName()).setAdded(true);
                updatesAddedIterator.remove();
            }
        }

        updates.getUpdated().removeIf(currentVehicle -> externalVehicles.containsKey(currentVehicle.getName()));
        updates.getRemovedNames().removeIf(vehicle -> externalVehicles.remove(vehicle) != null);
    }

    /**
     * Changes parameters of externally added vehicles.
     * So far only color change is supported.
     *
     * @param vehicleParametersChange Stores a list of vehicle parameters that should be changed.
     * @throws InternalFederateException Throws an IllegalArgumentException if color could not be set correctly.
     */
    public void changeExternalParameters(VehicleParametersChange vehicleParametersChange) throws InternalFederateException {
        final String veh_id = vehicleParametersChange.getVehicleId();
        for (final VehicleParameter param : vehicleParametersChange.getVehicleParameters()) {
            // Only color is supported as a parameter for external vehicles so far.
            if (param.getParameterType() == VehicleParameter.VehicleParameterType.COLOR) {
                final Color color = param.getValue();
                bridge.getVehicleControl().setColor(veh_id, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            }
        }
    }

    /**
     * This handles the case that sumo handles routing and creates new routes while doing so.
     *
     * @param vehicleUpdates Vehicle movement in the simulation.
     * @param time           Time at which the vehicle has moved.
     * @throws InternalFederateException Exception if an error occurred while propagating new routes.
     */
    private void propagateNewRoutes(VehicleUpdates vehicleUpdates, long time) throws InternalFederateException {
        // cache all new routes
        ArrayList<VehicleRoute> newRoutes = new ArrayList<>();

        // check added vehicles for new routes
        for (VehicleData vehicleData : vehicleUpdates.getAdded()) {
            if (!routes.containsKey(vehicleData.getRouteId())) {
                newRoutes.add(readRouteFromTraci(vehicleData.getRouteId()));
            }
        }

        // check updated vehicles for new routes
        for (VehicleData vehicleData : vehicleUpdates.getUpdated()) {
            if (!routes.containsKey(vehicleData.getRouteId())) {
                newRoutes.add(readRouteFromTraci(vehicleData.getRouteId()));
            }
        }

        // now create VehicleRouteRegistration interactions for each and add route to cache
        for (VehicleRoute route : newRoutes) {
            // propagate new route
            final VehicleRouteRegistration vehicleRouteRegistration = new VehicleRouteRegistration(time, route);
            try {
                rti.triggerInteraction(vehicleRouteRegistration);
            } catch (IllegalValueException e) {
                throw new InternalFederateException(e);
            }

            // save in cache
            routes.put(route.getId(), route);
        }
    }

    private void propagateSumoVehiclesToRti(long time) throws InternalFederateException {
        List<String> routeFileVehicles = getRouteFileVehicles();
        String vehicleTypeId;
        VehicleType vehicleType;
        for (String vehicleId : routeFileVehicles) {
            vehiclesAddedViaRouteFile.add(vehicleId);
            vehicleTypeId = bridge.getVehicleControl().getVehicleTypeId(vehicleId);
            vehicleType = bridge.getVehicleControl().getVehicleType(vehicleTypeId);
            try {
                rti.triggerInteraction(new ScenarioVehicleRegistration(time, vehicleId, vehicleType));
            } catch (IllegalValueException e) {
                throw new InternalFederateException(e);
            }
            if (sumoConfig.subscribeToAllVehicles) { // this is required as vehicles with no apps can't be subscribed to otherwise
                bridge.getSimulationControl().subscribeForVehicle(vehicleId, time, this.getEndTime());
            }
        }
    }

    private List<String> getRouteFileVehicles() throws InternalFederateException {
        return bridge.getSimulationControl().getDepartedVehicles().stream()
                .filter(v -> !vehiclesAddedViaRti.contains(v)) // all vehicles not added via MOSAIC are added by SUMO
                .toList();
    }

    private final List<String> personsSubscriptionCache = new ArrayList<>();

    private void propagatePersonsToRti(long time) throws InternalFederateException {
        List<String> persons = bridge.getSimulationControl().getDepartedPersons();
        String personType;
        for (String personId : persons) {
            personType = bridge.getPersonControl().getPersonTypeId(personId);
            try {
                rti.triggerInteraction(new ScenarioAgentRegistration(time, personId, personType));
            } catch (IllegalValueException e) {
                throw new InternalFederateException(e);
            }
            personsSubscriptionCache.add(personId);
        }
    }

    private void receiveInteraction(AgentRegistration agentRegistration) {
        String personId = agentRegistration.getMapping().getName();
        if (agentRegistration.getMapping().hasApplication() // this is required as persons with no apps can't be subscribed to otherwise
                // FIXME: This is a workaround as otherwise the ambassador will try to subscribe to Agents added by other simulators than SUMO, which is currently not possible
                && personsSubscriptionCache.contains(personId)) {
            try {
                bridge.getSimulationControl().subscribeForPerson(personId, agentRegistration.getTime(), this.getEndTime());
            } catch (InternalFederateException e) {
                log.warn("Could not subscribe to unknown person {}", personId);
            }
            personsSubscriptionCache.remove(personId);
        }
    }

    /**
     * Vehicles of the notYetRegisteredVehicles list will be added by this function
     * or cached again for the next time.
     *
     * @param time Current system time
     */
    abstract void flushNotYetAddedVehicles(long time) throws InternalFederateException;

    /**
     * Reads the route from the SUMO Traci.
     *
     * @param routeId The Id of the route.
     * @return The route from the Traci.
     * @throws InternalFederateException Exception is thrown if an error is occurred by reading route from the Traci.
     */
    VehicleRoute readRouteFromTraci(String routeId) throws InternalFederateException {
        // this route will always be generated with an empty list of nodes
        return new VehicleRoute(routeId, bridge.getRouteControl().getRouteEdges(routeId), new ArrayList<>(), 0d);
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
     * Fetches traffic light data, which contains program, position, controlled
     * roads.
     *
     * @param time Current time
     */
    private void initializeTrafficLights(long time) throws InternalFederateException, IOException, IllegalValueException {
        List<String> trafficLightGroupIds = bridge.getSimulationControl().getTrafficLightGroupIds();

        List<TrafficLightGroup> trafficLightGroups = new ArrayList<>();
        Map<String, Collection<String>> trafficLightGroupLaneMap = new HashMap<>();
        for (String trafficLightGroupId : trafficLightGroupIds) {
            try {
                TrafficLightGroup trafficLightGroup = bridge.getTrafficLightControl().getTrafficLightGroup(trafficLightGroupId);
                if (trafficLightGroup == null) {  // Reading traffic light groups doesn't work for railway signals
                    continue;
                }
                trafficLightGroups.add(trafficLightGroup);
                Collection<String> ctrlLanes = bridge.getTrafficLightControl().getControlledLanes(trafficLightGroupId);
                trafficLightGroupLaneMap.put(trafficLightGroupId, ctrlLanes);
            } catch (InternalFederateException e) {
                log.warn("Could not add traffic light {} to simulation. Skipping.", trafficLightGroupId);
            }
        }
        Interaction stlRegistration = new ScenarioTrafficLightRegistration(time, trafficLightGroups, trafficLightGroupLaneMap);
        rti.triggerInteraction(stlRegistration);
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

    @Override
    public boolean isTimeConstrained() {
        return true;
    }

    @Override
    public boolean isTimeRegulating() {
        return true;
    }

    @Override
    public boolean canProcessEvent() {
        return true;
    }
}