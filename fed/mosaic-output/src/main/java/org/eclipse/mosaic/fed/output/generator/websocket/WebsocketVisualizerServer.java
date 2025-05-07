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

package org.eclipse.mosaic.fed.output.generator.websocket;

import org.eclipse.mosaic.interactions.agent.AgentUpdates;
import org.eclipse.mosaic.interactions.communication.V2xMessageReception;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.interactions.electricity.ChargingStationUpdate;
import org.eclipse.mosaic.interactions.mapping.AgentRegistration;
import org.eclipse.mosaic.interactions.mapping.ChargingStationRegistration;
import org.eclipse.mosaic.interactions.mapping.RsuRegistration;
import org.eclipse.mosaic.interactions.mapping.TrafficLightRegistration;
import org.eclipse.mosaic.interactions.mapping.VehicleRegistration;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.lib.objects.agent.AgentData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.rti.api.Interaction;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Queues;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public class WebsocketVisualizerServer extends WebSocketServer implements Runnable {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final int MAX_MESSAGES_LIST = 1000;

    /**
     * Separate message id to transmit removed vehicles without dropping
     * them in VehicleUpdates.
     */
    private static final String UNITS_REMOVE = "UnitsRemove";

    private final AtomicReference<VehicleUpdates> vehicleUpdatesReference = new AtomicReference<>();
    private final AtomicReference<AgentUpdates> agentUpdatesReference = new AtomicReference<>();
    private final Queue<String> vehiclesToRemove = createQueue();
    private final Queue<String> agentsToRemove = createQueue();

    private final Queue<V2xMessageTransmission> sentV2xMessages = createQueue();
    private final Queue<V2xMessageReception> receivedV2xMessages = createQueue();

    private final Queue<VehicleRegistration> vehicleRegistrations = createQueue();
    private final Queue<AgentRegistration> agentRegistrations = createQueue();
    private final Queue<RsuRegistration> rsuRegistrations = createQueue();
    private final Queue<TrafficLightRegistration> trafficLightRegistrations = createQueue();
    private final Queue<ChargingStationRegistration> chargingStationRegistrations = createQueue();
    private final Queue<ChargingStationUpdate> chargingStationUpdates = createQueue();

    public WebsocketVisualizerServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onStart() {
        log.debug("Started");
    }

    @Override
    public void onClose(WebSocket arg0, int arg1, String arg2, boolean arg3) {
        log.debug("Closed");
    }

    @Override
    public void onError(WebSocket arg0, Exception ex) {
        log.error("WebsocketError", ex);
    }

    @Override
    public synchronized void onMessage(WebSocket socket, String arg1) {

        sendInteractions(socket, vehicleRegistrations);
        sendInteractions(socket, agentRegistrations);
        sendInteractions(socket, rsuRegistrations);
        sendInteractions(socket, trafficLightRegistrations);
        sendInteractions(socket, chargingStationRegistrations);

        sendVehicleUpdates(socket);
        sendAgentUpdates(socket);
        sendUnitsToBeRemoved(socket);

        sendV2xMessageTransmissions(socket);
        sendInteractions(socket, receivedV2xMessages);

        sendInteractions(socket, chargingStationUpdates);
    }

    private void sendVehicleUpdates(WebSocket socket) {
        if (vehicleUpdatesReference.get() != null && !vehicleUpdatesReference.get().getUpdated().isEmpty()) {
            VehicleUpdates reduced = reduceVehicleUpdates(vehicleUpdatesReference.get());
            JsonElement jsonElement = new Gson().toJsonTree(reduced);
            JsonObject jsonObject = new JsonObject();
            jsonObject.add(VehicleUpdates.TYPE_ID, jsonElement);
            socket.send(jsonObject.toString());
        }
    }

    private VehicleUpdates reduceVehicleUpdates(VehicleUpdates original) {
        List<VehicleData> reducedUpdates = new ArrayList<>();
        for (VehicleData veh : original.getUpdated()) {
            reducedUpdates.add(new VehicleData.Builder(veh.getTime(), veh.getName())
                    .position(veh.getPosition(), veh.getProjectedPosition())
                    .stopped(veh.getVehicleStopMode())
                    .create());
        }
        return new VehicleUpdates(original.getTime(), Collections.EMPTY_LIST, reducedUpdates, Collections.EMPTY_LIST);
    }

    private void sendAgentUpdates(WebSocket socket) {
        if (agentUpdatesReference.get() != null && !agentUpdatesReference.get().getUpdated().isEmpty()) {
            AgentUpdates reduced = reduceAgentUpdates(agentUpdatesReference.get());
            JsonElement jsonElement = new Gson().toJsonTree(reduced);
            JsonObject jsonObject = new JsonObject();
            jsonObject.add(AgentUpdates.TYPE_ID, jsonElement);
            socket.send(jsonObject.toString());
        }
    }

    private AgentUpdates reduceAgentUpdates(AgentUpdates original) {
        List<AgentData> reducedUpdates = new ArrayList<>();
        for (AgentData agent : original.getUpdated()) {
            reducedUpdates.add(
                    new AgentData(agent.getTime(), agent.getName(), agent.getPosition(), null, null, agent.getTripStatus())
            );
        }
        return new AgentUpdates(original.getTime(), reducedUpdates, Collections.EMPTY_LIST);
    }

    private void sendUnitsToBeRemoved(WebSocket socket) {
        if (!agentsToRemove.isEmpty() || !vehiclesToRemove.isEmpty()) {
            // copy (and remove) vehicles from queue to separate list in a thread-safe manner
            final List<String> toRemove = new ArrayList<>(vehiclesToRemove.size() + agentsToRemove.size());
            for (Iterator<String> iterator = vehiclesToRemove.iterator(); iterator.hasNext(); ) {
                toRemove.add(iterator.next());
                iterator.remove();
            }
            for (Iterator<String> iterator = agentsToRemove.iterator(); iterator.hasNext(); ) {
                toRemove.add(iterator.next());
                iterator.remove();
            }

            JsonElement jsonElement = new Gson().toJsonTree(toRemove);
            JsonObject jsonObject = new JsonObject();
            jsonObject.add(UNITS_REMOVE, jsonElement);
            socket.send(jsonObject.toString());
        }
    }

    private void sendV2xMessageTransmissions(WebSocket socket) {
        record TransmissionInfo(long time, int messageId, String sourceName) {}

        for (Iterator<V2xMessageTransmission> iterator = sentV2xMessages.iterator(); iterator.hasNext(); ) {
            V2xMessageTransmission transmission = iterator.next();

            Gson gson = new Gson();
            JsonElement jsonElement = gson.toJsonTree(
                    // reduce info to avoid expensive/erroneous json-serialization of custom V2xMessages
                    new TransmissionInfo(transmission.getTime(), transmission.getMessageId(), transmission.getSourceName())
            );
            JsonObject jsonObject = new JsonObject();
            jsonObject.add(V2xMessageTransmission.TYPE_ID, jsonElement);
            socket.send(jsonObject.toString());

            iterator.remove();
        }
    }

    private <T extends Interaction> void sendInteractions(WebSocket socket, Queue<T> interactionsQueue) {
        for (Iterator<T> iterator = interactionsQueue.iterator(); iterator.hasNext(); ) {
            T interaction = iterator.next();

            Gson gson = new Gson();
            JsonElement jsonElement = gson.toJsonTree(interaction);
            JsonObject jsonObject = new JsonObject();
            jsonObject.add(interaction.getTypeId(), jsonElement);
            socket.send(jsonObject.toString());

            iterator.remove();
        }
    }

    @Override
    public void onOpen(WebSocket arg0, ClientHandshake arg1) {

    }

    public synchronized void updateVehicleUpdates(VehicleUpdates interaction) {
        vehicleUpdatesReference.set(interaction);
        /* VehicleUpdates can be dropped as only the latest VehicleUpdates is sent when the server is ready for the next message.
         * To avoid dropping removed vehicles, we collect then in this extra queue and send them all together. */
        vehiclesToRemove.addAll(interaction.getRemovedNames());
    }

    public synchronized void updateAgentUpdates(AgentUpdates interaction) {
        agentUpdatesReference.set(interaction);
        agentsToRemove.addAll(interaction.getRemoved());
    }

    public synchronized void sendV2xMessage(V2xMessageTransmission interaction) {
        sentV2xMessages.add(interaction);
    }

    public synchronized void receiveV2xMessage(V2xMessageReception interaction) {
        receivedV2xMessages.add(interaction);
    }

    public synchronized void addRoadsideUnit(RsuRegistration interaction) {
        rsuRegistrations.add(interaction);
    }

    public synchronized void addTrafficLight(TrafficLightRegistration interaction) {
        trafficLightRegistrations.add(interaction);
    }

    public synchronized void addChargingStation(ChargingStationRegistration interaction) {
        chargingStationRegistrations.add(interaction);
    }

    public synchronized void updateChargingStation(ChargingStationUpdate interaction) {
        chargingStationUpdates.add(interaction);
    }

    public synchronized void addVehicle(VehicleRegistration interaction) {
        vehicleRegistrations.add(interaction);
    }

    public synchronized void addAgent(AgentRegistration interaction) {
        agentRegistrations.add(interaction);
    }

    private static <T> Queue<T> createQueue() {
        return Queues.synchronizedQueue(EvictingQueue.create(MAX_MESSAGES_LIST));
    }

}
