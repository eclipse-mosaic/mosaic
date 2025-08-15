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

package org.eclipse.mosaic.test.app.sumopersons;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.DATA;

public class HelloWorldServerApp extends AbstractApplication<ServerOperatingSystem> implements CommunicationApplication {

    @Override
    public void onStartup() {
        // Setup server with very limited bitrates
        getOs().getCellModule().enable(
                new CellModuleConfiguration()
                        .maxDownlinkBitrate(5 * DATA.MEGABIT)
                        .maxUplinkBitrate(5 * DATA.MEGABIT)
        );
        getLog().infoSimTime(this, "Setup limited capacity server {} at time {}", getOs().getId(), getOs().getSimulationTime());
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        V2xMessage message = receivedV2xMessage.getMessage();
        getLog().infoSimTime(this, "Received message from {} with content {}.",
                message.getRouting().getSource().getSourceName(),
                new String(message.getPayload().getBytes())
        );

        var routing = getOs().getCellModule().createMessageRouting()
                .destination(message.getRouting().getSource().getSourceAddress())
                .topological()
                .build();
        getOs().getCellModule().sendV2xMessage(new V2xMessage.Simple("pong", routing));
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

    @Override
    public void onShutdown() {
    }

    @Override
    public void processEvent(Event event) {
    }
}
