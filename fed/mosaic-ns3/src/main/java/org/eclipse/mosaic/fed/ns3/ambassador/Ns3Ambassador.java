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

package org.eclipse.mosaic.fed.ns3.ambassador;

import org.eclipse.mosaic.fed.cell.config.gson.ConfigBuilderFactory;
import org.eclipse.mosaic.fed.ns3.ambassador.config.CNodeB;
import org.eclipse.mosaic.interactions.communication.AdHocCommunicationConfiguration;
import org.eclipse.mosaic.lib.coupling.AbstractNetworkAmbassador;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.AddressType;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.enums.RoutingType;
import org.eclipse.mosaic.lib.objects.communication.AdHocConfiguration;
import org.eclipse.mosaic.lib.objects.communication.AdHocConfiguration.RadioMode;
import org.eclipse.mosaic.lib.objects.communication.InterfaceConfiguration;
import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;
import org.eclipse.mosaic.rti.api.FederateExecutor;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.federatestarter.DockerFederateExecutor;
import org.eclipse.mosaic.rti.api.federatestarter.ExecutableFederateExecutor;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.config.CLocalHost.OperatingSystem;

import com.google.gson.JsonParseException;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.annotation.Nonnull;

/**
 * Implementation of the ambassador for the ns-3 network simulator.
 */
public class Ns3Ambassador extends AbstractNetworkAmbassador {

    /**
     * Creates a new {@link Ns3Ambassador} object.
     *
     * @param ambassadorParameter Parameter to specify the ambassador.
     */
    public Ns3Ambassador(AmbassadorParameter ambassadorParameter) {
        super(ambassadorParameter, "NS-3 Ambassador", "NS-3 Federate");

        supportedRoutingAddress.add(Pair.of(RoutingType.AD_HOC_TOPOCAST, AddressType.IPV4_BROADCAST));
        supportedRoutingAddress.add(Pair.of(RoutingType.CELL_TOPOCAST, AddressType.IPV4_UNICAST));
        supportedProtocols.add(ProtocolType.UDP);
    }

    @Nonnull
    @Override
    public FederateExecutor createFederateExecutor(String host, int port, OperatingSystem os) {
        if (!Files.exists(Paths.get(this.ambassadorParameter.configuration.getParent(), "ns3_federate_config.xml"))) {
            throw new IllegalArgumentException("ns3_federate_config.xml missing");
        }
        switch (os) {
            case LINUX:
                return new ExecutableFederateExecutor(this.descriptor, "./run.sh", Integer.toString(port));
            case WINDOWS:
                return new ExecutableFederateExecutor(this.descriptor, "wsl.exe", "./run.sh", Integer.toString(port));
            case UNKNOWN:
            default:
                log.error("Operating system not supported by ns3");
                throw new RuntimeException("Operating system not supported by ns3");
        }
    }

    @Override
    public DockerFederateExecutor createDockerFederateExecutor(String imageName, OperatingSystem os) {
        this.dockerFederateExecutor = new DockerFederateExecutor(
                imageName,
                "ns3config",
                "/home/mosaic/bin/fed/ns3/ns3config"
        );
        return dockerFederateExecutor;
    }

    @Override
    public void initialize(long startTime, long endTime) throws InternalFederateException {
        super.initialize(startTime, endTime);

        readConfigurations();

        log.info("Finished Initialization, waiting for Interactions...");
    }

    /**
     * Digest the configs in regions.json before the simulation starts.
     */
    private void readConfigurations() throws InternalFederateException {
        log.debug("Read Configuration");

        if (log.isTraceEnabled()) {
            log.trace("Opening configuration file {}", ambassadorParameter.configuration);
        }

        /* read eNB positions and signal down to ns3 */
        if (this.config.regionConfigurationFile == null || this.config.regionConfigurationFile.isEmpty()) {
            log.warn("No configuration for eNodeBs given. Ignore.");
            return;
        }
        
        CNodeB nodeBs;
        try {
            File configFile = new File(String.valueOf(Paths.get(this.ambassadorParameter.configuration.getParent(), this.config.regionConfigurationFile)));
            nodeBs = new ObjectInstantiation<CNodeB>(CNodeB.class, log).readFile(configFile, ConfigBuilderFactory.getConfigBuilder());
        } catch (InstantiationException | NullPointerException | JsonParseException ex) {
            log.error("Could not read configuration {}", this.config.regionConfigurationFile, ex);
            throw new InternalFederateException(ex);
        }

        for (CNodeB.CNodeBProperties enb : nodeBs.regions) {
            if (enb.geoPosition != null) {
                super.addNodeBToSimulation(enb.geoPosition.toCartesian());
            } else if (enb.cartesianPosition != null) {
                super.addNodeBToSimulation(enb.cartesianPosition);
            } else {
                throw new InternalFederateException("NodeB has neither GeoPosition nor CartesianPosition set.");
            }
        }
    }


    @Override
    protected synchronized void process(AdHocCommunicationConfiguration interaction) throws InternalFederateException {

        AdHocConfiguration conf = interaction.getConfiguration();
        RadioMode radioMode = conf.getRadioMode();
        //These messages should not occur often so warn if they are incorrect
        if (radioMode == RadioMode.DUAL) {
            log.warn("The ns-3 federate currently does not support multi radio operation, "
                    + "configuration message will be discarded");
            return;
        } else if (radioMode == RadioMode.SINGLE && conf.getConf0().getMode() != InterfaceConfiguration.MultiChannelMode.SINGLE) {
            log.warn("The ns-3 federate currently does not support multi channel operation, "
                    + "configuration message will be discarded");
            return;
        } else if (radioMode == RadioMode.SINGLE && conf.getConf0().getChannel0() != AdHocChannel.CCH) {
            log.warn("The ns-3 federate currently does not support other channels than CCH, "
                    + "configuration message will be discarded");
            return;
        }
        super.process(interaction);
    }
}
