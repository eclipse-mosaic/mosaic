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

package org.eclipse.mosaic.fed.ns3.ambassador.config;

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.GeoPoint;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Storage class for the eNodeB configuration (from regions.json).
 * It contains the properties of the {@link CNodeBProperties}
 */
public final class CNodeB {

    /**
     * List of eNodeBs and their properties.
     * (we use same wording 'regions' as in cell ambassador)
     */
    public List<CNodeBProperties> regions = new ArrayList<>();

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        for (CNodeBProperties enb : regions) {
            builder.append(enb.toString()).append("\n");
        }
        return builder.toString();
    }

    /**
     * Configuration structure of one single NodeB.
     */
    public static class CNodeBProperties implements Serializable {
        /**
         * The eNodeB geo position.
         */
        public GeoPoint geoPosition;

        /**
         * The eNodeB cartesian position. GeoPosition will take precedence.
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

}
