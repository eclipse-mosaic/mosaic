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

import org.eclipse.mosaic.app.taxi.util.DatabaseCommunication;
import org.eclipse.mosaic.app.taxi.util.TaxiDispatchData;
import org.eclipse.mosaic.app.taxi.util.TaxiLatestData;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.TaxiServerApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.application.TaxiDispatch;
import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;
import org.eclipse.mosaic.lib.objects.taxi.TaxiVehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import java.util.HashMap;
import java.util.List;

import static org.eclipse.mosaic.app.taxi.util.Constants.*;
import static org.eclipse.mosaic.app.taxi.util.ExternalFilesUtil.*;
import static org.eclipse.mosaic.app.taxi.util.ParserUtil.parseMosaicVehicleIdToTaxiDbIndex;

public class TaxiDispatchingServer extends AbstractApplication<ServerOperatingSystem> implements TaxiServerApplication {
    // ================== DEFINE YOUR CUSTOM SCENARIO PARAMETERS ==================
    // SCENARIO
    private static final String SCENARIO_NAME = "naunhof";
    private static final String PATH_TO_DISPATCHER_WSL = "/PUT/YOUR/PATH/HERE";
	private static final String PATH_TO_DISPATCHER_WINDOWS = "/PUT/YOUR/PATH/HERE";

    // DISPATCHER CONFIGS
    public static final int TAXI_ORDER_MAX_DETOUR_IN_PERCENTAGE = 70;
    public static final int TAXI_ORDER_MAX_WAIT_IN_MINUTES = 5;

    // FLAGS
	private static final boolean EXECUTE_PYTHON_SCRIPTS_AND_TERMINATE = false;
    private static final boolean CREATE_DISTANCES_FILE_AND_TERMINATE = false;
    private static final boolean INCLUDE_PYTHON_SCRIPT_LOGS = false;
	private static final boolean START_DISPATCHER_INSIDE_WINDOWS_TERMINAL = false;
    // ================== END OF CUSTOM SCENARIO PARAMETERS ==================

    // TABLES
    private static final List<String> NOT_EMPTY_TABLES = List.of("customer", "stop", "cab");
    private static final List<String> EMPTY_TABLES = List.of("taxi_order", "leg", "route", "freetaxi_order");

    // GLOBAL VARIABLES
    private final HashMap<String, TaxiLatestData> cabsLatestData = new HashMap<>();
	private final HashMap<String, String> pendingDispatches = new HashMap<>();
    private int lastRegisteredTaxiDbIndex = 0;
    private int lastSavedReservationMosaicIndex = -1;
    private DatabaseCommunication dataBaseCommunication;

    @Override
    public void onStartup() {
		if (EXECUTE_PYTHON_SCRIPTS_AND_TERMINATE) {
        	executePythonScripts(getLog(), INCLUDE_PYTHON_SCRIPT_LOGS, SCENARIO_NAME);
			System.exit(0);
		}

        dataBaseCommunication = new DatabaseCommunication(getLog(), getOs().getRoutingModule());
        dataBaseCommunication.checkTablesState(NOT_EMPTY_TABLES, false);
        dataBaseCommunication.checkTablesState(EMPTY_TABLES, true);

		if (CREATE_DISTANCES_FILE_AND_TERMINATE) {
			createFileWithDistanceInMinutesBetweenStops(PATH_TO_DISPATCHER_WINDOWS, dataBaseCommunication.fetchAllBusStops(), getOs().getRoutingModule());
			System.exit(0);
		}

		// In the Windows terminal using the 'wsl' command it is much slower than
		// starting it manually under WSL, does not work optimal
		if (START_DISPATCHER_INSIDE_WINDOWS_TERMINAL) {
			startDispatcher(getLog(), SCENARIO_NAME, PATH_TO_DISPATCHER_WSL);
		}
    }

    @Override
    public void onShutdown() {
        dataBaseCommunication.closeDbConnection();
    }

    @Override
    public void onTaxiDataUpdate(List<TaxiVehicleData> taxis, List<TaxiReservation> taxiReservations) {

		for (TaxiVehicleData taxi : taxis) {

			// make sure that SUMO has executed the dispatch command
			if (pendingDispatches.containsKey(taxi.getId())) {
				if (taxi.getState() == TaxiVehicleData.EMPTY_TO_PICK_UP_TAXIS ||
					taxi.getState() == TaxiVehicleData.OCCUPIED_TAXIS) {
					getLog().info("Dispatch confirmed for vehicle '{}'", taxi.getId());
					pendingDispatches.remove(taxi.getId());
				} else {
					getLog().warn("Still no dispatch for vehicle '{}'", taxi.getId());
				}
			}

			switch (taxi.getState()) {
				case TaxiVehicleData.EMPTY_TAXIS -> {
					handleNotRegisteredTaxiInDb(taxi);
					handleAlreadyDeliveredTaxi(taxi);
				}
				case TaxiVehicleData.EMPTY_TO_PICK_UP_TAXIS -> handleEmptyToPickUpTaxi(taxi);
				case TaxiVehicleData.OCCUPIED_TAXIS -> handleOccupiedTaxi(taxi);
				case TaxiVehicleData.OCCUPIED_TO_PICK_UP_TAXIS -> handleOccupiedToPickUpTaxi(taxi);
			}
		}

        // select all unassigned reservations
        List<TaxiReservation> unassignedReservations = taxiReservations.stream()
            .filter(taxiRes -> taxiRes.getReservationState() == TaxiReservation.ONLY_NEW_RESERVATIONS ||
                taxiRes.getReservationState() == TaxiReservation.ALREADY_RETRIEVED_RESERVATIONS)
            .filter(taxiRes -> Integer.parseInt(taxiRes.getId()) > lastSavedReservationMosaicIndex)
            .toList();

        if (!unassignedReservations.isEmpty()) {
            lastSavedReservationMosaicIndex += dataBaseCommunication.insertNewReservationsInDb(unassignedReservations, getSimulationTimeInSeconds());
        }

        List<TaxiDispatchData> taxiDispatchDataList = dataBaseCommunication.fetchTaxiDispatchDataAndUpdateLatestData(cabsLatestData);

        if (!taxiDispatchDataList.isEmpty()) {
            for (TaxiDispatchData taxiDispatchData : taxiDispatchDataList) {
				getLog().info("Sending dispatch vehicle: '{}', reservations: [{}]", taxiDispatchData.taxiId(), String.join(",", taxiDispatchData.reservationIds()));
				System.out.printf("Taxi ID: %s, Reservations: %s%n",
					taxiDispatchData.taxiId(), String.join(",", taxiDispatchData.reservationIds()));
                getOs().sendInteractionToRti(
                    new TaxiDispatch(getOs().getSimulationTime(), taxiDispatchData.taxiId(), taxiDispatchData.reservationIds())
                );
				pendingDispatches.put(taxiDispatchData.taxiId(), "PENDING");
            }
        }
    }

    @Override
    public void processEvent(Event event) throws Exception {

    }

	private void handleNotRegisteredTaxiInDb(TaxiVehicleData taxi) {
		int taxiDbIndex = parseMosaicVehicleIdToTaxiDbIndex(taxi.getId());

		if (taxiDbIndex <= lastRegisteredTaxiDbIndex) {
			return;
		}

		dataBaseCommunication.setTaxiFreeStatusInDbById(taxiDbIndex);
		lastRegisteredTaxiDbIndex++;

		if (taxi.getVehicleData() == null) {
			throw new RuntimeException("On registering: Taxi with Mosaic Id %s not spawned on a free position! Vehicle data is null!".formatted(taxi.getId()));
		}
		cabsLatestData.put(taxi.getId(),
			new TaxiLatestData(TaxiVehicleData.EMPTY_TAXIS, null, null, null));
	}

	private void handleAlreadyDeliveredTaxi(TaxiVehicleData taxi) {
		if (Integer.parseInt(taxi.getNumberOfCustomersServed()) == 0) {
			return;
		}

		TaxiLatestData latestData = cabsLatestData.get(taxi.getId());

		if (latestData.getLastStatus() == TaxiVehicleData.EMPTY_TAXIS) {
			return;
		}

		// delivered final customer
		// update cab's last location, route
		dataBaseCommunication.markLegAsCompleted(latestData.getCurrentLegId(), getSimulationTimeInSeconds());
		dataBaseCommunication.updateRouteStatusByLegId(latestData.getCurrentLegId(), DISPATCHER_COMPLETED_ROUTE_LEG_STATUS);
		dataBaseCommunication.markOrderAsCompletedForLegIdIfFinal(latestData.getCurrentLegId(), getSimulationTimeInSeconds());
		latestData.setLastStatus(TaxiVehicleData.EMPTY_TAXIS);
		latestData.getEdgesToVisit().clear();
		latestData.setCurrentLegId(null);
		latestData.getNextLegIds().clear();

		dataBaseCommunication.setTaxiFreeStatusInDbById(parseMosaicVehicleIdToTaxiDbIndex(taxi.getId()));
	}

	private void handleEmptyToPickUpTaxi(TaxiVehicleData taxi) {
		TaxiLatestData latestData = cabsLatestData.get(taxi.getId());

		// 1. case: taxi has already entered this method and its new data is set
		// 2. case: taxi is en route and was occupied, but there is a short transition between
		// the drop-off of the previous customers and the pick-up of the new ones
		if (latestData.getLastStatus() == TaxiVehicleData.EMPTY_TO_PICK_UP_TAXIS
			|| latestData.getLastStatus() == TaxiVehicleData.OCCUPIED_TO_PICK_UP_TAXIS) {
			return;
		}

		if (latestData.getLastStatus() != TaxiVehicleData.EMPTY_TAXIS) {
			throw new RuntimeException("Expected status EMPTY, but got: %s for vehicle: %s".formatted(latestData.getLastStatus(), taxi.getId()));
		}

		latestData.setLastStatus(TaxiVehicleData.EMPTY_TO_PICK_UP_TAXIS);

		//mark leg and route as started
		Integer currentLeg = latestData.getNextLegIds().remove(0);
		latestData.setCurrentLegId(currentLeg);

		dataBaseCommunication.markLegAsStarted(currentLeg, getSimulationTimeInSeconds());
		dataBaseCommunication.updateRouteStatusByLegId(currentLeg, DISPATCHER_STARTED_ROUTE_LEG_STATUS);
		dataBaseCommunication.markOrdersAsStartedByLegId(currentLeg, getSimulationTimeInSeconds());
	}

	private void handleOccupiedTaxi(TaxiVehicleData taxi) {
		TaxiLatestData latestData = cabsLatestData.get(taxi.getId());

		if (latestData.getLastStatus() != TaxiVehicleData.OCCUPIED_TAXIS) {
			latestData.setLastStatus(TaxiVehicleData.OCCUPIED_TAXIS);
		}

		// taxi already delivered last customer, but have to wait for TraCI to send it with EMPTY status
		if (latestData.getEdgesToVisit().isEmpty() && latestData.getNextLegIds().isEmpty()) {
			return;
		}

		if (latestData.getEdgesToVisit().get(0).equals(taxi.getVehicleData().getRoadPosition().getConnectionId())) {
			latestData.getEdgesToVisit().remove(0);
			Integer finishedLegId = latestData.getCurrentLegId();

			// set current stop as cab location
			dataBaseCommunication.updateCabLocation(taxi.getId(), finishedLegId);

			// if this was the last leg, it will be updated in the handleAlreadyDeliveredTaxi method
			if (latestData.getNextLegIds().isEmpty()) {
				return;
			}

			// mark legs as started/completed;
			// check if it is the final leg of an order and mark as completed as well
			dataBaseCommunication.markLegAsCompleted(finishedLegId, getSimulationTimeInSeconds());
			dataBaseCommunication.markOrderAsCompletedForLegIdIfFinal(finishedLegId, getSimulationTimeInSeconds());

			Integer currentLegId = latestData.getNextLegIds().remove(0);
			latestData.setCurrentLegId(currentLegId);
			dataBaseCommunication.markLegAsStarted(currentLegId, getSimulationTimeInSeconds());
		}
	}

	private void handleOccupiedToPickUpTaxi(TaxiVehicleData taxi) {
		TaxiLatestData latestData = cabsLatestData.get(taxi.getId());

		if (latestData.getLastStatus() != TaxiVehicleData.OCCUPIED_TO_PICK_UP_TAXIS) {
			latestData.setLastStatus(TaxiVehicleData.OCCUPIED_TO_PICK_UP_TAXIS);
		}

		if (latestData.getEdgesToVisit().get(0).equals(taxi.getVehicleData().getRoadPosition().getConnectionId())) {
			latestData.getEdgesToVisit().remove(0);
			Integer finishedLegId = latestData.getCurrentLegId();

			// set current stop as cab location and mark legs as started/completed;
			// check if it is the final leg of an order and mark as completed as well
			dataBaseCommunication.updateCabLocation(taxi.getId(), finishedLegId);
			dataBaseCommunication.markLegAsCompleted(finishedLegId, getSimulationTimeInSeconds());
			dataBaseCommunication.markOrderAsCompletedForLegIdIfFinal(finishedLegId, getSimulationTimeInSeconds());

			Integer currentLegId = latestData.getNextLegIds().remove(0);
			latestData.setCurrentLegId(currentLegId);
			dataBaseCommunication.markLegAsStarted(currentLegId, getSimulationTimeInSeconds());
		}
	}

	private long getSimulationTimeInSeconds() {
		return getOs().getSimulationTime() / TIME.SECOND;
	}
}
