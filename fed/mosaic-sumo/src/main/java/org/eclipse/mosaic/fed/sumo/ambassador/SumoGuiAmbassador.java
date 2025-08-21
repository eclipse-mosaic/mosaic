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

package org.eclipse.mosaic.fed.sumo.ambassador;

import org.eclipse.mosaic.rti.api.FederateExecutor;
import org.eclipse.mosaic.rti.api.federatestarter.ExecutableFederateExecutor;
import org.eclipse.mosaic.rti.api.federatestarter.NopFederateExecutor;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.config.CLocalHost.OperatingSystem;

import java.io.InputStream;
import javax.annotation.Nonnull;

/**
 * Extension of the {@link SumoAmbassador} starting the GUI
 * of SUMO instead of the CLI.
 */
public class SumoGuiAmbassador extends SumoAmbassador {

    private int startCmdPort = -1;

    /**
     * Creates a new {@link SumoGuiAmbassador} object using
     * the super constructor, which loads the configuration
     * from the ambassadorParameter.
     *
     * @param ambassadorParameter containing parameters for the sumo ambassador.
     */
    public SumoGuiAmbassador(AmbassadorParameter ambassadorParameter) {
        super(ambassadorParameter);
    }

    @Override
    public void connectToFederate(String host, InputStream in, InputStream err) {
        LogStatements.printStartSumoGuiInfo();
        this.connectToFederate(host, startCmdPort);
    }

    @Nonnull
    @Override
    public FederateExecutor createFederateExecutor(String host, int port, OperatingSystem os) {
        // SUMO needs to start the federate by itself, therefore we need to store the federate starter locally and use it later
        this.federateExecutor = new ExecutableFederateExecutor(
                this.descriptor,
                SumoAmbassador.getFromSumoHome("sumo-gui"),
                super.getProgramArguments(port)
        );
        this.startCmdPort = port;
        return new NopFederateExecutor();
    }

    @Override
    public void finishSimulation() {
        if (bridge != null) {
            bridge.close();
        }
    }
}
