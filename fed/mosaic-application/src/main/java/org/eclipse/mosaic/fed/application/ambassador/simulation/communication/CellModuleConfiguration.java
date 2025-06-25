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

import org.eclipse.mosaic.lib.enums.RoutingType;

/**
 * <pre>
 * CellModuleConfiguration cellConfiguration = new CellModuleConfiguration()
 *     .maxDownlinkBitrate(7200 * DATA.MEGABIT)
 *     .maxUplinkBitrate(1400 * DATA.MEGABIT)
 *     .camConfiguration(new CAMConfiguration(DestinationType.CellGeoUnicast, 300));
 * </pre>
 */
public class CellModuleConfiguration extends AbstractCommunicationModuleConfiguration {

    /**
     * DL/UL bitrates to reflect a data plan from a certain provider.
     * (intended to be used by Cell)
     */
    private Long maxDownlinkBitrate;
    private Long maxUplinkBitrate;
    /**
     * Configuration for CAM messaging over cellular communication.
     * (intended for application ambassador)
     */
    private CellCamConfiguration camConfiguration = null;

    public CellModuleConfiguration maxDownlinkBitrate(long bitrate) {
        this.maxDownlinkBitrate = bitrate;
        return this;
    }

    public CellModuleConfiguration maxUplinkBitrate(long bitrate) {
        this.maxUplinkBitrate = bitrate;
        return this;
    }

    /**
     * Convenience method creating CAM config using default bitrates.
     *
     * @param camReceiver id of receiving entity
     * @return the built {@link CellModuleConfiguration}
     */
    public CellModuleConfiguration camConfigurationTopocast(String camReceiver) {
        this.camConfiguration = new CellCamConfiguration(camReceiver);
        return this;
    }

    /**
     * Convenience method creating CAM config using default bitrates.
     *
     * @param addressingMode addressing mode to be used
     * @param geoRadius      reception radius for CAM
     * @return the built {@link CellModuleConfiguration}
     */
    public CellModuleConfiguration camConfiguration(RoutingType addressingMode, double geoRadius) {
        this.camConfiguration = new CellCamConfiguration(addressingMode, geoRadius);
        return this;
    }

    /**
     * Convenience method creating CAM config using default bitrates.
     *
     * @param geoRadius reception radius for CAM
     * @return the built {@link CellModuleConfiguration}
     */
    public CellModuleConfiguration camConfiguration(double geoRadius) {
        this.camConfiguration = new CellCamConfiguration(RoutingType.CELL_GEOCAST, geoRadius);
        return this;
    }

    @Override
    public CellModuleConfiguration camMinimalPayloadLength(long minimalPayloadLength) {
        this.camMinimalPayloadLength = minimalPayloadLength;
        return this;
    }

    public Long getMaxDownlinkBitrate() {
        return maxDownlinkBitrate;
    }

    public Long getMaxUplinkBitrate() {
        return maxUplinkBitrate;
    }

    public CellCamConfiguration getCamConfiguration() {
        return camConfiguration;
    }

    /**
     * Configuration of disseminationMode (multiple unicasts or one multicast)
     * and information distance to neighbors in the vicinity.
     */
    public static class CellCamConfiguration {
        final RoutingType addressingMode;
        double geoRadius;
        String topocastReceiver;

        public CellCamConfiguration(RoutingType addressingMode, double geoRadius) {
            this.addressingMode = addressingMode;
            this.geoRadius = geoRadius;
        }

        public CellCamConfiguration(String topocastReceiver) {
            this.addressingMode = RoutingType.CELL_TOPOCAST;
            this.topocastReceiver = topocastReceiver;
        }

        public RoutingType getAddressingMode() {
            return addressingMode;
        }

        public double getGeoRadius() {
            return geoRadius;
        }

        public String getTopocastReceiver() {
            return topocastReceiver;
        }
    }
}
