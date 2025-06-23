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

package org.eclipse.mosaic.app.tutorial;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Denm;
import org.eclipse.mosaic.lib.objects.v2x.etsi.DenmContent;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

/**
 * This class acts as an omniscient application for a server that warns vehicles
 * about certain hazards on the road. The hazard is hard-coded for tutorial purposes,
 * in more realistic scenarios the location would've been updated dynamically.
 */
public class WeatherServerApp extends AbstractApplication<ServerOperatingSystem> implements CommunicationApplication {

    /**
     * Send warning at this interval, in seconds.
     */
    private final static long INTERVAL = 2 * TIME.SECOND;


    /**
     * Save the last received DEN message for relaying.
     */
    private Denm lastReceivedMessage = null;


    /**
     * This method is called by the Application Simulator when the vehicle that has been equipped with this application
     * enters the simulation.
     * It is the first method called of this class during a simulation.
     */
    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Initialize WeatherServer application");
        getOs().getCellModule().enable();
        getLog().infoSimTime(this, "Setup weather server {} at time {}", getOs().getId(), getOs().getSimulationTime());
        getLog().infoSimTime(this, "Activated Cell Module");
        sample();
    }

    /**
     * This method is called by mosaic-application when a previously triggered event is handed over to the
     * application for processing.
     * Events can be triggered be this application itself, e.g. to run a routine periodically.
     *
     * @param event The event to be processed
     */
    @Override
    public void processEvent(Event event) throws Exception {
        sample();
    }

    /**
     * Method to let the WeatherServer send a DEN message periodically.
     * <p>
     * This method sends a DEN message and generates a new event during each call.
     * When said event is triggered, processEvent() is called, which in turn calls sample().
     * This way, sample() is called periodically at a given interval (given by the generated event time)
     * and thus the DENM is sent periodically at this interval.
     */
    private void sample() {
        if (lastReceivedMessage == null) {
            getLog().infoSimTime(this, "No warning present.");
        } else {
            final Denm denm = constructDenm();
            getLog().debugSimTime(this, "{}", denm);
            getOs().getCellModule().sendV2xMessage(denm);
            getLog().infoSimTime(this, "Relayed last DENM");
        }
        // Line up new event for periodic sending
        getOs().getEventManager().addEvent(
                getOs().getSimulationTime() + INTERVAL, this
        );
    }

    /**
     * Constructs a staged DEN message for tutorial purposes that matches exactly the requirements of
     * the Barnim tutorial scenario.
     * <p>
     * This is not meant to be used for real scenarios and is for the purpose of the tutorial only.
     *
     * @return The constructed DENM
     */
    private Denm constructDenm() {
        final GeoCircle geoCircle = new GeoCircle(lastReceivedMessage.getEventLocation(), 3000.0D);
        final MessageRouting routing = getOs().getCellModule().createMessageRouting().broadcast().geographical(geoCircle).build();
        return new Denm(routing,
                new DenmContent(
                        lastReceivedMessage.getTime(),
                        lastReceivedMessage.getSenderPosition(),
                        lastReceivedMessage.getEventRoadId(),
                        lastReceivedMessage.getEventCause(),
                        lastReceivedMessage.getCausedSpeed(),
                        lastReceivedMessage.getSenderDeceleration(),
                        lastReceivedMessage.getEventLocation(),
                        lastReceivedMessage.getEventArea(),
                        lastReceivedMessage.getExtendedContainer()
                ),
                200
        );
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        final V2xMessage msg = receivedV2xMessage.getMessage();
        getLog().infoSimTime(this, "Received {} from {}.",
                msg.getSimpleClassName(),
                msg.getRouting().getSource().getSourceName()
        );
        // Only DEN Messages are handled
        if (!(msg instanceof Denm)) {
            getLog().infoSimTime(this, "Ignoring message of type: {}", msg.getSimpleClassName());
            return;
        }
        lastReceivedMessage = (Denm) msg;
        getLog().debugSimTime(this, "DENM content: Event Cause: {}", lastReceivedMessage.getEventCause().toString());
        getLog().debugSimTime(this, "DENM content: Event position: {}", lastReceivedMessage.getEventLocation());
        getLog().debugSimTime(this, "DENM content: Road Id of the Sender: {}", lastReceivedMessage.getEventRoadId());
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgement) {

    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {

    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {

    }

    /**
     * This method is called by mosaic-application when the simulation has finished.
     */
    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown server.");
    }

}
