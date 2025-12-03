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

package org.eclipse.mosaic.test.app.taxi;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.TaxiServerApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.application.TaxiDispatch;
import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;
import org.eclipse.mosaic.lib.objects.taxi.TaxiVehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.List;

public class TaxiDispatchTestApp extends AbstractApplication<ServerOperatingSystem> implements TaxiServerApplication {

    @Override
    public void onTaxiDataUpdate(List<TaxiVehicleData> taxis, List<TaxiReservation> taxiReservations) {
        for (TaxiReservation reservation : taxiReservations) {
            if (reservation.getReservationState() == TaxiReservation.ReservationState.NEW) {
                for (TaxiVehicleData taxi : taxis) {
                    if (taxi.getState() == TaxiVehicleData.TaxiState.EMPTY) {
                        getLog().info("Assigned reservation '{}' of person '{}' to vehicle '{}'.",
                                reservation.getId(), Iterables.getOnlyElement(reservation.getPersonList()), taxi.getId()
                        );
                        getOs().sendInteractionToRti(new TaxiDispatch(getOs().getSimulationTime(),
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
