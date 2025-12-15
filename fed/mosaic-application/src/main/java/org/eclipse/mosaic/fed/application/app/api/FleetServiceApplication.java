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

package org.eclipse.mosaic.fed.application.app.api;

import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.traffic.FleetVehicleAssignment;
import org.eclipse.mosaic.lib.objects.fleet.RideReservation;
import org.eclipse.mosaic.lib.objects.fleet.FleetVehicleData;

import java.util.List;

/**
 * Implement this interface as a server application, if it should be able to react on changes of vehicle fleets and reservations,
 * e.g. to model ride-pooling services. Currently, SUMO offers handling of taxi service, whereas the dispatching part can
 * be done by external algorithms (e.g., the application implementing this interface). By triggering a
 * {@link FleetVehicleAssignment} interaction, the reservations can be directly
 * assigned to vehicles.
 */
public interface FleetServiceApplication extends Application, OperatingSystemAccess<ServerOperatingSystem> {

    /**
     * Is called on each simulation step and informs about the current state of the vehicle fleet and
     * all ride reservations.
     */
    void onServiceUpdates(List<FleetVehicleData> taxis, List<RideReservation> rideReservations);
}
