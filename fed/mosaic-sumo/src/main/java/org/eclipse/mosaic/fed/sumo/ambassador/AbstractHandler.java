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

package org.eclipse.mosaic.fed.sumo.ambassador;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.config.CSumo;
import org.eclipse.mosaic.lib.util.scheduling.EventScheduler;
import org.eclipse.mosaic.rti.api.AbstractFederateAmbassador;
import org.eclipse.mosaic.rti.api.RtiAmbassador;
import org.eclipse.mosaic.rti.api.parameters.FederateDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractHandler {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected AbstractFederateAmbassador ambassador;
    protected FederateDescriptor federateDescriptor;
    protected CSumo sumoConfig;
    protected Bridge bridge;
    protected RtiAmbassador rti;
    protected EventScheduler eventScheduler;

    void initialize(
            AbstractFederateAmbassador ambassador,
            FederateDescriptor federateDescriptor,
            EventScheduler eventScheduler,
            CSumo sumoConfig,
            Bridge bridge,
            RtiAmbassador rti
    ) {
        this.ambassador = ambassador;
        this.federateDescriptor = federateDescriptor;
        this.eventScheduler = eventScheduler;
        this.sumoConfig = sumoConfig;
        this.bridge = bridge;
        this.rti = rti;
    }


}
