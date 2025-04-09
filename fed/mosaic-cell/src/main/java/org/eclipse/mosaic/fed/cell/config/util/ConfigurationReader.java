/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

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

package org.eclipse.mosaic.fed.cell.config.util;

import org.eclipse.mosaic.fed.cell.config.CCell;
import org.eclipse.mosaic.fed.cell.config.CNetwork;
import org.eclipse.mosaic.fed.cell.config.CRegion;
import org.eclipse.mosaic.fed.cell.config.gson.ConfigBuilderFactory;
import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import javax.annotation.Nonnull;

/**
 * This class is used to read the configurations used in the CellAmbassador. It mainly
 * utilizes the {@link ObjectInstantiation} to read the JSON-configs and additionally sets
 * some values used for calculation.
 */
public class ConfigurationReader {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationReader.class);

    /**
     * Read the cell configuration file.
     *
     * @param cellConfPath The path to a cell configuration file (as String)
     * @return Returns the cell configuration that is read from the file in the path cellConfPath
     * @throws InternalFederateException if config couldn't be read
     */
    public static CCell importCellConfig(String cellConfPath) throws InternalFederateException {
        return readConfigFile(cellConfPath, ConfigBuilderFactory.getConfigBuilder(), CCell.class);
    }

    /**
     * Read the network configuration file.
     *
     * @param networkConfPath The path to a network configuration file (as String)
     * @return Returns the network configuration that is read from the file in the path networkConfPath
     * @throws InternalFederateException if config couldn't be read
     */
    public static CNetwork importNetworkConfig(String networkConfPath) throws InternalFederateException {
        if (!new File(networkConfPath).exists()) {
            throw new InternalFederateException(
                    String.format("The network config '%s' does not exist.", new File(networkConfPath).getName())
            );
        }
        CNetwork networkConfig = readConfigFile(networkConfPath, ConfigBuilderFactory.getConfigBuilder(), CNetwork.class);
        networkConfig.globalNetwork.downlink.maxCapacity = networkConfig.globalNetwork.downlink.capacity;
        networkConfig.globalNetwork.uplink.maxCapacity = networkConfig.globalNetwork.uplink.capacity;

        // server capacity isn't limited by the network configuration, but handled within the cell module configuration
        networkConfig.servers.forEach((server) -> {
            if (server.downlink.capacity != null || server.uplink.capacity != null) {
                log.warn("It seems like you've tried to set a capacity value for a server. This should be done when enabling the "
                        + "CellModule in you application. Your set values will be dismissed");
            }
            if (server.downlink.multicast != null) {
                log.warn("It seems like you've tried to set the downlink multicast for a server."
                        + "Servers can't be addressed with multicasts. Your set values will be dismissed");
            }
            server.downlink.maxCapacity = server.downlink.capacity = Long.MAX_VALUE;
            server.uplink.maxCapacity = server.uplink.capacity = Long.MAX_VALUE;
            server.downlink.multicast = null;
        });

        return networkConfig;
    }

    /**
     * Read the regions configuration file.
     *
     * @param regionConfigPath The path to a region configuration file (as String)
     * @return Returns the region configuration that is read from the file in the path regionConfigPath
     * @throws InternalFederateException if config couldn't be read
     */
    public static CRegion importRegionConfig(String regionConfigPath) throws InternalFederateException {
        CRegion regionConfig = readConfigFile(regionConfigPath, ConfigBuilderFactory.getConfigBuilder(), CRegion.class);
        regionConfig.regions.forEach((region) -> {
            region.downlink.capacity = ObjectUtils.defaultIfNull(region.downlink.capacity, 0L);
            region.uplink.capacity = ObjectUtils.defaultIfNull(region.uplink.capacity, 0L);
            /*
             * Here the maximum capacity is set for the region.
             * The maximum capacity is needed to compute what
             * share of the maximum capacity is left during the simulation.
             * The remaining capacity is needed to decided whether a new transmission is allowed.
             * If not enough capacity is left, a new packet is dropped.
             * The reason to drop the packet is that the packet would otherwise be
             * delayed for too long due to the insufficient capacity.
             */
            region.downlink.maxCapacity = region.downlink.capacity;
            region.uplink.maxCapacity = region.uplink.capacity;
        });
        return regionConfig;
    }

    private static <T> T readConfigFile(String configPath, @Nonnull GsonBuilder configBuilder, Class<T> clazz) throws InternalFederateException {
        ObjectInstantiation<T> oiRegion = new ObjectInstantiation<>(clazz, log);
        try {
            File configFile = new File(configPath);
            return oiRegion.readFile(configFile, configBuilder);
        } catch (InstantiationException | JsonParseException ex) {
            log.error("Could not read configuration " + configPath + "", ex);
            throw new InternalFederateException(ex);
        }
    }
}
