/*
 * Copyright (c) 2025 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.test.app.networking;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import java.net.Inet4Address;
import java.net.UnknownHostException;

public class CarTxCellApp extends AbstractApplication<VehicleOperatingSystem> implements CommunicationApplication {

    Inet4Address ip;

    public CarTxCellApp(String str_ip){
        try {
            this.ip = (Inet4Address) Inet4Address.getByName(str_ip);
        } catch (UnknownHostException e) {
            getLog().error("Cannot translate destination {} to IP", str_ip, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Initialize application");
        getOs().getCellModule().enable();
        getLog().infoSimTime(this, "Activated Cell Module");

        // The ns3 lte device requires 21 millliseconds to attach to the eNB
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + 21 * TIME.MILLI_SECOND, this);
    }

    @Override
    public void processEvent(Event event) throws Exception {
        getLog().infoSimTime(this, "Received event: {}", event.getResourceClassSimpleName());
        sample();
    }

    public void sample() {
        getOs().getEventManager().addEvent(
                getOs().getSimulationTime() + 2 * TIME.SECOND, this
        );
        getLog().infoSimTime(this, "Sending out cell message to " + ip);
        getOs().getCellModule().sendV2xMessage(
            new InterVehicleMsg(
                getOs().getCellModule().createMessageRouting().destination(ip).topological().build(),
                getOs().getPosition()
            )
        );
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown application");
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {

    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {

    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {

    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {

    }

}
