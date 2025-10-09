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

package org.eclipse.mosaic.lib.coupling;

import org.eclipse.mosaic.rti.api.PreemptableFederateAmbassador;
import org.eclipse.mosaic.rti.api.parameters.FederatePriority;

import org.eclipse.mosaic.interactions.communication.AdHocCommunicationConfiguration;
import org.eclipse.mosaic.interactions.communication.CellularCommunicationConfiguration;
import org.eclipse.mosaic.interactions.communication.CommunicationConfiguration;
import org.eclipse.mosaic.interactions.communication.V2xMessageReception;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.interactions.mapping.ChargingStationRegistration;
import org.eclipse.mosaic.interactions.mapping.RsuRegistration;
import org.eclipse.mosaic.interactions.mapping.ServerRegistration;
import org.eclipse.mosaic.interactions.mapping.TmcRegistration;
import org.eclipse.mosaic.interactions.mapping.TrafficLightRegistration;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.lib.coupling.ClientServerChannel.NodeDataContainer;
import org.eclipse.mosaic.lib.coupling.ClientServerChannel.ReceiveWifiMessageRecord;
import org.eclipse.mosaic.lib.coupling.ClientServerChannel.ReceiveCellMessageRecord;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.CommandMessage.CommandType;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.AddNode.NodeType;
import org.eclipse.mosaic.lib.enums.AddressType;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.UnitData;
import org.eclipse.mosaic.lib.objects.UnitNameComparator;
import org.eclipse.mosaic.lib.objects.UnitNameGenerator;
import org.eclipse.mosaic.lib.objects.addressing.DestinationAddressContainer;
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;
import org.eclipse.mosaic.lib.objects.addressing.SourceAddressContainer;
import org.eclipse.mosaic.lib.objects.communication.AdHocConfiguration;
import org.eclipse.mosaic.lib.objects.communication.CellConfiguration;
import org.eclipse.mosaic.lib.objects.mapping.ChargingStationMapping;
import org.eclipse.mosaic.lib.objects.mapping.RsuMapping;
import org.eclipse.mosaic.lib.objects.mapping.ServerMapping;
import org.eclipse.mosaic.lib.objects.mapping.TmcMapping;
import org.eclipse.mosaic.lib.objects.mapping.TrafficLightMapping;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.AbstractFederateAmbassador;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.federatestarter.DockerFederateExecutor;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The Ambassador for coupling a network simulator to MOSAIC RTI.
 */
public abstract class AbstractNetworkAmbassador extends AbstractFederateAmbassador implements PreemptableFederateAmbassador {

    private final static class RegisteredNode {

        private NodeType type;
        private CartesianPoint position;
        private CommunicationConfiguration configuration;

        private RegisteredNode(NodeType type, CartesianPoint position, CommunicationConfiguration config) {
            this.type = type;
            this.position = position;
            this.configuration = config;
        }
    }

    /**
     * The actual ambassadors name.
     */
    private final String ambassadorName;

    /**
     * The actual federates name.
     */
    private final String federateName;

    /**
     * channel creating the abstraction of byte protocol for network federate.
     * This channel is for communication from the ambassador to the federate
     */
    ClientServerChannel ambassadorFederateChannel;

    /**
     * channel creating the abstraction of byte protocol for network federate.
     * This channel is for communication from the federate to the ambassador
     */
    ClientServerChannel federateAmbassadorChannel;

    /**
     * Docker-Executor for running the actual simulator (OMNeT++ or ns-3) in a Container.
     */
    protected DockerFederateExecutor dockerFederateExecutor = null;

    /**
     * List of new nodes, either vehicles or RSUs, which are only added when they have a position AND enabled radio communication.
     * Nodes are registered when just one part 1. OR 2. is received,
     * To get fully simulated, the ambassador must receive both of the following interactions:
     * 1. {@link VehicleUpdates}, {@link RsuRegistration}, {@link TrafficLightRegistration}, {@link ChargingStationRegistration}
     * 2. {@link CommunicationConfiguration}
     */
    private final Map<String, RegisteredNode> registeredNodes;

    /**
     * Holds a BiMap of internal (mosaic) and external (federate) unit ID's ({@code BiMap<String, Integer>}).
     * If simulated entity is in this map, the entity is present within the simulator.
     */
    private final NetworkEntityIdTransformer simulatedNodes;

    /**
     * Ids of nodes which has been added and removed.
     */
    protected final List<String> removedNodes;

    /**
     * A config object for whether to bypass federate destination type capability queries in
     * {@link #process(V2xMessageTransmission interaction)} if needed.
     */
    protected CAbstractNetworkAmbassador config;

    /**
     * A Set that contains all supported combinations of routing and address types that the federate supports
     */
    protected Set<Pair<RoutingType, AddressType>> supportedRoutingAddress = new HashSet<>();

    /**
     * A Set that contains all Protocol Types that the federate supports
     */
    protected Set<ProtocolType> supportedProtocols = new HashSet<>();

    /**
     * Number of tries to establish a ClientServerConnection
     */
    protected final static int MAX_CONNECTION_TRIES = 50;

    /**
     * Milliseconds to wait between tries when establishing a ClientServerConnection
     */
    protected final static int WAIT_BETWEEN_CONNECTION_TRIES = 100;

    /**
     * Creates a new AbstractNetworkAmbassador.
     *
     * @param ambassadorParameter parameters to configure the ambassador
     * @param ambassadorName      ambassador identifier
     * @param federateName        federate identifier
     */
    protected AbstractNetworkAmbassador(AmbassadorParameter ambassadorParameter, String ambassadorName, String federateName) {
        super(ambassadorParameter);
        this.ambassadorName = ambassadorName;
        this.federateName = federateName;
        this.registeredNodes = new HashMap<>();
        this.simulatedNodes = new NetworkEntityIdTransformer();
        this.removedNodes = new ArrayList<>();

        try {
            config = new ObjectInstantiation<>(CAbstractNetworkAmbassador.class, log)
                    .readFile(ambassadorParameter.configuration, CAbstractNetworkAmbassador.createConfigBuilder());
        } catch (InstantiationException | NullPointerException e) {
            log.warn("Problem when instantiating ambassador configuration from '{}'. Ignore file and try again with default config.", ambassadorParameter.configuration);
            config = new CAbstractNetworkAmbassador();
        }
    }

    @Override
    public void connectToFederate(String host, InputStream in, InputStream err) throws InternalFederateException {
        try {
            final Scanner outputScanner = new Scanner(in);
            final String outPortPattern = "OutPort=\\d{1,5}";
            final String errorPattern = ".*Error:.*";
            String matchedOutPort;
            String matchedError = null;

            while ((matchedOutPort = outputScanner.findInLine(outPortPattern)) == null
                    && (matchedError = outputScanner.findInLine(errorPattern)) == null) {
                outputScanner.nextLine();
            }

            // do not close outputScanner, as it would close the underlying stream.

            if (matchedOutPort != null) {
                log.debug("Found string \"{}\" in stdout", matchedOutPort);
                int port = Integer.parseInt(matchedOutPort.split("=")[1]);
                port = getHostPortFromDockerPort(port);
                connectToFederate(host, port);
            } else {
                log.error(matchedError);
                throw new InternalFederateException("Found error message in federate output while connecting: \n" + matchedError);
            }
        } catch (NumberFormatException ex) {
            throw new InternalFederateException("Could not parse port number output by federate", ex);
        } catch (IllegalStateException ex) {
            throw new InternalFederateException("Regex scanner was closed unexpectedly in connectToFederate", ex);
        } catch (NoSuchElementException ex) {
            throw new InternalFederateException("Could not find OutPort or error message in federate output", ex);
        }

        if (federateAmbassadorChannel == null || ambassadorFederateChannel == null) {
            throw new InternalFederateException("Could not establish connection to federate. The federate may not have started properly.");
        }

        log.trace("{} finished ConnectToFederate", ambassadorName);
    }

    private ClientServerChannel waitForClientServerChannel(String host, int port) {
        InetAddress h;
        try {
            h = InetAddress.getByName(host);
        } catch (UnknownHostException ex) {
            log.error("Unknown host: {}", ex.toString());
            throw new RuntimeException(ex);
        }
        return waitForClientServerChannel(h, port);
    }

    /**
     * Tries to establish a Channel _repeatedly_ within some timeout window.
     *
     * @param host host on which the federate is listening/speaking
     * @param port port on which the federate is listening/speaking
     */
    private ClientServerChannel waitForClientServerChannel(InetAddress host, int port) {
        int tries = 0;
        RuntimeException lastException = null;
        while (tries++ < MAX_CONNECTION_TRIES) {
            try {
                return new ClientServerChannel(host, port, log);
            } catch (IOException ex) {
                lastException = new RuntimeException(ex);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(WAIT_BETWEEN_CONNECTION_TRIES);
            } catch (InterruptedException e) {
                //quiet
            }
        }
        log.error("Failed to establish a socket connection within the last {}ms.", MAX_CONNECTION_TRIES * WAIT_BETWEEN_CONNECTION_TRIES);
        log.error(lastException.toString());
        throw lastException;
    }

    /**
     * Connects the incoming channel with the federate, waits for INIT message and a port number,
     * connects the outgoing channel to the received port number.
     * <br>
     * This method is called by the federation management service
     *
     * @param host host on which the federate is listening
     * @param port port on which the federate is listening
     */
    @Override
    public void connectToFederate(String host, int port) {
        // Connect to the network federate for reading
        federateAmbassadorChannel = waitForClientServerChannel(host, port);
        log.info("Connected to {} for reading on port {}", federateName, port);

        try { // Read the initial command and the port number to connect incoming channel
            CommandType cmd = federateAmbassadorChannel.readCommand();
            if (cmd == CommandType.INIT) {
                // This is the port the federate listens on for the second channel
                int remotePort = federateAmbassadorChannel.readPortBody();
                remotePort = getHostPortFromDockerPort(remotePort);
                // Connect the second channel
                ambassadorFederateChannel = waitForClientServerChannel(federateAmbassadorChannel.socket.getInetAddress(), remotePort);
                log.info("Connected to {} for commands on port {}", federateName, remotePort);
            } else {
                throw new RuntimeException("Could not connect to federate. Federate response is " + cmd);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not connect " + ambassadorName + " to " + federateName, e);
        }
    }

    /**
     * Since docker requires a binding of ports between container and host, we need to translate the ports used in
     * the container to the ports provided by the docker engine.
     *
     * @param port the container port
     * @return Returns, if a dockerFederateExecutor is set, the host port which is connected to the container port.
     * Otherwise, returns the given port.
     */
    private int getHostPortFromDockerPort(int port) {
        if (dockerFederateExecutor != null && dockerFederateExecutor.getRunningContainer() != null) {
            for (Pair<Integer, Integer> binding : dockerFederateExecutor.getRunningContainer().getPortBindings()) {
                if (binding.getRight() == port) {
                    return binding.getLeft();
                }
            }
        }
        return port;
    }

    @Override
    public void initialize(long startTime, long endTime) throws InternalFederateException {
        super.initialize(startTime, endTime);   // Set times in the super class
        try {
            // 1st Handshake: (1) Ambassador sends INIT (2) Ambassador sends times, (3) Federate sends SUCCESS
            if (CommandType.SUCCESS != ambassadorFederateChannel.writeInitBody(startTime, endTime, descriptor.isPreemptiveExecution())) {
                log.error("Could not initialize.");
                throw new InternalFederateException(
                        "Error in " + federateName + ": Could not initialize"
                );
            }
            log.info("Init simulation with startTime={}, stopTime={}", TIME.format(startTime), TIME.format(endTime));
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize " + ambassadorName, e);
        }

        for (CAbstractNetworkAmbassador.CBaseStationProperties bs : this.config.baseStations) {
            if (bs.geoPosition != null) {
                addNodeBToSimulation(bs.geoPosition.toCartesian());
            } else if (bs.cartesianPosition != null) {
                addNodeBToSimulation(bs.cartesianPosition);
            } else {
                throw new InternalFederateException("NodeB has neither GeoPosition nor CartesianPosition set.");
            }
        }

        log.info("Finished Initialization, waiting for Interactions...");
    }

    @Override
    protected void processInteraction(Interaction interaction) throws InternalFederateException {
        log.trace("ProcessInteraction {} at time={}", interaction.getTypeId(), TIME.format(interaction.getTime()));
        // 2nd step of time management cycle: Deliver interactions to the federate
        if (interaction.getTypeId().equals(ServerRegistration.TYPE_ID)) {
            this.process((ServerRegistration) interaction);
        } else if (interaction.getTypeId().equals(TmcRegistration.TYPE_ID)) {
            this.process((TmcRegistration) interaction);
        } else if (interaction.getTypeId().equals(RsuRegistration.TYPE_ID)) {
            this.process((RsuRegistration) interaction);
        } else if (interaction.getTypeId().equals(TrafficLightRegistration.TYPE_ID)) {
            this.process((TrafficLightRegistration) interaction);
        } else if (interaction.getTypeId().equals(ChargingStationRegistration.TYPE_ID)) {
            this.process((ChargingStationRegistration) interaction);
        } else if (interaction.getTypeId().equals(VehicleUpdates.TYPE_ID)) {
            this.process((VehicleUpdates) interaction);
        } else if (interaction.getTypeId().equals(V2xMessageTransmission.TYPE_ID)) {
            this.process((V2xMessageTransmission) interaction);
        } else if (interaction.getTypeId().equals(AdHocCommunicationConfiguration.TYPE_ID)) {
            this.process((AdHocCommunicationConfiguration) interaction);
        } else if (interaction.getTypeId().equals(CellularCommunicationConfiguration.TYPE_ID)) {
            this.process((CellularCommunicationConfiguration) interaction);
        } else {
            log.warn("Unhandeled interaction type {}", interaction.getTypeId());
        }
    }

    @Override
    public synchronized boolean advanceTimePreemptable(long time) throws InternalFederateException {
        Interaction nextInteraction = super.interactionQueue.getNextInteraction(time);
        while (nextInteraction != null) {
            rti.getMonitor().onProcessInteraction(getId(), nextInteraction);
            processInteraction(nextInteraction);
            nextInteraction = super.interactionQueue.getNextInteraction(time);
        }
        return processTimeAdvanceGrantPreemptable(time);
    }

    @Override
    protected void processTimeAdvanceGrant(long time) throws InternalFederateException {
        boolean r = processTimeAdvanceGrantPreemptable(time);
        if (!r) {
            throw new InternalFederateException("Did preempt when not expected to preempt");
        }
    }

    /*
     * This method just extends the processTimeAdvanceGrant method signature by a boolean return value,
     * in order to be used by advanceTimePreemptable function.
     */
    public boolean processTimeAdvanceGrantPreemptable(long time) throws InternalFederateException {
        log.trace("ProcessTimeAdvanceGrant at time={}", TIME.format(time));
        try {
            // 3rd and last step of cycle: Allow events up to current time in network simulator scheduler
            ambassadorFederateChannel.writeAdvanceTimeMessage(time);
            // Wait until next event request to start time management cycle
            // read while end of step is signalled
            command_loop:
            while (true) { // While the federate is advancing time we are receiving messages from it
                log.trace("Reading Command in TimeAdvanceGrant");
                CommandType cmd = federateAmbassadorChannel.readCommand(); // Which message does the federate send?
                switch (cmd) {
                    case NEXT_EVENT: // The federate has scheduled an event
                        long nextTime = federateAmbassadorChannel.readTimeBody();
                        log.trace("Requested next_event at {} ", nextTime);
                        this.rti.requestAdvanceTime(nextTime, 0, getPriority());
                        break;
                    case RECV_WIFI_MSG:
                        ReceiveWifiMessageRecord wifiRec = federateAmbassadorChannel.readReceiveWifiMessage(simulatedNodes);
                        // The receiver may have been removed from the simulation while message was on air
                        if (wifiRec.receiverName() != null) {
                            V2xMessageReception msg = new V2xMessageReception(
                                    wifiRec.time(),
                                    wifiRec.receiverName(),
                                    wifiRec.msgId(),
                                    wifiRec.receiverInformation()
                            );
                            log.debug("Receive wifi V2xMessage : Id({}) on Node {} at Time={}", msg.getMessageId(), msg.getReceiverName(), TIME.format(msg.getTime()));
                            this.rti.triggerInteraction(msg);
                        }
                        break;
                    case RECV_CELL_MSG:
                        ReceiveCellMessageRecord cellRec = federateAmbassadorChannel.readReceiveCellMessage(simulatedNodes);
                        // The receiver may have been removed from the simulation while message was on air
                        if (cellRec.receiverName() != null) {
                            V2xMessageReception msg = new V2xMessageReception(
                                    cellRec.time(),
                                    cellRec.receiverName(),
                                    cellRec.msgId(),
                                    null
                            );
                            log.debug("Receive cell V2xMessage : Id({}) on Node {} at Time={}", msg.getMessageId(), msg.getReceiverName(), TIME.format(msg.getTime()));
                            this.rti.triggerInteraction(msg);
                        }
                        break;
                    case END:       // The federate has terminated the current time advance -> we are done here
                        long termTime = federateAmbassadorChannel.readTimeBody();
                        log.trace("End ProcessTimeAdvanceGrant at: {}", termTime);
                        break command_loop; // break out of the infinite loop
                    case PREEMPTED:
                        // federate did not proceed all time-grant
                        long t = federateAmbassadorChannel.readTimeBody();
                        log.trace("Preempt ProcessTimeAdvanceGrant at: {}", t);
                        return false;
                    default:
                        throw new InternalFederateException("Unknown command from federate at processTimeAdvanceGrant");
                }
            }
        } catch (IOException | IllegalValueException | InternalFederateException e) {
            throw new InternalFederateException(e);
        }
        return true;
    }

    @Override
    public void finishSimulation() throws InternalFederateException {
        try {
            ambassadorFederateChannel.writeCommand(CommandType.SHUT_DOWN);
            ambassadorFederateChannel.close();
            federateAmbassadorChannel.close();
        } catch (IOException e) {
            log.error("Could not close socket.");
            throw new InternalFederateException(e);
        }
        log.info("Finished simulation");
    }

    /**
     * Add nodes based on received server mappings.
     *
     * @param interaction interaction containing a mapping of added server
     */
    private synchronized void process(ServerRegistration interaction) {
        log.debug(
                "Register Server {} at simulation time {} ",
                interaction.getMapping().getName(),
                TIME.format(interaction.getTime())
        );
        ServerMapping mapping = interaction.getMapping();
        if (simulatedNodes.containsInternalId(mapping.getName()) || registeredNodes.containsKey(mapping.getName())) {
            log.warn("A Server with ID {} was already added. Ignoring message.", mapping.getName());
            return;
        }
        // Put the new RSU into our list of nodes to be added when radio configuration is received
        registeredNodes.put(mapping.getName(), new RegisteredNode(NodeType.WIRED_NODE, CartesianPoint.ORIGO, null));
    }

    /**
     * Add nodes based on received tmc mappings.
     *
     * @param interaction interaction containing a mapping of added tmc
     */
    private synchronized void process(TmcRegistration interaction) {
        log.debug(
                "Register TMC {} at simulation time {} ",
                interaction.getMapping().getName(),
                TIME.format(interaction.getTime())
        );
        TmcMapping mapping = interaction.getMapping();
        if (simulatedNodes.containsInternalId(mapping.getName()) || registeredNodes.containsKey(mapping.getName())) {
            log.warn("A TMC with ID {} was already added. Ignoring message.", mapping.getName());
            return;
        }
        // Put the new RSU into our list of nodes to be added when radio configuration is received
        registeredNodes.put(mapping.getName(), new RegisteredNode(NodeType.WIRED_NODE, CartesianPoint.ORIGO, null));
    }

    /**
     * Add nodes based on received rsu mappings.
     *
     * @param interaction interaction containing a mapping of added rsu
     */
    private synchronized void process(RsuRegistration interaction) {
        log.debug(
                "Register RSU {} at simulation time {} ",
                interaction.getMapping().getName(),
                TIME.format(interaction.getTime())
        );
        RsuMapping mapping = interaction.getMapping();
        if (simulatedNodes.containsInternalId(mapping.getName()) || registeredNodes.containsKey(mapping.getName())) {
            log.warn("A RSU with ID {} was already added. Ignoring message.", mapping.getName());
            return;
        }
        // Put the new RSU into our list of nodes to be added when radio configuration is received
        registeredNodes.put(mapping.getName(), new RegisteredNode(NodeType.RADIO_NODE, mapping.getPosition().toCartesian(), null));
    }

    /**
     * Add nodes based on received traffic light mappings.
     * The method checks if the TLs are already present.
     * If not, the traffic light is added as a virtual node for later adding if an {@link CommunicationConfiguration} is received.
     *
     * @param interaction interaction containing a mapping of added traffic light
     */
    private synchronized void process(TrafficLightRegistration interaction) {
        log.debug(
                "Register TL {} (as RSU) at simulation time {} ",
                interaction.getMapping().getName(),
                TIME.format(interaction.getTime())
        );
        TrafficLightMapping mapping = interaction.getMapping();
        if (simulatedNodes.containsInternalId(mapping.getName()) || registeredNodes.containsKey(mapping.getName())) {
            log.warn("A TL with ID {} was already added. Ignoring message.", mapping.getName());
            return;
        }
        // Put the new TL RSU into our list of nodes to be added when radio configuration is received
        registeredNodes.put(mapping.getName(), new RegisteredNode(NodeType.RADIO_NODE, mapping.getPosition().toCartesian(), null));
    }

    /**
     * Add nodes based on received charging station mappings.
     * The method checks if the ChargingStation RSU is already present.
     * If not, the charging station is added as a virtual node for later adding if an {@link CommunicationConfiguration} is received.
     *
     * @param interaction interaction containing a mapping of added charging station
     */
    private synchronized void process(ChargingStationRegistration interaction) {
        log.debug(
                "Register charging station {} (as RSU) at simulation time {} ",
                interaction.getMapping().getName(),
                TIME.format(interaction.getTime())
        );
        ChargingStationMapping mapping = interaction.getMapping();
        if (simulatedNodes.containsInternalId(mapping.getName()) || registeredNodes.containsKey(mapping.getName())) {
            log.warn("A ChargingStation with ID {} was already added. Ignoring message.", mapping.getName());
            return;
        }
        // Put the new Charging Station RSU into our list of nodes to be added when radio configuration is received
        registeredNodes.put(mapping.getName(), new RegisteredNode(NodeType.RADIO_NODE, mapping.getPosition().toCartesian(), null));
    }

    /**
     * 1) Adds Vehicles on their first movement
     * 2) Updates node positions based on received vehicle movements.
     * 3) Removes nodes from the simulation
     * <br>
     * In the first case vehicles whose first movement is simulated will be added to the simulation in the federate.
     * The vehicles that are added will be sorted and verified to be also listed in the list of virtual vehicles.
     * If so, an external ID is generated for every vehicle and the id is added to the BiMap idMap. This map contains all simulated
     * vehicles and their corresponding external ID.
     * Next the vehicles position is converted and the vehicle is added to the list, which will be sent to the federate.
     * <br>
     * Second case: Vehicles moved, so it is checked if they are currently simulated and if so their new positions are converted.
     * All positions are then put into a list and handed to the channel for sending to the federate.
     * <br>
     * If vehicles shall be removed, they are verified to be simulated, their IDs are handed to the federate and are
     * erased from the idMap.
     *
     * @param interaction interaction containing vehicle movements
     * @throws InternalFederateException thrown when nodes could not be updated
     */
    private synchronized void process(VehicleUpdates interaction) throws InternalFederateException {
        try {
            if (!interaction.getAdded().isEmpty()) {
                List<VehicleData> addedVehicles = interaction.getAdded();
                Comparator<UnitData> comp = new UnitNameComparator();
                addedVehicles.sort(comp);
                for (VehicleData vi : addedVehicles) {
                    if (simulatedNodes.containsInternalId(vi.getName())) {
                        log.warn("Vehicle with ID {} was already added, ignoring entry.", vi.getName());
                    } else if (registeredNodes.containsKey(vi.getName())) {
                        // CommunicationConfiguration arrived before VehicleUpdates
                        RegisteredNode registeredNode = registeredNodes.get(vi.getName());
                        registeredNode.position = vi.getProjectedPosition();
                        if (registeredNode.configuration == null) {
                            log.error("Vehicle with ID {} was already registered but has no config present.", vi.getName());
                            return;
                        }
                        addNodeToSimulation(vi.getName(), registeredNode, interaction.getTime());
                    } else {
                        // Waiting for CommunicationConfiguration
                        log.debug(
                                "Register Vehicle {} at simulation time {} ",
                                vi.getName(),
                                TIME.format(interaction.getTime())
                        );
                        registeredNodes.put(vi.getName(), new RegisteredNode(NodeType.RADIO_NODE, vi.getProjectedPosition(), null));
                    }
                }
            }

            if (!interaction.getUpdated().isEmpty()) {
                long time = interaction.getTime();
                List<VehicleData> nodes = interaction.getUpdated();
                List<NodeDataContainer> nodesToUpdate = new ArrayList<>();
                for (VehicleData vi : nodes) {
                    GeoPoint geoPosition = vi.getPosition();
                    CartesianPoint projectedPosition = vi.getProjectedPosition();
                    if (simulatedNodes.containsInternalId(vi.getName())) { // if the vehicle is already present in the simulation
                        Integer id = simulatedNodes.toExternalId(vi.getName());
                        if (log.isTraceEnabled()) {
                            log.trace("UpdateNode : ID: [int={}, ext={}] Pos: x({}) y({}) Geo: {}", vi.getName(), id,
                                    projectedPosition.getX(), projectedPosition.getY(), geoPosition);
                        }
                        ArrayList<String> roadList = null;
                        if (vi.getAdditionalData() instanceof ArrayList<?>) {
                            roadList = (ArrayList<String>) vi.getAdditionalData();
                        }
                        nodesToUpdate.add(new NodeDataContainer(id, projectedPosition, roadList));
                    } else if (registeredNodes.containsKey(vi.getName())) {
                        // Node was not yet added to simulation, so update its entry in the registered node list
                        registeredNodes.get(vi.getName()).position = projectedPosition;
                        if (log.isTraceEnabled()) {
                            log.trace("UpdateNode (still virtual) : ID[int={}] Pos: x({}) y({}) Geo: {}", vi.getName(),
                                    projectedPosition.getX(), projectedPosition.getY(), geoPosition);
                        }
                    } else {
                        log.warn("Node ID[int={}] is not simulated", vi.getName());
                    }
                }
                if (CommandType.SUCCESS != ambassadorFederateChannel.writeUpdatePositionsMessage(time, nodesToUpdate)) {
                    LoggerFactory.getLogger(this.getClass()).error("Could not update nodes.");
                    throw new InternalFederateException("Error in " + federateName + ": Could not update nodes");
                }
            }
        } catch (IOException | InternalFederateException e) {
            log.error(e.getMessage(), e);
            throw new InternalFederateException("Could not update positions or remove vehicles.", e);
        }
    }

    /**
     * Insert a V2X message based on received {@link V2xMessageTransmission} interaction.
     *
     * @param interaction interaction containing a V2X message
     */
    private synchronized void process(V2xMessageTransmission interaction) throws InternalFederateException {
        final SourceAddressContainer sac = interaction.getMessage().getRouting().getSource();
        final DestinationAddressContainer dac = interaction.getMessage().getRouting().getDestination();

        if (!this.supportedRoutingAddress.contains(Pair.of(dac.getRoutingType(), dac.getAddressType()))) {
            log.warn(
                    "This V2XMessage requires a combination of routing scheme ({}) and address type ({})"
                            + " currently not supported by this network simulator."
                            + " Skip this message. Sender={}, Receiver={}, V2XMessage.id={}, time={}",
                    dac.getRoutingType(),
                    dac.getAddressType(),
                    sac.getSourceName(),
                    dac.getAddress().toString(),
                    interaction.getMessage().getId(),
                    TIME.format(interaction.getTime())
            );
            return;
        }

        if (!this.supportedProtocols.contains(dac.getProtocolType())) {
            log.warn(
                    "This V2XMessage requires a transport protocol ({})"
                            + " currently not supported by this network simulator. Skip this message. V2XMessage.id={}, time={}",
                    dac.getProtocolType().toString(),
                    interaction.getMessage().getId(),
                    TIME.format(interaction.getTime())
            );
            return;
        }

        try {
            Integer sourceId = simulatedNodes.containsInternalId(sac.getSourceName())
                    ? simulatedNodes.toExternalId(sac.getSourceName())
                    : null;

            if (sourceId == null) {
                log.warn("Node ID[int={}] is not simulated, ignoring transmission of message ID[{}], time={}",
                        sac.getSourceName(), interaction.getMessageId(), TIME.format(interaction.getTime())
                );
                return;
            }

            // Write the message onto the channel and to the federate
            // Then wait for ack
            CommandType ack = CommandType.UNDEF;
            if (dac.getRoutingType().isAdHoc()) {
                log.debug(
                        "Send {} V2XMessage id={} from node ID[int={} , ext={}] on channel {} to {} (time={})",
                        dac.getRoutingType(),
                        interaction.getMessageId(),
                        sac.getSourceName(),
                        sourceId,
                        dac.getAdhocChannelId(),
                        dac.getAddress(),
                        TIME.format(interaction.getTime())
                );
                ack = ambassadorFederateChannel.writeSendWifiMessage(
                        interaction.getTime(),
                        sourceId,
                        interaction.getMessage().getId(),
                        interaction.getMessage().getPayload().getEffectiveLength(),
                        dac
                );
            } else if (dac.getRoutingType().isCell()) {
                log.debug(
                        "Send {} V2XMessage id={} from node ID[int={} , ext={}] to {} (time={})",
                        dac.getRoutingType(),
                        interaction.getMessageId(),
                        sac.getSourceName(),
                        sourceId,
                        dac.getAddress(),
                        TIME.format(interaction.getTime())
                );
                ack = ambassadorFederateChannel.writeSendCellMessage(
                        interaction.getTime(),
                        sourceId,
                        interaction.getMessage().getId(),
                        interaction.getMessage().getPayload().getEffectiveLength(),
                        dac
                );
            } else {
                throw new InternalFederateException("Illegal state.");
            }

            if (CommandType.SUCCESS != ack) {
                log.error(
                        "Could not insert V2X message into network. Return status: {}",
                        ack
                );
                throw new InternalFederateException(
                        "Error in " + federateName + ": Could not insert V2X message into network"
                );
            }
        } catch (IOException | InternalFederateException e) {
            log.error("{}, time={}", e.getMessage(), TIME.format(interaction.getTime()));
            throw new InternalFederateException("Could not insert V2X message into network.", e);
        }
    }

    /**
     * Receive an {@link AdHocCommunicationConfiguration} and send it to the federate if the corresponding node is simulated.
     * If the node is not simulated (only added but did not move yet) the configuration interaction will be saved for later.
     *
     * @param interaction the AdHoc configuration interaction
     */
    protected synchronized void process(AdHocCommunicationConfiguration interaction) throws InternalFederateException {
        final String nodeId = interaction.getConfiguration().getNodeId();
        if (interaction.getConfiguration().getRadioMode() == AdHocConfiguration.RadioMode.OFF) {
            log.debug("Received AdHoc configuration (disable) for node {}", nodeId);
            removeNode(nodeId, interaction);
        } else {
            log.debug("Received AdHoc configuration (enable) for node {}", nodeId);
            configureRadioForNode(nodeId, interaction);
        }
    }

    /**
     * Receive an {@link CellularCommunicationConfiguration} and send it to the federate if the corresponding node is simulated.
     * If the node is not simulated (only added but did not move yet) the configuration interaction will be saved for later.
     *
     * @param interaction the Cellular configuration interaction
     */
    protected synchronized void process(CellularCommunicationConfiguration interaction) throws InternalFederateException {
        final String nodeId = interaction.getConfiguration().getNodeId();
        if (interaction.getConfiguration().isEnabled()) {
            log.debug("Received Cellular configuration (enable) for node {}", nodeId);
            configureRadioForNode(nodeId, interaction);
        } else {
            log.debug("Received Cellular configuration (disable) for node {}", nodeId);
            removeNode(nodeId, interaction);
        }
    }

    //####################################################################
    //   Helper methods
    //####################################################################

    private void configureRadioForNode(String nodeId, CommunicationConfiguration interaction) throws InternalFederateException {
        if (removedNodes.contains(nodeId)) {
            log.warn("Got radio configuration for already removed node {}. Ignoring.", nodeId);
        } else if (simulatedNodes.containsInternalId(nodeId)) {
            // node is already simulated -> reconfigure by direct call to send*Config
            log.debug("Updating Configuration for simulated node {}", nodeId);
            if (interaction instanceof AdHocCommunicationConfiguration castedInteraction) {
                sendAdHocCommunicationConfiguration(castedInteraction, interaction.getTime());
            } else if (interaction instanceof CellularCommunicationConfiguration castedInteraction) {
                sendCellularCommunicationConfiguration(castedInteraction, interaction.getTime());
            }
        } else if (UnitNameGenerator.isVehicle(nodeId)) {
            if (registeredNodes.containsKey(nodeId)) {
                // VehicleUpdates arrived before CommunicationConfiguration
                RegisteredNode registeredNode = registeredNodes.get(nodeId);
                registeredNode.configuration = interaction;
                if (registeredNode.position == null) {
                    log.error("Vehicle with ID {} was already registered but has no position present.", nodeId);
                    return;
                }
                addNodeToSimulation(nodeId, registeredNode, interaction.getTime());
            } else {
                // Wait for first VehicleUpdate with position
                log.debug(
                    "Register radio config for Vehicle {} at simulation time {} ",
                    nodeId,
                    TIME.format(interaction.getTime())
                );
                registeredNodes.put(nodeId, new RegisteredNode(NodeType.RADIO_NODE, null, interaction));
            }
        } else if (registeredNodes.containsKey(nodeId)) {
            // for Servers, TMCs, RSUs, TLs and CSs: Registrations arrive before CommunicationConfiguration -> they can be added to the simulation now
            RegisteredNode registeredNode = registeredNodes.get(nodeId);
            registeredNode.configuration = interaction;
            addNodeToSimulation(nodeId, registeredNode, interaction.getTime());
        } else {
            log.warn("Got radio configuration for unknown node {}. Ignoring.", nodeId);
        }
    }

    private synchronized void addNodeToSimulation(String nodeId, RegisteredNode registeredNode, long time) throws InternalFederateException {
        try {
            if (simulatedNodes.containsInternalId(nodeId)) {
                log.warn("Node with id (internal={}) couldn't be added: name already exists", nodeId);
            } else {
                int id = simulatedNodes.toExternalId(nodeId);
                if (CommandType.SUCCESS != ambassadorFederateChannel.writeAddNodeMessage(time, registeredNode.type, new NodeDataContainer(id, registeredNode.position, null))) {
                    log.error("Could not add new node.");
                    throw new InternalFederateException("Error in " + federateName + ": Could not add new node");
                }
                log.info(
                        "Added Node ID[int={}, ext={}] at projected position={} time={}",
                        simulatedNodes.fromExternalId(id), id, registeredNode.position, TIME.format(time)
                );
                registeredNodes.remove(nodeId);
                if (registeredNode.configuration instanceof AdHocCommunicationConfiguration castedInteraction) {
                    sendAdHocCommunicationConfiguration(castedInteraction, time);
                } else if (registeredNode.configuration instanceof CellularCommunicationConfiguration castedInteraction) {
                    sendCellularCommunicationConfiguration(castedInteraction, time);
                }
            }
        } catch (IOException | InternalFederateException e) {
            log.error(e.getMessage(), e);
            throw new InternalFederateException("Could not add new node.", e);
        }
    }

    protected synchronized void addNodeBToSimulation(CartesianPoint position) throws InternalFederateException {
        try {
            if (CommandType.SUCCESS != ambassadorFederateChannel.writeAddNodeMessage(0L, NodeType.NODE_B, new NodeDataContainer(0, position, null))) {
                log.error("Could not add new eNodeB.");
                throw new InternalFederateException("Error in " + federateName + ": Could not add new node");
            }
            log.info(
                    "Added eNodeB at projected position={} time={}", position, TIME.format(0L)
            );
        } catch (IOException | InternalFederateException e) {
            log.error(e.getMessage(), e);
            throw new InternalFederateException("Could not add new node.", e);
        }
    }

    /**
     * Send a configuration interaction to the vehicle.
     * <br>
     * Hands the configuration interaction data to the channel and logs the event
     *
     * @param interaction interaction containing an AdHocConfiguration
     * @param time        workaround for wrong timestamps when retaining configuration interactions
     */
    private synchronized void sendAdHocCommunicationConfiguration(AdHocCommunicationConfiguration interaction, long time) throws InternalFederateException {
        try {
            int interactionId = interaction.getId();
            AdHocConfiguration configuration = interaction.getConfiguration();
            if (!simulatedNodes.containsInternalId(configuration.getNodeId())) {
                throw new IllegalValueException("Node not simulated: " + configuration.getNodeId());
            }
            log.debug("Sending AdHocCommunicationConfiguration for node {}", configuration.getNodeId());
            Integer nodeId = simulatedNodes.toExternalId(configuration.getNodeId());
            if (log.isTraceEnabled()) {
                log.trace(
                        "AdHocCommunicationConfiguration: from node ID[int={}, ext={}], at time = {} channels: [{},{}|{},{}]",
                        configuration.getNodeId(), nodeId, time,
                        (configuration.getConf0() != null ? configuration.getConf0().getChannel0() : "null"),
                        (configuration.getConf0() != null ? configuration.getConf0().getChannel1() : "null"),
                        (configuration.getConf1() != null ? configuration.getConf1().getChannel0() : "null"),
                        (configuration.getConf1() != null ? configuration.getConf1().getChannel1() : "null")
                );
                log.trace("AdHocCommunicationConfiguration: Number of radios: {}", configuration.getRadioMode());
                if (configuration.getRadioMode() != AdHocConfiguration.RadioMode.OFF) {
                    log.trace("AdHocCommunicationConfiguration: radio0: IP: {}", configuration.getConf0().getIp());
                    log.trace("AdHocCommunicationConfiguration: radio0: Subnet: {}", configuration.getConf0().getSubnet());
                    log.trace("AdHocCommunicationConfiguration: radio0: Mode: {}", configuration.getConf0().getMode());
                    log.trace("AdHocCommunicationConfiguration: radio0: Channel0: {}", configuration.getConf0().getChannel0());
                    log.trace("AdHocCommunicationConfiguration: radio0: Channel1: {}", configuration.getConf0().getChannel1());
                    if (configuration.getConf0().getPower() == -1) {
                        log.trace("AdHocCommunicationConfiguration: radio0: Power set by federate");
                    } else {
                        log.trace("AdHocCommunicationConfiguration: radio0: Power: {} mW", configuration.getConf0().getPower());
                    }
                }
                if (configuration.getRadioMode() == AdHocConfiguration.RadioMode.DUAL) {
                    log.trace("AdHocCommunicationConfiguration: radio1: IP: {}", configuration.getConf1().getIp());
                    log.trace("AdHocCommunicationConfiguration: radio1: Subnet: {}", configuration.getConf1().getSubnet());
                    log.trace("AdHocCommunicationConfiguration: radio1: Mode: {}", configuration.getConf1().getMode());
                    log.trace("AdHocCommunicationConfiguration: radio1: Channel0: {}", configuration.getConf1().getChannel0());
                    log.trace("AdHocCommunicationConfiguration: radio1: Channel1: {}", configuration.getConf1().getChannel1());
                    if (configuration.getConf1().getPower() == -1) {
                        log.trace("AdHocCommunicationConfiguration: radio1: Power set by federate");
                    } else {
                        log.trace("AdHocCommunicationConfiguration: radio1: Power: {} mW", configuration.getConf1().getPower());
                    }
                }
            }
            if (CommandType.SUCCESS != ambassadorFederateChannel.writeConfigureWifiRadio(time, interactionId, nodeId, configuration)) {
                log.error("Could not configure node {}s radio", configuration.getNodeId());
                throw new InternalFederateException(
                        "Error in " + federateName + ": Could not configure node " + configuration.getNodeId() + "s radio"
                );
            }
        } catch (IOException | InternalFederateException | IllegalValueException e) {
            log.error(e.getMessage(), e);
            throw new InternalFederateException("Could not configure the radio.", e);
        }
    }

    private synchronized void sendCellularCommunicationConfiguration(CellularCommunicationConfiguration interaction, long time) throws InternalFederateException {
        try {
            CellConfiguration configuration = interaction.getConfiguration();
            if (!simulatedNodes.containsInternalId(configuration.getNodeId())) {
                throw new IllegalValueException("Node not simulated: " + configuration.getNodeId());
            }
            log.debug("Sending CellularCommunicationConfiguration for node {}", configuration.getNodeId());
            Integer nodeId = simulatedNodes.toExternalId(configuration.getNodeId());
            Inet4Address ip = IpResolver.getSingleton().lookup(configuration.getNodeId());
            if (CommandType.SUCCESS != ambassadorFederateChannel.writeConfigureCellRadio(time, nodeId, configuration, ip)) {
                log.error("Could not configure node {}s radio", configuration.getNodeId());
                throw new InternalFederateException(
                        "Error in " + federateName + ": Could not configure node " + configuration.getNodeId() + "s radio"
                );
            }
        } catch (IOException | InternalFederateException | IllegalValueException e) {
            log.error(e.getMessage(), e);
            throw new InternalFederateException("Could not configure the radio.", e);
        }
    }

    private void removeNode(String nodeId, Interaction interaction) throws InternalFederateException {
        final long time = interaction.getTime();
        // verify the node is simulated in the current simulation
        final Integer nodeToRemove = simulatedNodes.containsInternalId(nodeId) ? simulatedNodes.toExternalId(nodeId) : null;
        if (nodeToRemove != null) {
            log.info("removeNode ID[int={}, ext={}] time={}", nodeId, nodeToRemove, TIME.format(time));
            simulatedNodes.removeUsingInternalId(nodeId); // remove the vehicle from our internal list
            removedNodes.add(nodeId);
            try {
                if (CommandType.SUCCESS != ambassadorFederateChannel.writeRemoveNodeMessage(time, nodeToRemove)) {
                    log.error("Could not remove node.");
                    throw new InternalFederateException("Error in " + federateName + ": Could not remove node");
                }
            } catch (IOException | InternalFederateException e) {
                log.error("{}, time={}", e.getMessage(), TIME.format(interaction.getTime()));
                throw new InternalFederateException("Could not remove node from the simulator.", e);
            }
        } else if (registeredNodes.containsKey(nodeId)) {
            log.info("removeNode (still virtual) ID[int={}] time={}", nodeId, TIME.format(time));
            registeredNodes.remove(nodeId);
        } else {
            log.warn("Node ID[int={}] is not simulated", nodeId);
        }
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
    public boolean isPreemptiveExecutionEnabled() {
        return descriptor.isPreemptiveExecution();
    }
}
