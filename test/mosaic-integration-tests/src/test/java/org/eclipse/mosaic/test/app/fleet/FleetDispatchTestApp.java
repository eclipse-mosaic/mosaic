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

package org.eclipse.mosaic.test.app.fleet;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.FleetServiceApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.traffic.FleetVehicleAssignment;
import org.eclipse.mosaic.lib.objects.fleet.RideReservation;
import org.eclipse.mosaic.lib.objects.fleet.FleetVehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.List;

public class FleetDispatchTestApp extends AbstractApplication<ServerOperatingSystem> implements FleetServiceApplication {

    @Override
    public void onServiceUpdates(List<FleetVehicleData> taxis, List<RideReservation> rideReservations) {
        for (RideReservation reservation : rideReservations) {
            if (reservation.getState() == RideReservation.State.NEW) {
                for (FleetVehicleData taxi : taxis) {
                    if (taxi.getState() == FleetVehicleData.State.EMPTY) {
                        getLog().info("Assigned reservation '{}' of person '{}' to vehicle '{}'.",
                                reservation.getId(), Iterables.getOnlyElement(reservation.getPersonList()), taxi.getId()
                        );
                        getOs().sendInteractionToRti(new FleetVehicleAssignment(getOs().getSimulationTime(),
                                taxi.getId(), Lists.newArrayList(reservation.getId())
                        ));
                    }
                }
            }
        }
    }

    @Override
    public void onStartup() {

    }

    @Override
    public void onShutdown() {

    }

    @Override
    public void processEvent(Event event) throws Exception {

    }
}
