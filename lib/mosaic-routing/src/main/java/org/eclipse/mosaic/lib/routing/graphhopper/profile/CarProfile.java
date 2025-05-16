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
                VehicleSpeed.create(NAME, 7, 2.0, true),
                null
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
