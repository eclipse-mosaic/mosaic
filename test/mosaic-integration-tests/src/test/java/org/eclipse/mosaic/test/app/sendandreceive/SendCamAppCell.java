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

package org.eclipse.mosaic.test.app.sendandreceive;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModuleConfiguration;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.rti.TIME;

public class SendCamAppCell extends AbstractSenderApp {

    private final CellModuleConfiguration cellModuleConfiguration;

    @SuppressWarnings("unused") // used in mapping
    public SendCamAppCell(int durationInS) {
        this(durationInS, RoutingType.CELL_GEOCAST.toString(), 50d);
    }

    public SendCamAppCell(int durationInS, String addressingMode, double geoRadius) {
        this(durationInS, addressingMode, geoRadius, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    public SendCamAppCell(int durationInS, String addressingMode, double geoRadius, long maxDownlinkBitrate, long maxUplinkBitrate) {
        super(TIME.SECOND, durationInS * TIME.SECOND);

        this.cellModuleConfiguration = new CellModuleConfiguration()
                .camConfiguration(RoutingType.valueOf(addressingMode), geoRadius)
                .maxDownlinkBitrate(maxDownlinkBitrate)
                .maxUplinkBitrate(maxUplinkBitrate);
    }

    @Override
    protected void configureCommunication() {
        getOs().getCellModule().enable(cellModuleConfiguration);
    }

    @Override
    protected void disableCommunication() {
        if (getOs().getCellModule().isEnabled()) {
            getOs().getCellModule().disable();
        }
    }

    @Override
    protected void sendMessage() {
        Integer camId = getOs().getCellModule().sendCam();
        getLog().debugSimTime(this, "Sent CAM (id={}) with cellular radio", camId);
    }
}
