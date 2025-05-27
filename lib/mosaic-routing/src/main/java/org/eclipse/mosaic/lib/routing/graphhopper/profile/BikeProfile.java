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

package org.eclipse.mosaic.lib.routing.graphhopper.profile;

import com.google.common.collect.Lists;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.parsers.BikeAccessParser;
import com.graphhopper.routing.util.parsers.BikeAverageSpeedParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.util.PMap;

import java.util.List;

public class BikeProfile extends RoutingProfile {

    public final static String NAME = "bike";

    public BikeProfile() {
        super(NAME,
                /*
                 * Defines an encoding for speed limits. It uses just 4 bits to store the actual speed limit defined on an edge.
                 * It divides the actual speed limit by 2 before encoding it, reducing accuracy, but increasing the maximum storable
                 * speed limit. Therefore, the maximum speed limit to be stored is 30 km/h. (2^4-1 = 15 * 2 = 30)
                 * These are the default values used by GraphHopper, e.g., see DefaultImportRegistry.
                 */
                VehicleSpeed.create(NAME, 4, 2.0, false)
        );
    }

    @Override
    public List<TagParser> createTagParsers(EncodedValueLookup lookup) {
        return Lists.newArrayList(
                new BikeAccessParser(lookup, new PMap()),
                new BikeAverageSpeedParser(lookup)
        );
    }
}
