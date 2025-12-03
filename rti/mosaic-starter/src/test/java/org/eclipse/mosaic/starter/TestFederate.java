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

package org.eclipse.mosaic.starter;

import org.eclipse.mosaic.rti.api.FederateAmbassador;
import org.eclipse.mosaic.rti.api.FederateExecutor;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.RtiAmbassador;
import org.eclipse.mosaic.rti.api.federatestarter.DockerFederateExecutor;
import org.eclipse.mosaic.rti.api.federatestarter.NopFederateExecutor;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.api.parameters.FederateDescriptor;
import org.eclipse.mosaic.rti.config.CLocalHost;

import java.io.InputStream;
import javax.annotation.Nonnull;

public class TestFederate implements FederateAmbassador {

    private final AmbassadorParameter parameter;

    public TestFederate(AmbassadorParameter parameter) {
        this.parameter = parameter;
    }

    @Nonnull
    @Override
    public FederateExecutor createFederateExecutor(String host, int port, CLocalHost.OperatingSystem os) {
        return new NopFederateExecutor();
    }

    @Override
    public DockerFederateExecutor createDockerFederateExecutor(String dockerImage, CLocalHost.OperatingSystem os) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connectToFederate(String host, InputStream in, InputStream err) throws InternalFederateException {
        // nop
    }

    @Override
    public void connectToFederate(String host, int port) {
        // nop
    }

    @Override
    public void setRtiAmbassador(@Nonnull RtiAmbassador rti) {
        // nop
    }

    @Override
    public void initialize(long startTime, long endTime) throws InternalFederateException {
        // nop
    }

    @Override
    public void advanceTime(long time) throws InternalFederateException {
        // nop
    }

    @Override
    public void receiveInteraction(@Nonnull Interaction interaction) throws InternalFederateException {
        // nop
    }

    @Override
    public void finishSimulation() throws InternalFederateException {
        // nop
    }

    @Override
    public String getId() {
        return parameter.ambassadorId;
    }

    @Override
    public void setFederateDescriptor(@Nonnull FederateDescriptor descriptor) {
        // hop
    }

    @Override
    public byte getPriority() {
        return 50;
    }

    @Override
    public boolean isTimeConstrained() {
        return false;
    }

    @Override
    public boolean isTimeRegulating() {
        return false;
    }

    @Override
    public int compareTo(FederateAmbassador o) {
        return Integer.compare(this.getPriority(), o.getPriority());
    }
}
