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

package org.eclipse.mosaic.app.taxi;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.TaxiServerApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.application.TaxiDispatch;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;
import org.eclipse.mosaic.lib.objects.taxi.TaxiVehicleData;
import org.eclipse.mosaic.lib.routing.RoutingParameters;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.routing.RoutingResponse;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class ExampleTaxiDispatchingServer extends AbstractApplication<ServerOperatingSystem> implements TaxiServerApplication {

    @Override
    public void onStartup() {

    }

    @Override
    public void onShutdown() {

    }

    @Override
    public void onTaxiDataUpdate(List<TaxiVehicleData> taxis, List<TaxiReservation> taxiReservations) {

        // select all empty taxis
        List<String> emptyTaxis = new ArrayList<>();
        for (TaxiVehicleData taxi : taxis) {
            if (taxi.getState() == TaxiVehicleData.EMPTY_TAXIS) {
                emptyTaxis.add(taxi.getId());
            }
        }

        // select all unassigned reservations
        List<String> unassignedReservations = new ArrayList<>();
        for (TaxiReservation reservation : taxiReservations) {
            if (reservation.getReservationState() == TaxiReservation.ONLY_NEW_RESERVATIONS || reservation.getReservationState() == TaxiReservation.ALREADY_RETRIEVED_RESERVATIONS) {
                unassignedReservations.add(reservation.getId());
            }
        }

        for (String unassignedReservation:  unassignedReservations) {
            if (emptyTaxis.isEmpty()) {
                continue;
            }
            // for each unassigned reservation, just choose an empty taxi randomly
            String emptyTaxi = emptyTaxis.remove(getRandom().nextInt(emptyTaxis.size()));

            //  and send the dispatch command to SumoAmbassador via TaxiDispatch interaction
            getOs().sendInteractionToRti(
                    new TaxiDispatch(getOs().getSimulationTime(), emptyTaxi, Lists.newArrayList(unassignedReservation))
            );
        }

        /**
         * RoutingResponse response = getOs().getRoutingModule().calculateRoutes(
         *                 new RoutingPosition(GeoPoint.latLon(0, 0)),
         *                 new RoutingPosition(GeoPoint.latLon(0, 0)),
         *                 new RoutingParameters()
         *         );
         *         double length = response.getBestRoute().getLength();
         *         double time = response.getBestRoute().getTime();
         */


    }

    @Override
    public void processEvent(Event event) throws Exception {

    }
}
