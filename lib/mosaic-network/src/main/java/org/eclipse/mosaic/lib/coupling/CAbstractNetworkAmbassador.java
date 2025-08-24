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

package org.eclipse.mosaic.lib.coupling;

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.GeoPoint;

import com.google.gson.GsonBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CAbstractNetworkAmbassador {

    /**
     * Name to the federate configuration file.
     */
    public String federateConfigurationFile;

    /**
     * List of base stations and their properties.
     */
    public List<CBaseStationProperties> baseStations = new ArrayList<>();

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        for (CBaseStationProperties bs : baseStations) {
            builder.append(bs.toString()).append("\n");
        }
        return builder.toString();
    }

    /**
     * Configuration structure of one single base station.
     */
    public static class CBaseStationProperties implements Serializable {

        /**
         * The base stations geo position.
         */
        public GeoPoint geoPosition;

        /**
         * The base stations cartesian position. GeoPosition will take precedence.
         */
        public CartesianPoint cartesianPosition;

        @Override
        public String toString() {
            String s = "";
            s += " geoPosition: " + ((geoPosition != null) ? geoPosition.toString() : "null");
            s += " cartesianPosition: " + ((cartesianPosition != null) ? cartesianPosition.toString() : "null");
            return s;
        }
    }

    static GsonBuilder createConfigBuilder() {
        return new GsonBuilder()
                .setFieldNamingStrategy(f -> switch (f.getName()) {
                    case "latitude" -> "lat";
                    case "longitude" -> "lon";
                    case "altitude" -> "alt";
                    default -> f.getName();
                });
    }


}
