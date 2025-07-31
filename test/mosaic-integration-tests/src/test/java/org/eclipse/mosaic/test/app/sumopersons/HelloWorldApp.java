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

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.os.AgentOperatingSystem;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

public class HelloWorldApp extends AbstractApplication<AgentOperatingSystem> {

    private final static long TIME_INTERVAL = TIME.SECOND;

    private void sample() {
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + TIME_INTERVAL, this);
    }

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Hello World!");
        sample();

        getOs().getCellModule().enable();
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Bye bye World");
    }

    @Override
    public void processEvent(Event event) throws Exception {
        getLog().infoSimTime(this, "I'm still here at " + getOs().getPosition());
        sample();
    }

}