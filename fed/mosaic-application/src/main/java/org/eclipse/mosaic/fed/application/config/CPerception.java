/*
 * Copyright (c) 2023 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.application.config;

import org.eclipse.mosaic.lib.geo.GeoRectangle;
import org.eclipse.mosaic.lib.gson.TypeFieldTypeAdapter;
import org.eclipse.mosaic.lib.perception.PerceptionModel;
import org.eclipse.mosaic.lib.perception.index.TrafficLightIndex;
import org.eclipse.mosaic.lib.perception.index.VehicleGrid;
import org.eclipse.mosaic.lib.perception.index.VehicleIndex;
import org.eclipse.mosaic.lib.perception.index.VehicleTree;
import org.eclipse.mosaic.lib.perception.index.WallIndex;
import org.eclipse.mosaic.lib.perception.objects.VehicleObject;
import org.eclipse.mosaic.lib.util.gson.UnitFieldAdapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.util.List;

/**
 * Configuration for the perception backend used in the ApplicationSimulator
 * to determine surrounding vehicles, traffic lights, and buildings.
 */
public class CPerception implements Serializable {

    /**
     * If set to true, uses the perception index of the SUMO traffic
     * simulator, instead of the built-in one. Default: false.
     */
    public boolean useSumoPerception = false;

    /**
     * Backend for the spatial index providing vehicle information.
     */
    public CVehicleIndex vehicleIndex = new CVehicleIndex.Tree();

    /**
     * Backend for the spatial index providing traffic light information.
     */
    public CTrafficLightIndex trafficLightIndex = new CTrafficLightIndex();

    /**
     * Backend for the spatial index providing information about building walls.
     */
    public CWallIndex wallIndex = new CWallIndex();

    /**
     * Area defining the section of the map in which traffic objects (traffic lights, vehicles) should be held in the index.
     * This is useful if only part of your network contains vehicles.
     */
    public GeoRectangle perceptionArea;

    /**
     * A base class for configuring the VehicleIndex implementation to use during perception. Based on a hidden "type" parameter,
     * JSON deserialization chooses from Tree, Grid, or SUMO configuration. Possible type values are: "tree", "grid", "sumo"
     */
    @JsonAdapter(CVehicleIndexTypeAdapterFactory.class)
    public static abstract class CVehicleIndex implements Serializable {

        /**
         * Defines if the vehicle index is enabled. Default: true
         */
        public boolean enabled = true;

        /**
         * Creates the specific {@link VehicleIndex} instance based on the present configuration.
         */
        public abstract VehicleIndex create();

        /**
         * Class for configuring a {@link VehicleTree} index.
         */
        private static class Tree extends CVehicleIndex {

            public int splitSize = 20;
            public int maxDepth = 12;

            @Override
            public VehicleIndex create() {
                return enabled ? new VehicleTree(splitSize, maxDepth) : null;
            }
        }

        /**
         * Class for configuring a {@link VehicleGrid} index.
         */
        private static class Grid extends CVehicleIndex {

            @JsonAdapter(UnitFieldAdapter.DistanceMeters.class)
            public double cellWidth = 200;

            @JsonAdapter(UnitFieldAdapter.DistanceMeters.class)
            public double cellHeight = 200;

            @Override
            public VehicleIndex create() {
                return enabled ? new VehicleGrid(cellWidth, cellHeight) : null;
            }

        }

        /**
         * Class for configuring a vehicle index based on SUMO context subscriptions.
         */
        private static class Sumo extends CVehicleIndex {

            @Override
            public VehicleIndex create() {
                return enabled ? new SumoDummyIndex() : null;
            }
        }
    }

    public static class CTrafficLightIndex implements Serializable {

        /**
         * Defines if the traffic index is enabled. Default: false
         */
        public boolean enabled = false;
        public int bucketSize = 20;

        public TrafficLightIndex create() {
            return enabled ? new TrafficLightIndex(bucketSize) : null;
        }
    }

    public static class CWallIndex implements Serializable {

        /**
         * Defines if the wall index is enabled. Default: false
         */
        public boolean enabled = false;
        public int bucketSize = 20;

        public WallIndex create() {
            return enabled ? new WallIndex(bucketSize) : null;
        }
    }

    static class CVehicleIndexTypeAdapterFactory implements TypeAdapterFactory {

        static class CVehicleIndexTypeAdapter extends TypeFieldTypeAdapter<CVehicleIndex> {

            protected CVehicleIndexTypeAdapter(TypeAdapterFactory parentFactory, Gson gson) {
                super(parentFactory, gson);
                allowNullType();
            }

            @Override
            protected Class<?> fromTypeName(String type) {
                if (type == null) {
                    return CVehicleIndex.Tree.class;
                }
                return switch (type.toLowerCase()) {
                    case "grid" -> CVehicleIndex.Grid.class;
                    case "tree" -> CVehicleIndex.Tree.class;
                    default -> throw new IllegalArgumentException("Unknown index type " + type + ". Known types are: grid, tree, sumo.");
                };
            }

            @Override
            protected String toTypeName(Class<?> typeClass) {
                if (typeClass.equals(CVehicleIndex.Tree.class)) {
                    return "tree";
                } else if (typeClass.equals(CVehicleIndex.Grid.class)) {
                    return "grid";
                }
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            return (TypeAdapter<T>) new CVehicleIndexTypeAdapter(this, gson).nullSafe();
        }
    }

    private static class SumoDummyIndex extends VehicleIndex {

        @Override
        protected void initialize() {

        }

        @Override
        public List<VehicleObject> getVehiclesInRange(PerceptionModel perceptionModel) {
            return List.of();
        }

        @Override
        protected void onVehicleAdded(VehicleObject vehicleObject) {

        }

        @Override
        protected void onIndexUpdate() {

        }

        @Override
        protected void onVehicleRemoved(VehicleObject vehicleObject) {

        }
    }


}
