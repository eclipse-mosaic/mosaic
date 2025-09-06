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

package org.eclipse.mosaic.fed.cell.utility;

import org.eclipse.mosaic.fed.cell.config.CNetwork;
import org.eclipse.mosaic.fed.cell.config.model.CMobileNetworkProperties;
import org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties;
import org.eclipse.mosaic.fed.cell.data.ConfigurationData;
import org.eclipse.mosaic.fed.cell.data.SimulationData;
import org.eclipse.mosaic.lib.geo.CartesianArea;
import org.eclipse.mosaic.lib.geo.CartesianCircle;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.CartesianPolygon;
import org.eclipse.mosaic.lib.geo.GeoArea;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoPolygon;
import org.eclipse.mosaic.lib.geo.GeoRectangle;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * This class contains static methods to make the region handling easier.
 */
public class RegionUtility {

    private static final Logger log = LoggerFactory.getLogger(RegionUtility.class);

    private static RegionsIndex regionsIndex;

    public static void initializeRegionsIndex(final Collection<CMobileNetworkProperties> regions) {
        regionsIndex = new RegionsIndex(regions);
    }

    /**
     * Gets all nodes in the given region.
     *
     * @param region the region.
     * @return list of all nodes.
     */
    public static List<String> getNodesForRegion(CNetworkProperties region) {
        final List<String> nodesForRegion = new ArrayList<>();
        final Set<String> allNodes = SimulationData.INSTANCE.getAllNodesInSimulation();
        for (String node : allNodes) {
            final String regionForNode = RegionUtility.getRegionForNode(node).id;
            if (regionForNode.equals(region.id)) {
                nodesForRegion.add(node);
            }
        }

        return nodesForRegion;
    }

    /**
     * Convenience function for getting the region of a specified node.
     *
     * @param node the node.
     * @return the region the node is in.
     */
    public static CNetworkProperties getRegionForNode(String node) {
        if (node == null) {

            log.warn("nodeID is null, returning default region");
            return ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork;
        }

        CNetworkProperties region = SimulationData.INSTANCE.getNetworkPropertiesOfNode(node);

        if (region == null) {
            region = getNetworkPropertiesForRegionAt(SimulationData.INSTANCE.getPositionOfNode(node));
            SimulationData.INSTANCE.setNetworkPropertiesOfNode(node, region);
        }
        log.trace("Getting region for node {} at region {}", node, region.id);
        return region;
    }

    /**
     * Get the region Id for a given Node.
     *
     * @param nodeId Node Id to find the region.
     * @return The region Id of the node.
     * @throws InternalFederateException Exception in case node Id is null.
     */
    public static String getRegionIdForNode(String nodeId) throws InternalFederateException {
        Validate.notNull(nodeId, "Could not get the region id because the nodeId is null");
        final CNetworkProperties nodeRegion = SimulationData.INSTANCE.getNetworkPropertiesOfNode(nodeId);

        if (nodeRegion == null) {
            throw new InternalFederateException("Could not find the region id for node " + nodeId);
        } else {
            log.debug("getRegionIdForNode for node {}", nodeId);
            return nodeRegion.id;
        }
    }

    /**
     * Get the base region for a given cartesian position.
     *
     * @param position Position to find region for.
     * @return Base region.
     */
    public static CNetworkProperties getNetworkPropertiesForRegionAt(CartesianPoint position) {
        if (position == null) {
            return ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork;
        }

        CNetworkProperties region = regionsIndex.getRegion(position);
        if (region == null) {
            region = ConfigurationData.INSTANCE.getNetworkConfig().globalNetwork;
        }
        return region;
    }

    /**
     * Get all nodes for a destination area (of a geocast message).
     *
     * @param geoArea destination geoArea (GeoCircle or GeoRectangle).
     * @return list of all nodes within the destination area.
     */
    public static List<String> getNodesForDestinationArea(GeoArea geoArea) {
        final CartesianArea area = geoArea.toCartesian();
        List<String> nodes = new ArrayList<>();
        for (String n : SimulationData.INSTANCE.getAllNodesInSimulation()) {
            CartesianPoint nodePos = SimulationData.INSTANCE.getPositionOfNode(n);
            if (nodePos != null && area.contains(nodePos)) {
                nodes.add(n);
            }
        }

        return nodes;
    }

    /**
     * Get all regions for a destination area (of a geocast message).
     *
     * @param geoArea The destination geoArea.
     * @return List of all regions that intersect the destination area.
     */
    public static List<CNetworkProperties> getRegionsForDestinationArea(GeoArea geoArea) {

        List<CNetworkProperties> regions = new ArrayList<>();

        if (geoArea instanceof GeoCircle geoCircle) {
            for (CMobileNetworkProperties region : ConfigurationData.INSTANCE.getRegionConfig().regions) {
                if (isCircleIntersectingPolygon(geoCircle.toCartesian(), region.getCapoArea())){
                    regions.add(region);
                }
            }
        } else {
            if (geoArea instanceof GeoRectangle geoRectangle) {
                geoArea = geoRectangle.toPolygon();
            }
            CartesianPolygon destPolygon = ((GeoPolygon) geoArea).toCartesian();
            for (CMobileNetworkProperties region : ConfigurationData.INSTANCE.getRegionConfig().regions) {
                if (region.getCapoArea().isIntersectingPolygon(destPolygon)) {
                    regions.add(region);
                }
            }
        }

        return regions;
    }

    /**
     * Collision detection for the collision of a circle with a polygon.
     *
     * @param destinationArea The circle.
     * @param regionalArea The polygon.
     * @return true if the circle and the polygon collide.
     */
    private static boolean isCircleIntersectingPolygon(CartesianCircle destinationArea,
                                                        CartesianPolygon regionalArea) {
        CartesianPoint circleCenter = destinationArea.getCenter();
        // Check if circle center is contained in polygon
        if (regionalArea.contains(circleCenter)) {
            return true;
        }
        // Check if any edge of the regionalArea intersects the circular destinationArea
        for (int idx = 1; idx < regionalArea.getVertices().size() ; idx++) {
            CartesianPoint verticeA = regionalArea.getVertices().get(idx - 1);
            CartesianPoint verticeB = regionalArea.getVertices().get(idx);
            double dx = verticeA.getX() - verticeB.getX();
            double dy = verticeA.getY() - verticeB.getY();
            double edgeLength = Math.sqrt(dx * dx + dy * dy);
            double t = ((verticeB.getX() - circleCenter.getX()) * dx + (verticeB.getY() - circleCenter.getY()) * dy) / edgeLength;
            if (t >= 0 && t <= 1) {
                // If line through circle center and perpendicular to edge vector intersects the edge, check distance to circle center
                double distanceToCircleCenter = Math.abs((verticeB.getX() - circleCenter.getX()) * dy
                        - (verticeB.getY() - circleCenter.getY()) * dx)
                        / edgeLength;
                if (distanceToCircleCenter <= destinationArea.getRadius()) {
                    return true;
                }
            } else if (verticeB.distanceTo(circleCenter) <= destinationArea.getRadius()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get a whole region handle for a region name string.
     *
     * @param regionName the string.
     * @return the handle.
     */
    public static CNetworkProperties getRegionByName(String regionName) {
        for (CNetworkProperties region : getAllRegions(true, true)) {
            if (regionName.equals(region.id)) {
                return region;
            }
        }
        return null;
    }

    /**
     * Get the list of all configured regions, optionally including the "globalNetwork".
     *
     * @param includeGlobal include the "globalNetwork", otherwise deliver only AreaRegions.
     * @param includeServers include server "regions"
     * @return list of all regions.
     */
    public static List<CNetworkProperties> getAllRegions(boolean includeGlobal, boolean includeServers) {
        List<CNetworkProperties> regions = new ArrayList<>();
        if (ConfigurationData.INSTANCE.getRegionConfig() != null) {
            regions.addAll(ConfigurationData.INSTANCE.getRegionConfig().regions);
        }

        CNetwork networkConfig = ConfigurationData.INSTANCE.getNetworkConfig();
        if (networkConfig != null) {
            if (includeGlobal) {
                regions.add(networkConfig.globalNetwork);
            }
            if (includeServers && networkConfig.servers != null) {
                regions.addAll(networkConfig.servers);
            }
        }
        return regions;
    }
}