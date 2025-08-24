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

import org.eclipse.mosaic.fed.application.app.api.os.modules.Locatable;
import org.eclipse.mosaic.interactions.communication.CellularCommunicationConfiguration;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.addressing.CellMessageRoutingBuilder;
import org.eclipse.mosaic.lib.objects.communication.CellConfiguration;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents the module for cellular communication of a simulation unit.
 */
public class CellModule extends AbstractCommunicationModule<CellModuleConfiguration> {

    /**
     * Default radius for geographic cam dissemination over the cellular network [m].
     */
    private final static long DEFAULT_CAM_GEO_RADIUS = 300;

    public CellModule(CommunicationModuleOwner owner, Logger log) {
        super(owner, log);
    }

    public CellModule(CommunicationModuleOwner owner, AtomicInteger idGenerator, Logger log) {
        super(owner, idGenerator, log);
    }

    /**
     * Enables the Cell module with the given configuration.
     *
     * @param configuration includes settings for maximum bitrates and CAM settings
     */
    @Override
    public void enable(CellModuleConfiguration configuration) {
        super.enable(configuration);
        if (configuration == null) {
            return;
        }

        owner.sendInteractionToRti(new CellularCommunicationConfiguration(
                owner.getSimulationTime(), new CellConfiguration(
                owner.getId(), true, configuration.getMaxDownlinkBitrate(), configuration.getMaxUplinkBitrate()))
        );
    }

    /**
     * Convenience method to enable the cell module with bare minimum default values
     * and a default configuration for CAMs using the {@link #DEFAULT_CAM_GEO_RADIUS}
     * Note: When using this method, no bitrates will be set and default values
     * configured in the Cell module will be used.
     * If you want to set these values use {@link #enable(CellModuleConfiguration)}
     */
    public void enable() {
        enable(new CellModuleConfiguration().camConfiguration(DEFAULT_CAM_GEO_RADIUS));
    }

    /**
     * Turn off the Cellular module in order to not receive messages anymore.
     */
    @Override
    public void disable() {
        super.disable();
        owner.sendInteractionToRti(
                new CellularCommunicationConfiguration(owner.getSimulationTime(), new CellConfiguration(owner.getId(), false))
        );
    }

    /**
     * Returns whether the Cell module is off or on (able to send/receive messages).
     *
     * @return whether the Cell module is off or on
     */
    @Override
    public boolean isEnabled() {
        return configuration != null;
    }

    /**
     * Sends a CAM over the cellular network to all neighbors in the vicinity,
     * using the configured sending mechanism (as multiple unicasts or one multicast).
     *
     * @return the message id of the sent CAM
     */
    @Override
    public Integer sendCam() {
        if (!isEnabled()) {
            log.warn("sendCAM: Cell communication disabled (!cellModule.isEnabled()).");
            return null;
        }

        if (configuration == null || configuration.getCamConfiguration() == null) {
            log.warn("sendCAM: No camConfiguration with addressingMode and geoRadius given.");
            return null;
        }

        CellModuleConfiguration.CellCamConfiguration camConfiguration = configuration.getCamConfiguration();
        return switch (camConfiguration.getAddressingMode()) {
            case CELL_TOPOCAST -> sendCamViaTopocast(camConfiguration);
            case CELL_GEOCAST -> sendCamViaGeoBroadcast(camConfiguration);
            case CELL_GEOCAST_MBMS -> sendCamViaGeoBroadcastMbms(camConfiguration);
            default -> {
                log.warn("sendCam: Unsupported addressing mode {}.", camConfiguration.getAddressingMode());
                yield null;
            }
        };
    }

    private Integer sendCamViaTopocast(CellModuleConfiguration.CellCamConfiguration camConfiguration) {
        return super.sendCam(createMessageRouting().destination(camConfiguration.getTopocastReceiver()).topological().build());
    }

    private Integer sendCamViaGeoBroadcast(CellModuleConfiguration.CellCamConfiguration camConfiguration) {
        if (!(getOwner() instanceof Locatable locatable)) {
            throw new UnsupportedOperationException("Cannot send CAM for entities without a location.");
        }
        final GeoCircle destination = new GeoCircle(locatable.getPosition(), camConfiguration.getGeoRadius());
        return super.sendCam(createMessageRouting().broadcast().geographical(destination).build());
    }

    private Integer sendCamViaGeoBroadcastMbms(CellModuleConfiguration.CellCamConfiguration camConfiguration) {
        if (!(getOwner() instanceof Locatable locatable)) {
            throw new UnsupportedOperationException("Cannot send CAM for entities without a location.");
        }
        final GeoCircle destination = new GeoCircle(locatable.getPosition(), camConfiguration.getGeoRadius());
        return super.sendCam(createMessageRouting().broadcast().geographical(destination).mbs().build());
    }

    /**
     * Sends a message to the addressed node(s) over the cellular network.
     *
     * @param msg the message to send
     */
    @Override
    public void sendV2xMessage(V2xMessage msg) {
        if (!isEnabled()) {
            log.warn("sendV2XMessage: Cell communication disabled (!cellModule.isEnabled()).");
            return;
        }
        if (!msg.getRouting().getDestination().getRoutingType().isCell()) {
            log.warn("sendV2XMessage: Message {} provided to Cell module is no Cell message.", msg.getId());
            return;
        }
        super.sendV2xMessage(msg);
        log.trace("sendV2XMessage {} with sequence number {} from Cell module", msg.getId(), msg.getSequenceNumber());
    }

    /**
     * Creates a new {@link CellMessageRoutingBuilder} for the cell module and returns it.
     * This object can then be used to configure the routing.
     *
     * @return the created builder for further configuration
     */
    public CellMessageRoutingBuilder createMessageRouting() {
        GeoPoint position = getOwner() instanceof Locatable
                ? ((Locatable) getOwner()).getPosition()
                : null;
        return new CellMessageRoutingBuilder(getOwner().getId(), position);
    }
}
