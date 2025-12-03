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

package org.eclipse.mosaic.rti.api;

import org.eclipse.mosaic.rti.api.federatestarter.DockerFederateExecutor;
import org.eclipse.mosaic.rti.api.federatestarter.NopFederateExecutor;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.api.parameters.FederateDescriptor;
import org.eclipse.mosaic.rti.config.CLocalHost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.PriorityBlockingQueue;
import javax.annotation.Nonnull;

/**
 * Provides basic function of a federate ambassador and declares abstract methods to be implemented.
 */
public abstract class AbstractFederateAmbassador implements FederateAmbassador {

    /**
     * End time of the simulation.
     */
    protected long endTime;

    /**
     * The current lookahead for time advance requests.
     */
    protected long lookahead = 0;

    /**
     * The bridge for interacting with the RTI.
     */
    protected RtiAmbassador rti;

    /**
     * A priority queue that holds unprocessed interactions.
     */
    protected InteractionQueue interactionQueue = new InteractionQueue();

    protected FederateDescriptor descriptor;

    protected final Logger log;

    protected final AmbassadorParameter ambassadorParameter;

    protected AbstractFederateAmbassador(AmbassadorParameter ambassadorParameter) {
        this.ambassadorParameter = ambassadorParameter;
        this.log = LoggerFactory.getLogger(this.getClass());
    }

    /**
     * This method is called by the {@link AbstractFederateAmbassador}s whenever the
     * federate can safely process interactions in its incoming interaction queue. The
     * decision when it is safe to process such an interaction depends on the
     * policies TimeRegulating and TimeConstrained that has to be set by the
     * federate.
     *
     * @param interaction the interaction to be processed
     * @throws InternalFederateException an exception inside of a joined federate occurs
     */
    protected void processInteraction(Interaction interaction) throws InternalFederateException {
        log.trace("processInteraction(Interaction<?> interaction); interaction.getClass: {}", interaction.getClass());
    }

    /**
     * This method is called by the AbstractFederateAmbassador when a time
     * advance has been granted by the RTI. Before this call is placed, any
     * unprocessed interaction is forwarded to the federate using the processInteraction
     * method.
     *
     * @param time The timestamp towards which the federate can advance it local time.
     */
    protected void processTimeAdvanceGrant(long time) throws InternalFederateException {
        log.trace("processTimeAdvanceGrant(time); time: {}", time);
    }

    /**
     * This method is called by the time management service. If the ambassador
     * has requested to advance its internal clock, the time management service
     * calls this method to signal that a time advance request is granted.
     *
     * @param time Time in nano seconds until this federate is allowed to advance
     *             its internal clock.
     * @throws InternalFederateException an exception inside of a joined federate occurs
     */
    @Override
    public final synchronized void advanceTime(long time) throws InternalFederateException {
        Interaction nextInteraction = interactionQueue.getNextInteraction(time);
        while (nextInteraction != null) {
            rti.getMonitor().onProcessInteraction(getId(), nextInteraction);
            processInteraction(nextInteraction);
            nextInteraction = interactionQueue.getNextInteraction(time);
        }
        processTimeAdvanceGrant(time);
    }

    /**
     * The receiveInteraction method is called by the time management service
     * when an interaction is available for which this federate is registered.
     * If the receiver wants to advance its internal clock based on the
     * interaction it has to request a time advance. The receiver is not allowed
     * to advance its internal clock without granted request.
     *
     * @param interaction An object extending the interaction object and containing
     *                    shared data by another federate.
     * @throws InternalFederateException an exception if error while receiving interaction
     */
    @Override
    public final void receiveInteraction(@Nonnull Interaction interaction) throws InternalFederateException {
        try {
            // request time advance to process interaction if necessary
            if (isTimeConstrained()) {
                this.requestAdvanceTime(interaction.getTime());
                interactionQueue.add(interaction);
            } else {
                // not time constrained --> doesn't care about timestamps
                rti.getMonitor().onProcessInteraction(getId(), interaction);
                processInteraction(interaction);
                // if fed is time regulating but not time constrained,
                // it would have to request advance time before it may send
                // any interaction to other federates
            }
        } catch (IllegalValueException e) {
            log.error("Error while receiving interaction.", e);
        }
    }

    /*
     * Helper method to request time advance
     */
    private void requestAdvanceTime(long time) throws InternalFederateException, IllegalValueException {
        final long lookahead;
        if (isTimeRegulating()) {
            lookahead = this.lookahead;
        } else {
            // request with MAX lookahead since federate promised not to send any time stamped interactions (!timeRegulating)
            lookahead = Long.MAX_VALUE;
        }
        rti.requestAdvanceTime(time, lookahead, descriptor.getPriority());
    }

    @Override
    public final void setRtiAmbassador(@Nonnull RtiAmbassador rti) {
        log.trace("setRtiAmbassador(RtiAmbassador rti)");
        this.rti = rti;
    }

    @Override
    public final void setFederateDescriptor(@Nonnull FederateDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * Returns the ID of this federate.
     * Actually returns the ID of the federate descriptor to provide unique IDs,
     * even for multiple federates of the same type.
     *
     * @return the ID of this federate.
     */
    @Override
    public final String getId() {
        if (descriptor == null) {
            throw new IllegalStateException(getClass().getSimpleName()
                    + ": Federate descriptor has not been set");
        }

        return descriptor.getId();
    }

    /**
     * Finishes the simulation.
     *
     * @throws InternalFederateException an exception inside of a joined federate occurs
     */
    @Override
    public void finishSimulation() throws InternalFederateException {
        log.trace("finishSimulation");
    }

    /**
     * Creates a Docker federate executor.
     *
     * @param imageName name of the docker image containing the federate
     * @param os        operating system enum from {@link CLocalHost}
     * @return the {@link FederateExecutor} which starts the federate in a docker container
     */
    @Override
    public DockerFederateExecutor createDockerFederateExecutor(String imageName, CLocalHost.OperatingSystem os) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("This ambassador does not support federates inside of a docker container");
    }

    /**
     * If a federate should be started by MOSAIC, an implementation of {@link FederateExecutor} must be
     * provided. This method must return a nonnull object. The default implementation returns a {@link NopFederateExecutor},
     * which does nothing when called.
     *
     * @param host name of the host (as specified in /etc/hosts.xml)
     * @param port port number to be used by this federate
     * @param os   operating system enum
     * @return the {@link FederateExecutor} which starts the federate
     */
    @Nonnull
    @Override
    public FederateExecutor createFederateExecutor(String host, int port, CLocalHost.OperatingSystem os) {
        log.trace("createFederateStarter(String host, int port, CLocalHost.OperatingSystem os); host: {}, port: {}, os: {}", host, port, os);
        return new NopFederateExecutor();
    }

    /**
     * This method is called by the federation management service if the federate does not need
     * to be started.
     *
     * @param host the host on which the simulator is running
     * @param port the port to use for connecting to the simulator
     */
    @Override
    public void connectToFederate(String host, int port) {
        log.trace("connectToFederate(String host, int port); host: {}, port: {}", host, port);
    }

    /**
     * Connects to the federate.
     *
     * @param host The host on which the simulator is running.
     * @param in   This input stream is connected to the output stream of the
     *             started simulator process. The stream is only valid during
     *             this method call.
     * @param err  The error input stream
     * @throws InternalFederateException if the federation should be stopped due to an critical error
     */
    @Override
    public void connectToFederate(String host, InputStream in, InputStream err) throws InternalFederateException {
        log.trace("connectToFederate(String host, InputStream in, InputStream err); host: {}", host);
    }

    /**
     * This method is called by the TimeManagement to tell the federate the
     * start and the end time.
     *
     * @param startTime Start time of the simulation run in nano seconds.
     * @param endTime   End time of the simulation run in nano seconds.
     * @throws InternalFederateException if the federation should be stopped due to an critical error
     */
    @Override
    public void initialize(long startTime, long endTime) throws InternalFederateException {
        log.trace("initialize(long startTime, long endTime); startTime: {}, endTime: {}", startTime, endTime);
        this.endTime = endTime;
    }

    @Override
    public byte getPriority() {
        return descriptor.getPriority();
    }

    /**
     * Returns the time at which the simulation will be terminated.
     *
     * @return Time in ns at which the simulation will be terminated.
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * Used to sort ambassadors based on their priority where the lower
     * number means higher priority.
     */
    @Override
    public int compareTo(FederateAmbassador o) {
        return Byte.compare(descriptor.getPriority(), o.getPriority());
    }

    protected static class InteractionQueue extends PriorityBlockingQueue<Interaction> {

        private static final long serialVersionUID = 1L;

        /**
         * Returns the next interaction in the queue whose timestamp is smaller or equal
         * the given time.
         *
         * @param time time in [ns]
         */
        public Interaction getNextInteraction(long time) {
            if (this.peek() != null && this.peek().getTime() <= time) {
                return this.poll();
            }
            return null;
        }

    }
}

