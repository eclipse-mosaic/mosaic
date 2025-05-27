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
import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;
import org.eclipse.mosaic.lib.objects.taxi.TaxiVehicleData;

import java.util.List;

public interface TaxiServerApplication extends Application, OperatingSystemAccess<ServerOperatingSystem> {

    void onTaxiDataUpdate(List<TaxiVehicleData> taxis, List<TaxiReservation> taxiReservations);
}
