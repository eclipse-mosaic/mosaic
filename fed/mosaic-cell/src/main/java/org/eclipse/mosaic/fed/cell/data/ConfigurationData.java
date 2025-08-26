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

package org.eclipse.mosaic.fed.cell.data;

import org.eclipse.mosaic.fed.cell.config.CCell;
import org.eclipse.mosaic.fed.cell.config.CNetwork;
import org.eclipse.mosaic.fed.cell.config.CRegion;
import org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties;

/**
 * ConfigurationData Singleton that holds references to the cell configurations, which are
 * - set once by the CellAmbassador and
 * - needed and used by the Utilities (the actual models).
 */
public enum ConfigurationData {
    INSTANCE;

    private CCell cellAmbassadorConfig;
    private CNetwork networkConfig;
    private CRegion regionConfig;

    /**
     * Set the network configuration file.
     *
     * @param networkConfig Network configuration file {@link CNetwork}.
     */
    public void setNetworkConfig(CNetwork networkConfig) {
        if (networkConfig != null) {
            this.networkConfig = networkConfig;
        }
    }

    /**
     * Set the region configuration file.
     *
     * @param regionConfig Region configuration file {@link CRegion}.
     */
    public void setRegionConfig(CRegion regionConfig) {
        if (regionConfig != null) {
            this.regionConfig = regionConfig;
        }
    }

    /**
     * Set the cell configuration file.
     *
     * @param cellAmbassadorConfig Cell configuration file {@link CCell}.
     */
    public void setCellConfig(CCell cellAmbassadorConfig) {
        if (cellAmbassadorConfig != null) {
            this.cellAmbassadorConfig = cellAmbassadorConfig;
        }
    }

    /**
     * Get the network configuration file.
     *
     * @return Network configuration file {@link CNetwork}.
     */
    public CNetwork getNetworkConfig() {
        return networkConfig;
    }

    /**
     * Get the region configuration file.
     *
     * @return Region configuration file {@link CRegion}.
     */
    public CRegion getRegionConfig() {
        return regionConfig;
    }

    /**
     * Get the cell configuration file.
     *
     * @return Cell configuration file {@link CCell}.
     */
    public CCell getCellConfig() {
        return cellAmbassadorConfig;
    }

    /**
     * Checks if server has been configured in network.json and
     * return it.
     * Note: This returns a copy of the configuration in case multiple servers
     * use the same configuration.
     *
     * @param serverGroupName name of the defined group in mapping
     * @return the configured {@code CNetworkProperties} if server region exists, else {@code null}
     */
    public CNetworkProperties getNetworkPropertiesForServer(String serverGroupName) {
        for (CNetworkProperties server : networkConfig.servers) {
            if (server.id.equals(serverGroupName)) {
                return server;
            }
        }
        return null;
    }
}
