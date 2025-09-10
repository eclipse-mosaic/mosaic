/*
 * Copyright (c) 2025 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.application.ambassador.simulation.electric;

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.electricity.ChargingStationData;
import org.eclipse.mosaic.lib.perception.objects.SpatialObjectAdapter;
import org.eclipse.mosaic.lib.spatial.KdTree;
import org.eclipse.mosaic.lib.spatial.SpatialTreeTraverser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ChargingStationIndex} holds Charging Stations in a tree structure, sorted by their position.
 * The tree is initialized and gets updated lazily when a search is performed.
 */
public class ChargingStationIndex {

    private final int bucketSize;

    /**
     * Stores {@link ChargingStationObject}s for fast removal and position update.
     */
    final Map<String, ChargingStationObject> indexedChargingStations = new HashMap<>();

    private KdTree<ChargingStationObject> chargingStationTree;

    private SpatialTreeTraverser.InRadius<ChargingStationObject> treeTraverser;

    private boolean needsTreeUpdate = false;

    /**
     * Inits a {@link ChargingStationIndex} with default bucket size of 20.
     * Bucket size describes the item capacity of one tree node.
     */
    public ChargingStationIndex() {
        this.bucketSize = 20;
        this.treeTraverser = new SpatialTreeTraverser.InRadius<>();
    }

    /**
     * Adds a Charging Station to the tree.
     * Be sure to add {@link ChargingStationData} using {@link ChargingStationIndex#updateChargingStation(ChargingStationData)}.
     * <p>The CS is inserted into the tree when it is queried (e.g. {@link ChargingStationIndex#getChargingStationsInCircle(GeoCircle)}or
     * {@link ChargingStationIndex#getNumberOfChargingStations()}</p>
     *
     * @param id       ID of the ChargingStation
     * @param position Position of the ChargingStation
     */
    public void addChargingStation(String id, GeoPoint position) {
        needsTreeUpdate = true;
        indexedChargingStations.computeIfAbsent(id, ChargingStationObject::new).setPosition(position.toCartesian());
    }

    /**
     * Replaces the stations data object.
     */
    public void updateChargingStation(ChargingStationData chargingStationData) {
        CartesianPoint newPosition = chargingStationData.getPosition().toCartesian();
        ChargingStationObject cs = indexedChargingStations.get(chargingStationData.getName());

        if (cs.getPosition().toCartesian().equals(newPosition)) {
            cs.setChargingStationData(chargingStationData);
            return;
        }

        cs.setChargingStationData(chargingStationData).setPosition(newPosition);
        needsTreeUpdate = true;
    }

    private void updateSearchTree() {
        if (!needsTreeUpdate) {
            return;
        }

        List<ChargingStationObject> allChargingStations = new ArrayList<>(indexedChargingStations.values());
        chargingStationTree = new KdTree<>(new SpatialObjectAdapter<>(), allChargingStations, bucketSize);
        needsTreeUpdate = false;
    }

    public List<ChargingStationObject> getChargingStationsInCircle(GeoCircle circle) {
        updateSearchTree();
        treeTraverser.setup(circle.getCenter().toVector3d(), circle.getRadius());
        treeTraverser.traverse(chargingStationTree);
        return treeTraverser.getResult();
    }

    public int getNumberOfChargingStations() {
        updateSearchTree();
        return chargingStationTree.getRoot().size();
    }

}
