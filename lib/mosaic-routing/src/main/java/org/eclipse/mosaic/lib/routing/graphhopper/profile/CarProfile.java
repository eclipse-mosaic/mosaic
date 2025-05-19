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
import com.graphhopper.routing.util.parsers.CarAccessParser;
import com.graphhopper.routing.util.parsers.CarAverageSpeedParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.util.PMap;

import java.util.List;

public class CarProfile extends RoutingProfile {

    public final static String NAME = "car";

    public CarProfile() {
        super(NAME,
                /*
                 * Defines an encoding for speed limits. It uses 7 bits to store the actual speed limit defined on an edge.
                 * It divides the actual speed limit by 2 before encoding it, reducing accuracy, but increasing the maximum storable
                 * speed limit. Therefore, the maximum speed limit to be stored is 254 km/h. (2^7-1 = 127 * 2 = 254).
                 * It can store different speed limits for each direction, doubling the required number of bits per edge.
                 * These are the default values used by GraphHopper, e.g., see DefaultImportRegistry.
                 */
                VehicleSpeed.create(NAME, 7, 2.0, true)
        );
    }

    @Override
    public List<TagParser> createTagParsers(EncodedValueLookup lookup) {
        return Lists.newArrayList(
                new CarAccessParser(lookup, new PMap()),
                new CarAverageSpeedParser(lookup)
        );
    }
}
