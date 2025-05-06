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

package org.eclipse.mosaic.lib.routing.graphhopper.util;

import org.eclipse.mosaic.lib.routing.graphhopper.profile.RoutingProfile;

import com.graphhopper.routing.ev.FerrySpeed;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadClassLink;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.Smoothness;
import com.graphhopper.routing.util.EncodingManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * In GraphHopper, any data for edges, nodes, and turns, are stored with as low overhead
 * as possible. To achieve this, {@link com.graphhopper.routing.ev.EncodedValue}s
 * to encode and decode any data. This class, encapsulates the initialization and access to single
 * {@link com.graphhopper.routing.ev.EncodedValue}s, making it easier to use them in code.
 */
public class RoutingProfileManager {

    private final EncodingManager encodingManager;

    private final Map<String, RoutingProfile> routingProfiles = new HashMap<>();

    public RoutingProfileManager(Collection<Supplier<RoutingProfile>> profiles) {
        EncodingManager.Builder builder = new EncodingManager.Builder()
                .add(WayTypeEncoder.create())
                .add(Roundabout.create())
                .add(RoadClass.create())
                .add(RoadClassLink.create())
                .add(Smoothness.create())
                .add(FerrySpeed.create())
                .add(MaxSpeed.create());
        for (Supplier<RoutingProfile> profileSupplier : profiles) {
            final RoutingProfile profile = profileSupplier.get();

            routingProfiles.put(profile.getName(), profile);

            final VehicleEncoding encoding = profile.getVehicleEncoding();
            builder.add(encoding.access())
                    .add(encoding.speed())
                    .addTurnCostEncodedValue(encoding.turnRestriction())
                    .addTurnCostEncodedValue(encoding.turnCost())
                    .add(encoding.subnetwork());
            if (encoding.priority() != null) {
                builder.add(encoding.priority());
            }
        }
        this.encodingManager = builder.build();
    }

    public Collection<RoutingProfile> getAllProfiles() {
        return Collections.unmodifiableCollection(routingProfiles.values());
    }

    /**
     * Returns the specific wrapper of {@link com.graphhopper.routing.ev.EncodedValue}s required
     * for the given transportation mode (e.g. "car", "bike").
     */
    public RoutingProfile getRoutingProfile(String vehicle) {
        return routingProfiles.get(vehicle);
    }

    /**
     * Returns the actual encoding manager used in GraphHopper.
     */
    public EncodingManager getEncodingManager() {
        return encodingManager;
    }
}
