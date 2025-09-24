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
import org.eclipse.mosaic.rti.api.parameters.FederateDescriptor;
import org.eclipse.mosaic.rti.config.CLocalHost.OperatingSystem;

import java.io.InputStream;
import javax.annotation.Nonnull;

/**
 * This interface must be implemented by each simulator (federate) that
 * wants to be included into a federation. It contains methods that are used
 * by the RTI services to start, stop and control the federate.
 */
public interface FederateAmbassador extends Comparable<FederateAmbassador> {

    /**
     * Returns a {@link FederateExecutor} which is used to start the federate this
     * ambassador is associated with. If no separate federate needs to be started,
     * a {@link NopFederateExecutor} should be returned.
     *
     * @param host name of the host (as specified in /etc/hosts.xml)
     * @param port port number to be used by this federate
     * @param os   the current operating system of the system
     * @return the {@link FederateExecutor} which starts the federate
     */
    @Nonnull
    FederateExecutor createFederateExecutor(String host, int port, OperatingSystem os);

    /**
     * If the federate or simulator can be executed as a docker container, this method returns a
     * {@link DockerFederateExecutor}.
     *
     * @param dockerImage name of the docker image containing the federate (as specified in /etc/defaults.xml)
     * @param os          the current operating system of the host machine
     * @return the {@link DockerFederateExecutor} which starts the federate
     * @throws UnsupportedOperationException if the ambassador does not support running the federate in a docker container.
     */
    DockerFederateExecutor createDockerFederateExecutor(String dockerImage, OperatingSystem os) throws UnsupportedOperationException;

    /**
     * This method is called by the federation management service after it has started the corresponding federate or simulator.
     *
     * @param host The host on which the simulator is running.
     * @param in   This input stream is connected to the output stream of the
     *             started simulator process. The stream is only valid during
     *             this method call.
     * @throws InternalFederateException This exception is to be thrown when a federation specific
     *                                   error occurs.
     */
    void connectToFederate(String host, InputStream in, InputStream err) throws InternalFederateException;

    /**
     * This method is called by the federation management service to connect to the
     * federate without starting it. This requires the port to be configured in the configuration
     * file for the RTI.
     *
     * @param host the host on which the simulator is running
     * @param port the port to use for connecting to the simulator
     */
    void connectToFederate(String host, int port);

    /**
     * Assigns a new {@link RtiAmbassador} to this federate. The {@link RtiAmbassador} is the bridge
     * the the RTI providing various methods, e.g. to exchange interactions. Each ambassador requires
     * its own instance of the {@link RtiAmbassador}.
     *
     * @param rti a {@link RtiAmbassador} instance
     */
    void setRtiAmbassador(@Nonnull RtiAmbassador rti);

    /**
     * This method is called by the TimeManagement to tell the federate the
     * start and the end time.
     *
     * @param startTime Start time of the simulation run in nano seconds.
     * @param endTime   End time of the simulation run in nano seconds.
     * @throws InternalFederateException This exception is to be thrown when a federation specific
     *                                   error occurs.
     */
    void initialize(long startTime, long endTime) throws InternalFederateException;

    /**
     * This method is called by the time management service. If the ambassador
     * has requested to advance its internal clock, the time management service
     * calls this method to signal that a time advance request is granted.
     *
     * @param time Time in nano seconds until this federate is allowed to advance
     *             its internal clock.
     * @throws InternalFederateException This exception is to be thrown when a federation specific
     *                                   error occurs.
     */
    boolean advanceTime(long time) throws InternalFederateException;

    /**
     * The receiveMessage method is called by the time management service
     * when an interaction is available for which this federate is registered.
     * If the receiver wants to advance its internal clock based on the
     * interaction it has to request a time advance. The receiver is not allowed
     * to advance its internal clock without granted request.
     *
     * @param interaction An object extending the interaction object and containing
     *                    shared data by another federate.
     * @throws InternalFederateException This exception is to be thrown when a federation specific
     *                                   error occurs.
     */
    void receiveInteraction(@Nonnull Interaction interaction) throws InternalFederateException;

    /**
     * This method is called by the time management service to signal that the
     * simulation is finished.
     */
    void finishSimulation() throws InternalFederateException;

    /**
     * Returns the identifier of the federate.
     */
    String getId();

    /**
     * Sets the descriptor containing all required information for setting up the
     * ambassador and federate.
     */
    void setFederateDescriptor(@Nonnull FederateDescriptor descriptor);

    /**
     * Returns the priority of this ambassador/federate. The lower the value
     * the higher the priority.
     *
     * @return The priority of this federate
     */
    byte getPriority();

    /**
     * Returns whether this federate is time constrained.
     * Is set if the federate is sensitive towards the correct ordering of
     * events. The federate ambassador will ensure that the message
     * processing happens in time stamp order. If set to false, interactions will be
     * processed will be in receive order.
     *
     * @return {@code true} if this federate is time constrained, else {@code false}
     */
    boolean isTimeConstrained();

    /**
     * Returns whether this federate is time regulating.
     * Is set if the federate influences other federates and can prevent them
     * from advancing their local time.
     *
     * @return {@code true} if this federate is time regulating, {@code false} else
     */
    boolean isTimeRegulating();
}

