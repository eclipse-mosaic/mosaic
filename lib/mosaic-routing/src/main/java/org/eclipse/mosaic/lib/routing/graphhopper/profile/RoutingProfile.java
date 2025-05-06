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

import org.eclipse.mosaic.lib.routing.graphhopper.util.VehicleEncoding;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.util.parsers.TagParser;

import java.util.List;

public abstract class RoutingProfile {

    private final VehicleEncoding vehicleEncoding;
    private final String name;

    protected RoutingProfile(String name, DecimalEncodedValue speedEncoding, DecimalEncodedValue priorityEncoding) {
        this.name = name;
        this.vehicleEncoding = new VehicleEncoding(name, speedEncoding, priorityEncoding);
    }

    public String getName() {
        return name;
    }

    public VehicleEncoding getVehicleEncoding() {
        return vehicleEncoding;
    }

    public abstract List<TagParser> createTagParsers(EncodedValueLookup lookup);

}
