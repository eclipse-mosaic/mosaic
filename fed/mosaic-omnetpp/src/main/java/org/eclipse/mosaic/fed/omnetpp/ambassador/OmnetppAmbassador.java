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

package org.eclipse.mosaic.fed.omnetpp.ambassador;

import org.eclipse.mosaic.lib.coupling.AbstractNetworkAmbassador;
import org.eclipse.mosaic.lib.enums.AddressType;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.rti.api.FederateExecutor;
import org.eclipse.mosaic.rti.api.federatestarter.DockerFederateExecutor;
import org.eclipse.mosaic.rti.api.federatestarter.ExecutableFederateExecutor;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.config.CLocalHost.OperatingSystem;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Files;
import java.nio.file.Paths;
import javax.annotation.Nonnull;

/**
 * Implementation of the ambassador for the OMNeT++ network simulator.
 */
public class OmnetppAmbassador extends AbstractNetworkAmbassador {

    /**
     * Creates a new {@link OmnetppAmbassador} object.
     *
     * @param ambassadorParameter Parameter to specify the ambassador.
     */
    public OmnetppAmbassador(AmbassadorParameter ambassadorParameter) {
        super(ambassadorParameter, "OMNeT++ Ambassador", "OMNeT++ Federate");

        supportedRoutingAddress.add(Pair.of(RoutingType.AD_HOC_TOPOCAST, AddressType.IPV4_BROADCAST));
        supportedRoutingAddress.add(Pair.of(RoutingType.CELL_TOPOCAST, AddressType.IPV4_UNICAST));
        supportedProtocols.add(ProtocolType.UDP);
    }

    @Nonnull
    @Override
    public FederateExecutor createFederateExecutor(String host, int port, OperatingSystem os) {
        String omnetppConfigFileName = ObjectUtils.defaultIfNull(config.federateConfigurationFile, "omnetpp.ini"); // default // this one is required
        if (!Files.exists(Paths.get(this.ambassadorParameter.configuration.getParent(), omnetppConfigFileName))) {
            throw new IllegalArgumentException(omnetppConfigFileName + " missing");
        }
        String omnetppConfigFilePath = "omnetpp-federate/simulations/" + omnetppConfigFileName;
        String inetSourceDirectories = "inet:omnetpp-federate/src";
        switch (os) {
            case LINUX:
                return new ExecutableFederateExecutor(this.descriptor, "omnetpp-federate/omnetpp-federate",
                        "-u", "Cmdenv",
                        "-f", omnetppConfigFilePath,
                        "-n", inetSourceDirectories,
                        "--mosaiceventscheduler-host=" + host,
                        "--mosaiceventscheduler-port=" + port
                );
            case WINDOWS:
                return new ExecutableFederateExecutor(this.descriptor, "wsl.exe", "omnetpp-federate/omnetpp-federate",
                        "-u", "Cmdenv",
                        "-f", omnetppConfigFilePath,
                        "-n", inetSourceDirectories,
                        "--mosaiceventscheduler-host=" + host,
                        "--mosaiceventscheduler-port=" + port
                );
            case UNKNOWN:
            default:
                log.error("Operating system not supported by omnetpp");
                throw new RuntimeException("Operating system not supported by omnetpp");
        }
    }

    @Override
    public DockerFederateExecutor createDockerFederateExecutor(String dockerImage, OperatingSystem os) {
        this.dockerFederateExecutor = new DockerFederateExecutor(
                dockerImage,
                "omnetpp-federate/simulations",
                "/home/mosaic/bin/fed/omnetpp/omnetpp-federate/simulations"
        );
        return dockerFederateExecutor;
    }
}
