/*
 * Copyright (c) 2022 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.lib.perception.index;

import org.eclipse.mosaic.lib.geo.CartesianRectangle;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroupInfo;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;
import org.eclipse.mosaic.lib.perception.PerceptionModel;
import org.eclipse.mosaic.lib.perception.PerceptionIndex;
import org.eclipse.mosaic.lib.perception.objects.SpatialObjectAdapter;
import org.eclipse.mosaic.lib.perception.objects.TrafficLightObject;
import org.eclipse.mosaic.lib.spatial.KdTree;
import org.eclipse.mosaic.lib.spatial.SpatialTreeTraverser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrafficLightIndex {

    /**
     * Stores {@link TrafficLightObject}s for fast removal and position update.
     */
    final Map<String, TrafficLightObject> indexedTrafficLights = new HashMap<>();

    private final int bucketSize;

    private CartesianRectangle bounds;
    private KdTree<TrafficLightObject> trafficLightTree;

    private final SpatialTreeTraverser.InRadius<TrafficLightObject> treeTraverser;

    private boolean triggerNewTree = false;

    public TrafficLightIndex(int bucketSize) {
        this.bucketSize = bucketSize;
        this.treeTraverser = new SpatialTreeTraverser.InRadius<>();
    }

    public void initialize(CartesianRectangle bounds) {
        this.bounds = bounds;
    }

    /**
     * Queries the {@link PerceptionIndex} and returns all traffic lights inside the {@link PerceptionModel}.
     */
    public List<TrafficLightObject> getTrafficLightsInRange(PerceptionModel perceptionModel) {
        GeoCircle c = new GeoCircle(
                perceptionModel.getBoundingBox().center.toGeo(),
                perceptionModel.getBoundingBox().center.distanceSqrTo(perceptionModel.getBoundingBox().min)
        );
        return this.getTrafficLightsInCircle(c).stream().filter(perceptionModel::isInRange).toList();
    }

    /**
     * Queries the {@link PerceptionIndex} and returns all traffic lights inside the {@link GeoCircle}.
     */
    public List<TrafficLightObject> getTrafficLightsInCircle(GeoCircle circle) {
        if (triggerNewTree) {
            rebuildTree();
        }
        treeTraverser.setup(circle.getCenter().toVector3d(), circle.getRadius());
        treeTraverser.traverse(trafficLightTree);
        return treeTraverser.getResult();
    }

    /**
     * Adds traffic lights to the spatial index, as their positions are static it is sufficient
     * to store positional information only once.
     *
     * @param trafficLightGroup the registration interaction
     */
    public void addTrafficLight(TrafficLightGroup trafficLightGroup) {
        triggerNewTree = true;
        String trafficLightGroupId = trafficLightGroup.getGroupId();
        trafficLightGroup.getTrafficLights().forEach(
                (trafficLight) -> {
                    String trafficLightId = calculateTrafficLightId(trafficLightGroupId, trafficLight.getIndex());
                    if (bounds.contains(trafficLight.getPosition().toCartesian())) { // check if inside bounding area
                        indexedTrafficLights.computeIfAbsent(trafficLightId, TrafficLightObject::new)
                                .setTrafficLightGroupId(trafficLightGroupId)
                                .setPosition(trafficLight.getPosition().toCartesian())
                                .setIncomingLane(trafficLight.getIncomingLane())
                                .setOutgoingLane(trafficLight.getOutgoingLane())
                                .setTrafficLightState(trafficLight.getCurrentState());
                    }
                }
        );
    }

    /**
     * Updates the {@link PerceptionIndex} in regard to traffic lights. The unit simulator has to be queried as
     * {@code TrafficLightUpdates} do not contain all necessary information.
     *
     * @param trafficLightGroupsToUpdate a list of information packages transmitted by the traffic simulator
     */
    public void updateTrafficLights(Map<String, TrafficLightGroupInfo> trafficLightGroupsToUpdate) {
        trafficLightGroupsToUpdate.forEach(
                (trafficLightGroupId, trafficLightGroupInfo) -> {
                    List<TrafficLightState> trafficLightStates = trafficLightGroupInfo.getCurrentState();
                    for (int i = 0; i < trafficLightStates.size(); i++) {
                        String trafficLightId = calculateTrafficLightId(trafficLightGroupId, i);
                        final TrafficLightState trafficLightState = trafficLightStates.get(i);
                        indexedTrafficLights.computeIfPresent(trafficLightId, (id, trafficLightObject)
                                -> trafficLightObject.setTrafficLightState(trafficLightState));
                    }
                }
        );
    }

    /**
     * This is used as a workaround to get a unique id for each traffic light signal, by combining the group id with the index
     */
    private String calculateTrafficLightId(String trafficLightGroupId, int trafficLightIndex) {
        return trafficLightGroupId + "_" + trafficLightIndex;
    }

    private void rebuildTree() {
        List<TrafficLightObject> allTrafficLights = new ArrayList<>(indexedTrafficLights.values());
        trafficLightTree = new KdTree<>(new SpatialObjectAdapter<>(), allTrafficLights, bucketSize);
        triggerNewTree = false;
    }

    /**
     * Returns the number of TLs in the simulation.
     *
     * @return the number of TLs
     */
    public int getNumberOfTrafficLights() {
        return indexedTrafficLights.size();
    }
}
