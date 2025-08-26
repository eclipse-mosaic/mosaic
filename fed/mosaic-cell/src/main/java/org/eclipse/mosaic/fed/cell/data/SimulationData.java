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

import org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.communication.CellConfiguration;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * SimulationData Singleton that manages all dynamic simulation data from MOSAIC.
 * It includes:
 * <ul>
 * <li/>the current simulation time
 * <li/>the position of each known node
 * <li/>the vehicle speeds (rsus, tl, cs have a speed of 0)
 * </ul>
 */
public enum SimulationData {
    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(SimulationData.class);

    /**
     * A simulation node with specific information about it.
     */
    private static class SimulationNode {
        private CartesianPoint position;
        private CNetworkProperties networkProperties;
        private Double speed;
        private CellConfiguration cellConfiguration;
    }

    private final Map<String, SimulationNode> simulationNodeMap = new HashMap<>();

    /**
     * Set or update the position for a node.
     * To be only called by the CellAmbassador (throw exception when anything is wrong).
     *
     * @param nodeId   the id of the node to update
     * @param position the new position for the node
     */
    public void setPositionOfNode(String nodeId, CartesianPoint position) {
        if (nodeId != null && position != null) {
            getOrCreate(nodeId).position = position;
        } else {
            throw new RuntimeException(
                    "Unable to setPositionOfNode for nodeId=" + nodeId + ", position=" + position);
        }
    }

    /**
     * Set or update the region for a node.
     *
     * @param nodeId the id of the node.
     * @param networkProperties the new region for the node.
     */
    public void setNetworkPropertiesOfNode(String nodeId, CNetworkProperties networkProperties) {
        Validate.notNull(networkProperties, "Unable to setNetworkPropertiesOfNode for nodeId=" + nodeId + ", because the given region is null");
        Validate.notNull(nodeId, "Unable to setNetworkPropertiesOfNode for nodeId=" + nodeId + ", networkProperties=" + networkProperties.id);

        getOrCreate(nodeId).networkProperties = networkProperties;
    }

    /**
     * Set or update the speed for a node.
     * To be only called by the CellAmbassador (throw exception when anything is wrong).
     *
     * @param nodeId the id of the node to update.
     * @param speed  the new speed of the node.
     */
    public void setSpeedOfNode(String nodeId, double speed) {
        if (nodeId != null && speed >= 0) {
            getOrCreate(nodeId).speed = speed;
        } else {
            throw new RuntimeException(
                    "Unable to setSpeedOfNode for nodeId=" + nodeId + ", speed=" + speed + "(negative speeds not allowed)");
        }
    }

    /**
     * Set or update the cell config for a node.
     *
     * @param nodeId The id of the node whose cell config is updated.
     * @param config The new cell configuration that is used after the update.
     */
    public void setCellConfigurationOfNode(String nodeId, CellConfiguration config) {
        if (nodeId != null && config != null) {
            getOrCreate(nodeId).cellConfiguration = config;
        } else {
            throw new RuntimeException("Unable to set Configuration of Node for nodeId=" + nodeId);
        }
    }

    /**
     * Get the simulation node or generate a new simulation node.
     *
     * @param nodeId the id of the simulation node.
     * @return the simulation node of existing or new created simulation node.
     */
    private SimulationNode getOrCreate(String nodeId) {
        SimulationNode node = simulationNodeMap.get(nodeId);
        if (node == null) {
            node = new SimulationNode();
            simulationNodeMap.put(nodeId, node);
        }
        return node;
    }

    /**
     * Removes the node from position and speed table.
     * To be only called by the CellAmbassador (throw exception when anything is wrong).
     *
     * @param nodeId the id of the node to remove.
     */
    public void removeNode(String nodeId) {
        simulationNodeMap.remove(nodeId);
    }

    /**
     * Gets all currently known nodes in the simulation.
     *
     * @return A set of all nodes.
     */
    public Set<String> getAllNodesInSimulation() {
        return simulationNodeMap.keySet();
    }

    /**
     * Returns the position of a node from the position table.
     * Returns null if the node is non existing.
     *
     * @param nodeId Name of the node.
     * @return Position of the node, null if non existing.
     */
    public CartesianPoint getPositionOfNode(String nodeId) {
        SimulationNode node = simulationNodeMap.get(nodeId);
        return node != null ? node.position : null;
    }

    /**
     * Returns the base region of a node from the region table.
     * Returns null if the node is non existing.
     *
     * @param nodeId The id of the node.
     * @return Region of the node if existing else null.
     */
    public CNetworkProperties getNetworkPropertiesOfNode(String nodeId) {
        SimulationNode node = simulationNodeMap.get(nodeId);
        return node != null ? node.networkProperties : null;
    }

    /**
     * Returns the speed of a node from the speed table.
     * Returns -1.0 if the node is not there.
     *
     * @param nodeId Name of the node
     * @return Speed of the node, -1.0 if not there (should not happen)
     */
    public double getSpeedOfNode(String nodeId) {
        SimulationNode node = simulationNodeMap.get(nodeId);
        if (node != null && node.speed != null) {
            return node.speed;
        } else {
            log.debug("Tried getting speed of node {}, but isn't there.", nodeId);
            return -1.0d;
        }
    }

    /**
     * Returns the cell configuration that is used for a node.
     *
     * @param nodeId The id of the node whose cell configuration is requested.
     * @return The cell configuration of the node with the id nodeId.
     * @throws InternalFederateException if no {@link CellConfiguration} was found for the given node
     */
    public CellConfiguration getCellConfigurationOfNode(@Nonnull String nodeId) throws InternalFederateException {
        SimulationNode node;
        try {
            node = simulationNodeMap.get(nodeId);
            Validate.notNull(node.cellConfiguration);
        } catch (NullPointerException e) {
            log.warn("Tried getting cell configuration of node {}, but it was not available", nodeId);
            throw new InternalFederateException(e);
        }
        return node.cellConfiguration;
    }

    /**
     * Checks whether a cell configuration is configured for a node.
     * Note: Servers are not configured using a {@link CellConfiguration} and are always enabled.
     *
     * @param nodeId The id of the node which is checked for an existing cell configuration.
     * @return Returns true if a cell configuration is configured for the id nodeId.
     */
    public boolean containsCellConfigurationOfNode(String nodeId) {
        SimulationNode node = simulationNodeMap.get(nodeId);
        return node != null && (node.cellConfiguration != null);
    }
}
