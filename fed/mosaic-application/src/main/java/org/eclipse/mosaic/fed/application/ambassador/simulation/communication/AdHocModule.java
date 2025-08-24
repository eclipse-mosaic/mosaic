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

package org.eclipse.mosaic.fed.application.ambassador.simulation.communication;

import static java.lang.Integer.min;

import org.eclipse.mosaic.fed.application.app.api.os.modules.Locatable;
import org.eclipse.mosaic.interactions.communication.AdHocCommunicationConfiguration;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.addressing.AdHocMessageRoutingBuilder;
import org.eclipse.mosaic.lib.objects.communication.AdHocConfiguration;
import org.eclipse.mosaic.lib.objects.communication.InterfaceConfiguration;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents the module for ad hoc communication of a simulation unit.
 */
public class AdHocModule extends AbstractCommunicationModule<AdHocModuleConfiguration> {

    public AdHocModule(CommunicationModuleOwner owner, Logger log) {
        super(owner, log);
    }

    public AdHocModule(CommunicationModuleOwner owner, AtomicInteger idGenerator, Logger log) {
        super(owner, idGenerator, log);
    }

    /**
     * Enables the AdHoc communication module with default configuration parameters.
     * The transmission power will be obtained from the specific network simulator's configuration files.
     */
    public void enable() {
        enable(new AdHocModuleConfiguration().addRadio().channel(AdHocChannel.CCH).create());
    }

    /**
     * Enables the AdHoc communication module with the given configuration.
     *
     * @param configuration An AdHoc configuration determining the number of radios, channels, power and more.
     */
    @Override
    public void enable(AdHocModuleConfiguration configuration) {
        super.enable(configuration);

        if (configuration == null) {
            return;
        }

        AdHocConfiguration.Builder builder = new AdHocConfiguration.Builder(owner.getId());
        for (int i = 0; i < min(2, configuration.getNrOfRadios()); i++) {
            AdHocModuleConfiguration.AdHocModuleRadioConfiguration radio = configuration.getRadios().get(i);
            InterfaceConfiguration.Builder interfaceBuilder =
                    new InterfaceConfiguration.Builder(radio.getChannel0())
                            .ip(address)
                            .subnet(subnet)
                            .radius(radio.getDistance())
                            .power(radio.getPower());
            if (radio.getNrOfChannels() > 1) {
                interfaceBuilder.secondChannel(radio.getChannel1());
            }
            builder.addInterface(interfaceBuilder.create());
        }

        owner.sendInteractionToRti(new AdHocCommunicationConfiguration(owner.getSimulationTime(), builder.create()));

    }

    /**
     * Disables the AdHoc communication on the vehicle.
     */
    @Override
    public void disable() {
        super.disable();
        owner.sendInteractionToRti(new AdHocCommunicationConfiguration(
                owner.getSimulationTime(),
                new AdHocConfiguration.Builder(owner.getId()).create())
        );
    }

    /**
     * Returns whether the AdHoc communication is enabled and configured or disabled.
     *
     * @return status of AdHoc module
     */
    @Override
    public boolean isEnabled() {
        return configuration != null;
    }

    /**
     * Assembles and transmits a Cooperative Awareness Message (CAM).
     *
     * @return unique messageId of the CAM or {@code null} (in case of non-sent cam, e.g. due to deactivated network card)
     */
    @Override
    public Integer sendCam() {
        if (!isEnabled()) {
            log.warn("sendCAM: Ad hoc communication disabled (!adhocModule.isEnabled()).");
            return null;
        }
        return super.sendCam(createMessageRouting().singlehop().broadcast().topological().build());
    }

    @Override
    public void sendV2xMessage(V2xMessage msg) {
        if (!isEnabled()) {
            log.warn("sendV2XMessage: Ad hoc communication disabled (!adhocModule.isEnabled()).");
            return;
        }
        if (!msg.getRouting().getDestination().getRoutingType().isAdHoc()) {
            log.warn("sendV2XMessage: Message {} provided to Ad hoc module is not an Ad hoc message.", msg.getId());
            return;
        }
        super.sendV2xMessage(msg);
        log.trace("sendV2XMessage {} with sequence number {} from Ad hoc module", msg.getId(), msg.getSequenceNumber());
    }

    /**
     * Creates a new {@link AdHocMessageRoutingBuilder} for the adhoc module and returns it.
     * This object can then be used to configure the routing.
     *
     * @return the created builder for further configuration
     */
    public AdHocMessageRoutingBuilder createMessageRouting() {
        if (!(getOwner() instanceof Locatable locatable)) {
            throw new UnsupportedOperationException("Cannot send message from senders without a location.");
        }
        GeoPoint position = locatable.getPosition();
        return new AdHocMessageRoutingBuilder(getOwner().getId(), position);
    }
}
