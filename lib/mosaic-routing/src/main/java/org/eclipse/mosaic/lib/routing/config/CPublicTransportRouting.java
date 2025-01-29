/*
 * Copyright (c) 2024 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.lib.routing.config;

import java.io.Serializable;

public class CPublicTransportRouting implements Serializable {

    /**
     * Declares if PT routing is enabled (default=false).
     */
    public boolean enabled = false;

    /**
     * The path to the OSM file which is used to calculate walking between PT legs.
     * The provided path is expected to be relative to the application directory of the scenario.
     */
    public String osmFile = "map.osm";

    /**
     * The path to the GTFS file (ZIP archive) which contains the whole PT schedule.
     * The provided path is expected to be relative to the application directory of the scenario.
     */
    public String gtfsFile = "gtfs.zip";

    /**
     * The real time in ISO format at which the beginning of the simulation should point at.
     * Example format: 2024-11-27T10:15:30
     */
    public String scheduleDateTime = "2024-12-03T10:15:30";

    /**
     * The time zone of the location where the PT system is implemented.
     */
    public String timeZone = "ECT";
}
