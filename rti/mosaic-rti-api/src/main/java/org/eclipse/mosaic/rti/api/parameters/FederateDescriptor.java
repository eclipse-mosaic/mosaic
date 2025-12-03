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

package org.eclipse.mosaic.rti.api.parameters;

import org.eclipse.mosaic.rti.api.FederateAmbassador;
import org.eclipse.mosaic.rti.api.FederateExecutor;
import org.eclipse.mosaic.rti.config.CLocalHost;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import javax.annotation.Nonnull;

/**
 * The federation handle is the description of a federate. If a federate is to
 * join a federation, it must be described using an instance of this class.
 */
public class FederateDescriptor {

    /**
     * Unique string identifying a federate. Must be non empty.
     */
    private final String id;

    /**
     * The ambassador instance representing a federate in federation.
     */
    private final FederateAmbassador ambassador;

    /**
     * Flag signalizing whether to deploy and undeploy the federate (e.g., an external executable) in the
     * working directory before starting it.
     */
    private boolean deployAndUndeploy = false;

    /**
     * Flag signalizing whether to start/stop the federate external component (e.g., an external executable).
     */
    private boolean startAndStop = false;

    /**
     * Activates preemptive execution for this ambassador. <br>With preemptive execution, the ambassador is time advanced
     * not only for own events or requests, but at timestamps of other events of all other ambassadors, leaving
     * room for federate-internal optimizations while still being able to react on events outside its own scope.
     */
    private boolean preemptiveExecution = false;

    /**
     * Parameters describing the host on which the federate is running or is to
     * be deployed and started.
     */
    private CLocalHost host = null;

    /**
     * The directory where the external component for the federate (e.g., the external executable, libraries and its resources)
     * can be found.
     */
    private File binariesDir = null;

    /**
     * The target path relative to the working directory of the federate, where the configuration files
     * should be deployed in.
     */
    private Path configTargetPath;

    /**
     * The configuration directory for the federate.
     */
    private File configDir = null;

    /**
     * Interactions for which a federate wants to be subscribed.
     */
    private Collection<InteractionDescriptor> interactions;

    /**
     * The priority assigned to the ambassador/federate. The lower the value,
     * the higher the priority of this ambassador/federate. Federates with an
     * higher priority receive interactions with the same time stamp earlier.
     * <p/>
     * {@link FederatePriority#HIGHEST} is highest / best priority
     */
    private final byte priority;

    /**
     * The executor instance which starts the federate.
     */
    private FederateExecutor federateExecutor;

    /**
     * Additional parameters to be applied for federates which need to be
     * executed in a Java Virtual Machine.
     */
    private JavaFederateParameters javaFederateParameters;

    /**
     * Creates a new {@link FederateDescriptor} which provides various information
     * for managing the ambassador and the federate it presents.
     *
     * @param id         unique string identifying a federate
     * @param ambassador ambassador representing a federate in federation
     * @param priority   the priority for this federate
     */
    public FederateDescriptor(String id, FederateAmbassador ambassador, byte priority) {
        this.id = id;
        this.ambassador = ambassador;
        this.priority = priority;
    }

    /**
     * Returns the unique identifier of the federate.
     */
    public String getId() {
        return this.id;
    }

    public FederateAmbassador getAmbassador() {
        return this.ambassador;
    }

    /**
     * Returns the priority of this federate. The lower the value the higher the priority.
     */
    public byte getPriority() {
        return priority;
    }

    @Nonnull
    public Collection<InteractionDescriptor> getInteractions() {
        return Validate.notNull(
                interactions,
                "The descriptor for {} has not been initialized properly: #setInteractions has not been called yet.", id
        );
    }

    public void setInteractions(@Nonnull Collection<InteractionDescriptor> interactions) {
        this.interactions = Validate.notNull(interactions, "The list of interactions for federate {} must not be null.", id);
    }

    public Path getConfigTargetPath() {
        return this.configTargetPath;
    }

    public void setConfigTargetPath(Path configTargetPath) {
        this.configTargetPath = configTargetPath;
    }

    public File getBinariesDir() {
        return this.binariesDir;
    }

    public void setBinariesDir(File binariesDir) {
        this.binariesDir = binariesDir;
    }

    public File getConfigDir() {
        return this.configDir;
    }

    public void setConfigDir(File configDir) {
        this.configDir = configDir;
    }

    public CLocalHost getHost() {
        return this.host;
    }

    public void setHost(CLocalHost host) {
        this.host = host;
    }

    public boolean isToDeployAndUndeploy() {
        return this.deployAndUndeploy;
    }

    public void setDeployAndUndeploy(boolean deployAndUndeploy) {
        this.deployAndUndeploy = deployAndUndeploy;
    }

    public boolean isToStartAndStop() {
        return this.startAndStop;
    }

    public void setStartAndStop(boolean startAndStop) {
        this.startAndStop = startAndStop;
    }

    public boolean isPreemptiveExecution() {
        return preemptiveExecution;
    }

    public void setPreemptiveExecution(boolean preemptiveExecution) {
        this.preemptiveExecution = preemptiveExecution;
    }

    public FederateExecutor getFederateExecutor() {
        return Validate.notNull(
                federateExecutor,
                "The descriptor for {} has not been initialized properly: #setFederateExecutor has not been called yet.", id
        );
    }

    public void setFederateExecutor(@Nonnull FederateExecutor federateExecutor) {
        this.federateExecutor = Validate.notNull(federateExecutor, "FederateExecutor for federate {} must not be null", id);
    }

    public JavaFederateParameters getJavaFederateParameters() {
        return ObjectUtils.defaultIfNull(javaFederateParameters, JavaFederateParameters.defaultParameters());
    }

    public void setJavaFederateParameters(JavaFederateParameters javaFederateParameters) {
        this.javaFederateParameters = javaFederateParameters;
    }
}
