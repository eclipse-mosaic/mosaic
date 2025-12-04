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

package org.eclipse.mosaic.fed.application.ambassador.simulation;

import org.eclipse.mosaic.fed.application.ambassador.ErrorRegister;
import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AbstractCommunicationModule;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModule;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModule;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CommunicationModuleOwner;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.ambassador.util.ClassNameParser;
import org.eclipse.mosaic.fed.application.ambassador.util.ClassSubsetIterator;
import org.eclipse.mosaic.fed.application.ambassador.util.UnitLoggerImpl;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.MosaicApplication;
import org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem;
import org.eclipse.mosaic.interactions.application.ItefLogging;
import org.eclipse.mosaic.interactions.application.SumoTraciRequest;
import org.eclipse.mosaic.interactions.communication.V2xMessageAcknowledgement;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.traffic.SumoTraciResult;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.util.scheduling.EventInterceptor;
import org.eclipse.mosaic.lib.util.scheduling.EventManager;
import org.eclipse.mosaic.lib.util.scheduling.EventProcessor;
import org.eclipse.mosaic.lib.util.scheduling.InterceptedEvent;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

/**
 * This class is to be extended by all units that can be equipped with applications.
 * It supplies all functionality for loading applications, communication with the RTI
 * and the processing of events, etc.
 */
public abstract class AbstractSimulationUnit implements EventProcessor, OperatingSystem, CommunicationModuleOwner {

    /**
     * Id (name) which indicates the unit.
     */
    @Nonnull
    private final String id;

    private String group;

    /**
     * Position of the unit.
     */
    private final GeoPoint initialPosition;

    /**
     * The operating system log.
     */
    private final Logger osLog;

    private final List<Application> applications = new ArrayList<>();

    private final EventInterceptor eventInterceptor;


    private final AdHocModule adhocModule;
    private final CellModule cellModule;

    /**
     * User defined tagged value for the next CAM.
     */
    private byte[] userTaggedValue = null;

    private Class<? extends OperatingSystem> operatingSystemCheck;

    /**
     * Creates a new simulation unit, sets initial parameters and initializes
     * {@link AbstractCommunicationModule}s.
     *
     * @param id              simulation unit identifier
     * @param initialPosition initial position od the simulation unit
     */
    AbstractSimulationUnit(@Nonnull final String id, final GeoPoint initialPosition) {
        this.id = Objects.requireNonNull(id);
        osLog = new UnitLoggerImpl(id, "OperatingSystem");
        eventInterceptor = new EventInterceptor(SimulationKernel.SimulationKernel.getEventManager(), this);
        AtomicInteger messageSequenceNumberGenerator = new AtomicInteger();
        this.adhocModule = new AdHocModule(this, messageSequenceNumberGenerator, getOsLog());
        this.cellModule = new CellModule(this, messageSequenceNumberGenerator, getOsLog());
        this.initialPosition = initialPosition;
    }

    public AbstractSimulationUnit setGroup(String vehicleGroup) {
        this.group = vehicleGroup;
        return this;
    }

    @Nonnull
    @Override
    public final String getId() {
        return id;
    }

    /**
     * This method processes an event, before the extension of the simulation unit should process an event.
     * Important: This method should always be called before.
     * It is used to process certain events that are executed in all units equivalent.
     *
     * @param event the event to process.
     * @return true if this method has processed the given event
     * @throws RuntimeException if {@link Event} could not be properly handled by the processor
     */
    public final boolean preProcessEvent(final Event event) {
        final Object resource = event.getResource();
        if (osLog.isTraceEnabled()) {
            osLog.trace("#preProcessEvent at simulation time {} with resource class {} and nice {}",
                    TIME.format(event.getTime()), event.getResourceClassSimpleName(), event.getNice());
        }
        // failsafe
        if (resource == null) {
            return false;
        }

        /*
         * This should be an intercepted event from an application of this simulation unit.
         * The operating system intercept all events from the applications and can watch into the events and maybe forward the event.
         */
        if (event instanceof InterceptedEvent interceptedEvent) {
            // cast the resource of the intercepted event, it must be an event
            final Event originalEvent = interceptedEvent.getOriginalEvent();
            if (osLog.isTraceEnabled()) {
                osLog.trace(
                        "schedule intercepted event {} at simulation time {} event to processors {}",
                        interceptedEvent, TIME.format(event.getTime()), originalEvent.getProcessors())
                ;
            }
            final List<EventProcessor> processors = originalEvent.getProcessors();

            for (EventProcessor processor : processors) {
                if (!processor.canProcessEvent()) {
                    continue;
                }

                try {
                    processor.processEvent(originalEvent);
                } catch (Exception ex) {
                    throw new RuntimeException(
                            ErrorRegister.SIMULATION_UNIT_UncaughtExceptionDuringProcessEvent.toString(), ex);
                }
            }

            return true;
        } else {
            if (resource instanceof ReceivedV2xMessage receivedMessage) {
                processReceivedV2xMessage(receivedMessage);
                return true;
            } else if (resource instanceof V2xMessageAcknowledgement v2xAck) {
                processV2xMessageAcknowledgement(v2xAck);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public final long getSimulationTime() {
        return SimulationKernel.SimulationKernel.getCurrentSimulationTime();
    }

    public GeoPoint getInitialPosition() {
        return initialPosition;
    }

    @Override
    @Nonnull
    public final EventManager getEventManager() {
        return eventInterceptor;
    }

    /**
     * Returns the operating system log.
     *
     * @return the operating system log.
     */
    @Nonnull
    public final Logger getOsLog() {
        return osLog;
    }

    /**
     * Tears down the simulation unit by tearing down all applications and clearing all its application list.
     */
    public void tearDown() {
        osLog.debug("#tearDown at simulation time {}", TIME.format(getSimulationTime()));
        for (AbstractApplication<?> application : getApplicationsIterator(AbstractApplication.class)) {
            application.tearDown();
        }
        applications.clear();

        if (cellModule.isEnabled()) {
            cellModule.disable();
        }
        if (adhocModule.isEnabled()) {
            adhocModule.disable();
        }
    }

    protected void setUp() {
        osLog.debug("#setUp at simulation time {}", TIME.format(getSimulationTime()));
        for (AbstractApplication<?> application : getApplicationsIterator(AbstractApplication.class)) {
            // create a new logger for each application and call the syscallSetUp method
            application.setUp(this, new UnitLoggerImpl(id, application.getClass().getSimpleName()));
        }
    }

    /**
     * Load applications.
     *
     * @param applicationClassNames list of application class names
     */
    public final void loadApplications(List<String> applicationClassNames) {
        osLog.debug("#loadApplications {} at simulation time {}", applicationClassNames, TIME.format(getSimulationTime()));
        final ClassNameParser classNameParser = new ClassNameParser(osLog, SimulationKernel.SimulationKernel.getClassLoader());

        // iterate over all class names
        for (final String className : applicationClassNames) {

            Application newApplication = classNameParser.createInstanceFromClassName(className, AbstractApplication.class);

            if (newApplication == null) {
                osLog.error("Could not load application with name {}", className);
                continue;
            }

            if (operatingSystemCheck != null) {
                try {
                    // check if defined operating system is matching
                    Type mySuperclass = newApplication.getClass().getGenericSuperclass();
                    if (mySuperclass instanceof ParameterizedType parameterizedType) {
                        Type[] typeArgs = parameterizedType.getActualTypeArguments();
                        Type type = typeArgs[typeArgs.length - 1];
                        boolean typeAssignableFromOs = Class.forName(StringUtils.substringAfter(type.toString(), "interface").trim())
                                .isAssignableFrom(operatingSystemCheck);
                        if (!typeAssignableFromOs) {
                            throw new RuntimeException(ErrorRegister.SIMULATION_UNIT_IsNotAssignableFrom.toString());
                        }
                    } else {
                        osLog.debug("Could not check operating system of Application. Skipping check.");
                    }
                } catch (ClassNotFoundException e) {
                    osLog.debug("Check for operating system of Application failed. Skipping check.", e);
                }
            }

            osLog.debug("Successfully instantiated the class {}", className);

            // add the application to the list
            applications.add(newApplication);
        }
        // call tear up for every application
        setUp();
    }

    /**
     * Send an interaction to the RTI.
     *
     * @param interaction the interaction.
     */
    @Override
    public final void sendInteractionToRti(Interaction interaction) {
        try {
            SimulationKernel.SimulationKernel.getInteractable().triggerInteraction(interaction);
        } catch (IllegalValueException | InternalFederateException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public final void sendItefLogTuple(long logTupleId, int... values) {
        ItefLogging itefLogging = new ItefLogging(
                SimulationKernel.SimulationKernel.getCurrentSimulationTime(),
                getId(),
                logTupleId,
                values
        );
        sendInteractionToRti(itefLogging);
    }

    @Override
    public final String sendSumoTraciRequest(byte[] command) {
        String requestId = UUID.randomUUID().toString();
        SumoTraciRequest sumoTraciRequest = new SumoTraciRequest(
                SimulationKernel.SimulationKernel.getCurrentSimulationTime(),
                requestId,
                command
        );
        sendInteractionToRti(sumoTraciRequest);
        return requestId;
    }

    /**
     * Handle the returned information of a request to SUMO via traci.
     *
     * @param sumoTraciResult the result of a request to traci
     */
    public final void processSumoTraciMessage(final SumoTraciResult sumoTraciResult) {
        for (MosaicApplication application : getApplicationsIterator(MosaicApplication.class)) {
            application.onSumoTraciResponded(sumoTraciResult);
        }
    }

    /**
     * All applications that implement the {@link CommunicationApplication} interface are informed about
     * the reception of a new V2X-Message.
     *
     * @param receivedV2xMessage the {@link Interaction} containing the received V2X-Message
     */
    private void processReceivedV2xMessage(final ReceivedV2xMessage receivedV2xMessage) {
        // inform all applications about the received message
        for (CommunicationApplication application : getApplicationsIterator(CommunicationApplication.class)) {
            application.onMessageReceived(receivedV2xMessage);
        }
    }

    /**
     * All applications that implement the {@link CommunicationApplication} interface are informed about
     * whether a V2X-Message was acknowledged or not.
     *
     * @param v2xMessageAcknowledgement the acknowledgement {@link Interaction} containing a V2X-Message
     *                                  and information about acknowledgement
     */
    private void processV2xMessageAcknowledgement(final V2xMessageAcknowledgement v2xMessageAcknowledgement) {
        // Unmap the V2XMessage (which was cached and only a V2XMessageGeneralized was sent around)
        V2xMessage v2xMessage = SimulationKernel.SimulationKernel.getV2xMessageCache().getItem(
                v2xMessageAcknowledgement.getOriginatingMessageId()
        );

        if (v2xMessage == null) {
            osLog.error(
                    "Could not retrieve V2xMessage with id={} from Message Cache.",
                    v2xMessageAcknowledgement.getOriginatingMessageId()
            );
            return;
        }

        ReceivedAcknowledgement acknowledgement = new ReceivedAcknowledgement(v2xMessage, v2xMessageAcknowledgement.getNegativeReasons());
        for (CommunicationApplication application : getApplicationsIterator(CommunicationApplication.class)) {
            application.onAcknowledgementReceived(acknowledgement);
        }

    }

    @Override
    public void triggerOnSendMessage(V2xMessageTransmission messageTransmission) {
        for (CommunicationApplication application : getApplicationsIterator(CommunicationApplication.class)) {
            application.onMessageTransmitted(messageTransmission);
        }
    }

    /**
     * Returns the user tagged value and resets the value afterwards.
     *
     * @return The user tagged value.
     */
    final byte[] getAndResetUserTaggedValue() {
        // get the user tagged value from the memory
        final byte[] tmp = userTaggedValue;
        // clear the memory
        userTaggedValue = null;

        // return the user tagged value
        return tmp;
    }

    public final AdHocModule getAdHocModule() {
        return adhocModule;
    }

    public final CellModule getCellModule() {
        return cellModule;
    }

    void setRequiredOperatingSystem(Class<? extends OperatingSystem> operatingSystemCheck) {
        this.operatingSystemCheck = operatingSystemCheck;
    }

    @Override
    public final List<Application> getApplications() {
        return applications;
    }

    @Override
    public <A extends Application> Iterable<A> getApplicationsIterator(final Class<A> applicationClass) {
        return new Iterable<>() {

            @Nonnull
            @Override
            public Iterator<A> iterator() {
                return new ClassSubsetIterator<>(applications.iterator(), applicationClass);
            }
        };
    }

    @Override
    public final File getConfigurationPath() {
        return SimulationKernel.SimulationKernel.getConfigurationPath();
    }

    @Override
    public String getGroup() {
        return group;
    }
}
