/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
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
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.objects.taxi.TaxiVehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This application shows how to interact with SUMO through the interface provided by MOSAIC which allows sending
 * messages to TraCI and reacting on received TraCI response.
 */
public class SumoTaxiTraciInteractionApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication {

	private long sendTimes = 3; // Contact sumo 3 times

	@Override
	public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
		if(--sendTimes < 0) { // Contacted sumo 3 times already
			return;
		}
		Object additionalData = updatedVehicleData.getAdditionalData();

		if(additionalData instanceof TaxiVehicleData taxiVehicleData) {
			getLog().info("Taxi {} state: {}", getOs().getId(), taxiVehicleData.getState());
			getLog().info("Customers: {}", String.join(", ", taxiVehicleData.getCustomersToPickUpOrOnBoard()));
			getLog().info("Occupied distance: {}m", taxiVehicleData.getTotalOccupiedDistanceInMeters());
			getLog().info("Occupied time: {}s", taxiVehicleData.getTotalOccupiedTimeInSeconds());
		}
	}

	@Override
	public void onStartup() {
		getLog().infoSimTime(this, "Startup");
	}

	@Override
	public void onShutdown() {
		getLog().infoSimTime(this, "Shutdown");
	}

	@Override
	public void processEvent(Event event) throws Exception {

	}
}
