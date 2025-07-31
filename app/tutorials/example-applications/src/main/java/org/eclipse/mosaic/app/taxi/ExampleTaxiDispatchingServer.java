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

import org.eclipse.mosaic.app.taxi.util.DataBaseCommunication;
import org.eclipse.mosaic.app.taxi.util.TaxiDispatchData;
import org.eclipse.mosaic.app.taxi.util.TaxiLatestData;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.TaxiServerApplication;
import org.eclipse.mosaic.fed.application.app.api.navigation.RoutingModule;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.application.TaxiDispatch;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;
import org.eclipse.mosaic.lib.objects.taxi.TaxiVehicleData;
import org.eclipse.mosaic.lib.routing.RoutingParameters;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.routing.RoutingResponse;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.List;

import static org.eclipse.mosaic.app.taxi.util.Constants.*;
import static org.eclipse.mosaic.app.taxi.util.ExternalFilesUtil.executePythonScripts;
import static org.eclipse.mosaic.app.taxi.util.ExternalFilesUtil.startDispatcher;
import static org.eclipse.mosaic.app.taxi.util.ParserUtil.parseMosaicVehicleIdToTaxiDbIndex;

public class ExampleTaxiDispatchingServer extends AbstractApplication<ServerOperatingSystem> implements TaxiServerApplication {
    // ================== DEFINE YOUR CUSTOM SCENARIO PARAMETERS ==================
    // SCENARIO
    private static final String SCENARIO_NAME = "theodorHeuss";
    private static final String PATH_TO_DISPATCHER = "/PUT/YOUR/PATH/HERE";

    // DISPATCHER CONFIGS
    public static final int ORDER_MAX_DETOUR_IN_PERCENTAGE_DISPATCHER_CONFIG = 70;
    public static final int ORDER_MAX_WAIT_IN_MINUTES_DISPATCHER_CONFIG = 10;

    // FLAGS
    private static final boolean CREATE_DISTANCES_FILE_FLAG = true;
    private static final boolean INCLUDE_PYTHON_SCRIPT_LOGS_FLAG = false;
    // ================== END OF CUSTOM SCENARIO PARAMETERS ==================

    // TABLES
    private static final List<String> NOT_EMPTY_TABLES = List.of("customer", "stop", "cab");
    private static final List<String> EMPTY_TABLES = List.of("taxi_order", "leg", "route", "freetaxi_order");

    // GLOBAL VARIABLES
    private static final HashMap<String, TaxiLatestData> cabsLatestData = new HashMap<>();
    private static int lastRegisteredTaxiDbIndex = 0;
    private static int lastSavedReservationMosaicIndex = -1;
    private static DataBaseCommunication dataBaseCommunication;

    @Override
    public void onStartup() {
        executePythonScripts(getLog(), INCLUDE_PYTHON_SCRIPT_LOGS_FLAG, SCENARIO_NAME);
        dataBaseCommunication = new DataBaseCommunication(getLog());
        dataBaseCommunication.checkTablesState(NOT_EMPTY_TABLES, false);
        dataBaseCommunication.checkTablesState(EMPTY_TABLES, true);
        if (CREATE_DISTANCES_FILE_FLAG) {
            createFileWithDistancesInMinutesBetweenStops();
        }
        startDispatcher(getLog(), SCENARIO_NAME, PATH_TO_DISPATCHER);
    }

    @Override
    public void onShutdown() {
        dataBaseCommunication.closeDbConnection();
    }

    @Override
    public void onTaxiDataUpdate(List<TaxiVehicleData> taxis, List<TaxiReservation> taxiReservations) {

        // select all empty taxis
        List<TaxiVehicleData> emptyTaxis = taxis.stream()
            .filter(taxi -> taxi.getState() == TaxiVehicleData.EMPTY_TAXIS)
            .toList();

        if (!emptyTaxis.isEmpty()) {
            checkForNotRegisteredTaxisInDb(emptyTaxis);
            checkAlreadyDeliveredTaxis(emptyTaxis);
        }

        checkEmptyToPickUpTaxis(taxis);
        checkOccupiedTaxis(taxis);

        // select all unassigned reservations
        List<TaxiReservation> unassignedReservations = taxiReservations.stream()
            .filter(taxiRes -> taxiRes.getReservationState() == TaxiReservation.ONLY_NEW_RESERVATIONS ||
                taxiRes.getReservationState() == TaxiReservation.ALREADY_RETRIEVED_RESERVATIONS)
            .filter(taxiRes -> Integer.parseInt(taxiRes.getId()) > lastSavedReservationMosaicIndex)
            .toList();

        if (!unassignedReservations.isEmpty()) {
            lastSavedReservationMosaicIndex += dataBaseCommunication.insertNewReservationsInDb(unassignedReservations, getOs().getRoutingModule());
        }

        List<TaxiDispatchData> taxiDispatchDataList = dataBaseCommunication.fetchAvailableTaxiDispatchData(cabsLatestData);

        if (!taxiDispatchDataList.isEmpty()) {
            for (TaxiDispatchData taxiDispatchData : taxiDispatchDataList) {
                getOs().sendInteractionToRti(
                    new TaxiDispatch(getOs().getSimulationTime(), taxiDispatchData.taxiId(), taxiDispatchData.reservationIds())
                );
            }
        }
    }

    @Override
    public void processEvent(Event event) throws Exception {

    }

    private void checkAlreadyDeliveredTaxis(List<TaxiVehicleData> emptyTaxis) {
        List<TaxiVehicleData> alreadyDeliveredTaxis = emptyTaxis.stream()
            .filter(taxi -> Integer.parseInt(taxi.getNumberOfCustomersServed()) > 0)
            .toList();

        if (alreadyDeliveredTaxis.isEmpty()) {
            return;
        }

        for (TaxiVehicleData taxi : alreadyDeliveredTaxis) {
            TaxiLatestData latestData = cabsLatestData.get(taxi.getId());

            if (latestData.getLastStatus() == TaxiVehicleData.OCCUPIED_TAXIS) {
                latestData.setLastStatus(TaxiVehicleData.OCCUPIED_TAXIS);
                latestData.getEdgesToVisit().clear();
                latestData.setCurrentLegId(null);
                latestData.getNextLegIds().clear();
            }
        }

        List<Integer> alreadyDeliveredIds = alreadyDeliveredTaxis.stream()
            .map(taxiVehicleData -> parseMosaicVehicleIdToTaxiDbIndex(taxiVehicleData.getId()))
            .toList();
        dataBaseCommunication.setTaxiFreeStatusInDbByIds(alreadyDeliveredIds);
    }

    private void checkOccupiedTaxis(List<TaxiVehicleData> taxis) {
        List<TaxiVehicleData> occupiedTaxis = taxis.stream()
            .filter(taxi -> taxi.getState() == TaxiVehicleData.OCCUPIED_TAXIS)
            .toList();

        if (occupiedTaxis.isEmpty()) {
            return;
        }

        for (TaxiVehicleData taxi : occupiedTaxis) {
            TaxiLatestData latestData = cabsLatestData.get(taxi.getId());

            if (latestData.getEdgesToVisit().isEmpty() && latestData.getNextLegIds().isEmpty()) {
                continue; //taxi already delivered last customer, but have to wait for TraCI to send it with EMPTY status
            }

            if (latestData.getEdgesToVisit().get(0).equals(taxi.getVehicleData().getRoadPosition().getConnectionId())) {
                latestData.getEdgesToVisit().remove(0);
                Integer finishedLegId = latestData.getCurrentLegId();

                //set current stop as cab location and mark legs as started/completed
                dataBaseCommunication.updateCabLocation(taxi.getId(), finishedLegId);
                dataBaseCommunication.markLegAsCompleted(finishedLegId);

                Integer currentLegId = null;
                if (latestData.getNextLegIds().isEmpty()) {
                    //delivered final customer
                    //update cab's last location, route
                    dataBaseCommunication.updateRouteStatusByLegId(finishedLegId, DISPATCHER_COMPLETED_ROUTE_LEG_STATUS);
                    dataBaseCommunication.updateOrdersByLegId(finishedLegId, DISPATCHER_COMPLETED_ORDER_STATUS, false);
                } else {
                    currentLegId = latestData.getNextLegIds().remove(0);
                    dataBaseCommunication.markLegAsStarted(currentLegId);
                }

                latestData.setLastStatus(TaxiVehicleData.OCCUPIED_TAXIS);
                latestData.setCurrentLegId(currentLegId);
            }
        }
    }

    private void checkEmptyToPickUpTaxis(List<TaxiVehicleData> taxis) {
        List<TaxiVehicleData> emptyToPickupTaxis = taxis.stream()
            .filter(taxi -> taxi.getState() == TaxiVehicleData.EMPTY_TO_PICK_UP_TAXIS)
            .toList();

        if (emptyToPickupTaxis.isEmpty()) {
            return;
        }

        for (TaxiVehicleData taxi : emptyToPickupTaxis) {
            TaxiLatestData latestData = cabsLatestData.get(taxi.getId());
            if (latestData.getLastStatus() != TaxiVehicleData.EMPTY_TAXIS) {
                continue;
            }

            //mark leg and route as started
            Integer currentLeg = latestData.getNextLegIds().remove(0);
            dataBaseCommunication.markLegAsStarted(currentLeg);
            dataBaseCommunication.updateRouteStatusByLegId(currentLeg, DISPATCHER_STARTED_ROUTE_LEG_STATUS);
            dataBaseCommunication.updateOrdersByLegId(currentLeg, DISPATCHER_PICKEDUP_ORDER_STATUS, true);

            latestData.setLastStatus(TaxiVehicleData.EMPTY_TO_PICK_UP_TAXIS);
            latestData.setCurrentLegId(currentLeg);
        }
    }

    private void checkForNotRegisteredTaxisInDb(List<TaxiVehicleData> emptyTaxis) {
        List<TaxiVehicleData> taxisToRegister = emptyTaxis.stream()
            .filter(taxiVehicleData -> parseMosaicVehicleIdToTaxiDbIndex(taxiVehicleData.getId()) > lastRegisteredTaxiDbIndex)
            .toList();

        if (taxisToRegister.isEmpty()) {
            return;
        }

        List<Integer> idsToRegister = emptyTaxis.stream()
            .map(taxiVehicleData -> parseMosaicVehicleIdToTaxiDbIndex(taxiVehicleData.getId()))
            .toList();

        dataBaseCommunication.setTaxiFreeStatusInDbByIds(idsToRegister);
        lastRegisteredTaxiDbIndex += idsToRegister.size();

		for (TaxiVehicleData taxiVehicleData : taxisToRegister) {
            if (taxiVehicleData.getVehicleData() == null) {
                throw new RuntimeException("On registering: Taxi with Veh Id %s not spawned on a free position! Vehicle data is null!".formatted(taxiVehicleData.getId()));
            }

			cabsLatestData.put(taxiVehicleData.getId(),
				new TaxiLatestData(TaxiVehicleData.EMPTY_TAXIS, null, null, null));
		}
    }

    // TODO check if this distance is good enough for the dispatcher
    public static int calculateDistanceInMinutesBetweenTwoStops(String fromStopEdge, String toStopEdge, RoutingModule routingModule) {
        GeoPoint startPoint = routingModule
            .getConnection(fromStopEdge)
            .getStartNode()
            .getPosition();
        GeoPoint finalPoint = routingModule
            .getConnection(toStopEdge)
            .getEndNode()
            .getPosition();

        RoutingResponse response = routingModule.calculateRoutes(
            new RoutingPosition(startPoint),
            new RoutingPosition(finalPoint),
            new RoutingParameters());
        double timeInMinutes = Math.ceil(response.getBestRoute().getTime() / 60);

        return (int) timeInMinutes;
    }

    private void createFileWithDistancesInMinutesBetweenStops() {
        String filePath = "C:\\Users\\Kotse\\VSCodeProjects\\kern_Github";
        File file = new File(filePath + FileSystems.getDefault().getSeparator() + "distances.txt");

		List<String> edges = dataBaseCommunication.fetchAllBusStopEdgeIds();
		long start = System.currentTimeMillis();
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(file, false));
            //first element is total count of bus stops + 1 because of Kern
			writer.append(String.valueOf(edges.size() + 1)).append("\n");

            // Kern takes also stops with id=0, so we should fill the matrix here with an irrelevant value
			for (int i = 0; i < edges.size() + 1; i++) {
				for (int j = 0; j < edges.size() + 1; j++) {
                    if (i == 0 || j == 0) {
                        writer.append("10000");
                    } else if (i == j) {
						writer.append("0");
					} else {
						writer.append(
							String.valueOf(calculateDistanceInMinutesBetweenTwoStops(edges.get(i-1), edges.get(j-1), getOs().getRoutingModule())));
					}

					if (j == edges.size()) {
						writer.append("\n");
                        continue;
					}

                    writer.append(",");
				}
			}
			writer.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		long finish = System.currentTimeMillis();
		System.out.printf("Time elapsed while creating distances file: %s ms%n", finish - start);
	}
}
