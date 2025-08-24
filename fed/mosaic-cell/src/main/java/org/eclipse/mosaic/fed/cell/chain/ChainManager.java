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

package org.eclipse.mosaic.fed.cell.chain;

import org.eclipse.mosaic.fed.cell.message.CellModuleMessage;
import org.eclipse.mosaic.fed.cell.module.CellModule;
import org.eclipse.mosaic.fed.cell.module.CellModuleNames;
import org.eclipse.mosaic.fed.cell.module.GeocasterModule;
import org.eclipse.mosaic.fed.cell.module.streammodules.DownstreamModule;
import org.eclipse.mosaic.fed.cell.module.streammodules.UpstreamModule;
import org.eclipse.mosaic.fed.cell.viz.StreamListener;
import org.eclipse.mosaic.fed.cell.viz.StreamListener.StreamParticipant;
import org.eclipse.mosaic.fed.cell.viz.StreamListener.StreamProperties;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.util.scheduling.DefaultEventScheduler;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.util.scheduling.EventManager;
import org.eclipse.mosaic.lib.util.scheduling.EventScheduler;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.RtiAmbassador;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * The ChainManager receives communication dependent interactions from the CellAmbassador
 * and handles the complete interaction between the cell modules internally
 * and also interfaces towards MOSAIC to advance the simulation.
 */
public class ChainManager implements EventManager {

    private static final Logger log = LoggerFactory.getLogger(ChainManager.class);
    private long lastAdvanceTime = 0;

    public RtiAmbassador getRti() {
        return rti;
    }

    private final AmbassadorParameter ambassadorParameter;

    /**
     * The ModuleChain to manage (the module to the actual cell logic).
     */
    private final Map<String, CellModule> moduleRegistry = new HashMap<>();

    /**
     * List of {@link StreamListener}s.
     */
    private final List<StreamListener> streamListeners = new LinkedList<>();

    /**
     * The eventScheduler to simulate the (timed) interactions of the cell modules.
     */
    private final EventScheduler eventScheduler = new DefaultEventScheduler();

    /**
     * Handle to interact with MOSAIC.
     */
    private final RtiAmbassador rti;

    private final RandomNumberGenerator rng;

    /**
     * Initialize the ChainManager, which is the EventManager for the communication dependent events
     * and at the same time the only module in the cell to perform interactions towards MOSAIC.
     *
     * @param rti the reference towards MOSAIC (for requesting timeAdvances and sending interactions).
     * @param rng the RandomGeneratorObject object that is needed by the Geocaster
     */
    public ChainManager(RtiAmbassador rti, RandomNumberGenerator rng, AmbassadorParameter ambassadorParameter) {
        log.info("Initialize ChainManager");
        this.rti = rti;
        this.rng = rng;
        this.ambassadorParameter = ambassadorParameter;
        initializeModuleRegistry();
    }

    /**
     * Initialize the module chain,
     * modules are worked through in the order they are added.
     * The current design envisions
     * the Upstream- and DownstreamModule for transmission delay and packet loss
     * the GeocasterModule for addressing of nodes.
     */
    private void initializeModuleRegistry() {
        addModule(new UpstreamModule(this));
        addModule(new GeocasterModule(this));
        addModule(new DownstreamModule(this));
    }

    private void addModule(CellModule module) {
        log.info("Adding Module {}", module.getModuleName());
        moduleRegistry.put(module.getModuleName(), module);
    }

    /**
     * Do the actual logic of the Cell for communication dependent events here (i.e., schedule the events).
     *
     * @param time simulation time of the TA (time advance).
     */
    public void advanceTime(long time) {
        this.lastAdvanceTime = time;

        final int scheduled = eventScheduler.scheduleEvents(time);
        if (log.isTraceEnabled()) {
            log.trace("t={}: scheduled {} events", TIME.format(time), scheduled);
        }
    }

    /**
     * On arrival of the {@link V2xMessageTransmission}, set the event to introduce the message
     * to the first module of the module chain (which is usually the {@link UpstreamModule}).
     * The UpstreamModule actually starts, when the TA for the {@link V2xMessageTransmission} arrives.
     *
     * @param v2xMessageTransmission sendV2XMessage from MOSAIC (typically send by the applications).
     */
    public void startEvent(V2xMessageTransmission v2xMessageTransmission) {
        RoutingType dstType = v2xMessageTransmission.getMessage().getRouting().getDestination().getRoutingType();
        if (dstType.isCell()) {
            log.trace("CellMessageType: {}", dstType);
            // Introduce V2XMessage to first module in the chain (which is the UpstreamModule)
            if (log.isDebugEnabled()) {
                log.debug("t={}: Introduce msg-{} from {} to chain to {} at {}",
                        TIME.format(lastAdvanceTime),
                        v2xMessageTransmission.getMessageId(), v2xMessageTransmission.getSourceName(),
                        getFirstModule().getModuleName(),
                        TIME.format(v2xMessageTransmission.getTime()));
            }
            newEvent(v2xMessageTransmission.getTime(), getFirstModule()).withResource(v2xMessageTransmission.getMessage()).schedule();
        }
    }

    /**
     * When the modules have processed the event, the ChainManager finishes this event
     * and sets a new event to actually simulate the transmission delay of the cell messages.
     * This can result in several actions:
     * (feed back to calling module, chain through to next module, and send interactions to MOSAIC).
     *
     * @param cellModuleMessage result from the modules with further information what to do.
     */
    public void finishEvent(CellModuleMessage cellModuleMessage) {
        Validate.notNull(
                cellModuleMessage, "The cellModuleResult message is null and the ChainManager could, therefore, not finish the Event"
        );

        // Set event for the next module in the chain, unless the current module is the last module
        if (cellModuleMessage.getNextModule() != null) {
            CellModule nextModule = getModule(cellModuleMessage.getNextModule());
            log.debug("t={}: Chain message through from {} to {} at {}",
                    TIME.format(lastAdvanceTime),
                    cellModuleMessage.getEmittingModule(),
                    nextModule.getModuleName(),
                    TIME.format(cellModuleMessage.getEndTime()));
            newEvent(cellModuleMessage.getEndTime(), nextModule).withResource(cellModuleMessage).schedule();
        }
    }

    @Override
    public void addEvent(@Nonnull Event event) {
        if (log.isTraceEnabled()) {
            log.trace("t={}: Add event to the scheduler with time {}",
                    TIME.format(lastAdvanceTime),
                    TIME.format(event.getTime()));
        }
        if (log.isTraceEnabled()) {
            log.trace(" event.resource: {}", event.getResourceClassSimpleName());
            log.trace(" event.processors: {}", event.getProcessors());
        }
        eventScheduler.addEvent(event);

        if (log.isTraceEnabled()) {
            log.trace(" and requestAdvanceTime({})", TIME.format(event.getTime()));
        }
        try {
            rti.requestAdvanceTime(event.getTime());
        } catch (IllegalValueException ex) {
            throw new RuntimeException("Could not request advanceTime from RTI.", ex);
        }
    }

    /**
     * Send interaction back to MOSAIC.
     *
     * @param interaction can be either
     *                    a V2xMessageReception and dedicated for the receiver or
     *                    an V2xMessageAcknowledgement for the sender.
     */
    public void sendInteractionToRti(Interaction interaction) {
        log.debug("t={}: Send Interaction to RTI (msgType={}, msgTime={})",
                TIME.format(lastAdvanceTime),
                interaction.getTypeId(), TIME.format(interaction.getTime()));
        try {
            rti.triggerInteraction(interaction);
        } catch (IllegalValueException | InternalFederateException ex) {
            throw new RuntimeException("Could not send interaction to RTI.", ex);
        }
    }

    /**
     * Helper functions for the navigation in the moduleRegistry.
     *
     * @return firstModule in the chain (in the current design, the UpstreamModule).
     */
    private CellModule getFirstModule() {
        return moduleRegistry.get(CellModuleNames.UPSTREAM_MODULE);
    }

    /**
     * Returns the module with the given module name if it exists, otherwise null.
     *
     * @param moduleName The name (id) of the requested module
     * @return currentModule (just to convert moduleName to module object)
     */
    private CellModule getModule(String moduleName) {
        return moduleRegistry.get(moduleName);
    }

    /**
     * Get the ambassador parameter (Ambassador-ID, Log-ID, Configuration file).
     *
     * @return Ambassador parameter.
     */
    public AmbassadorParameter getAmbassadorParameter() {
        return ambassadorParameter;
    }

    /**
     * Generate a random number.
     *
     * @return Random number.
     */
    public RandomNumberGenerator getRandomNumberGenerator() {
        return rng;
    }

    /**
     * Registers a new {@link StreamListener} which is called when a message finished
     * all stream modules, i.e. a message is sent to its target.
     *
     * @param streamListener the new stream listener
     */
    public void addStreamListener(StreamListener streamListener) {
        streamListeners.add(streamListener);
    }

    /**
     * Notify the {@link StreamListener} with {@link StreamParticipant}'s (receiver and sender)
     * and the properties of the stream {@link StreamParticipant}.
     *
     * @param sender     Sender of the stream.
     * @param receiver   Receiver of the Stream.
     * @param properties Stream properties.
     */
    public void notifyStreamListeners(StreamParticipant sender, StreamParticipant receiver, StreamProperties properties) {
        for (StreamListener streamListener : streamListeners) {
            streamListener.messageSent(sender, receiver, properties);
        }
    }

    /**
     * Print statistics about processedMessages for each module.
     */
    public void printStatistics() {
        for (CellModule module : moduleRegistry.values()) {
            log.info("[{}] Processed messages: {}", module.getModuleName(), module.getProcessedMessages());
        }

        for (StreamListener streamListener : streamListeners) {
            streamListener.finish();
        }
    }
}
