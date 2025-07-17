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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.sql.*;
import java.util.*;

public class ExampleTaxiDispatchingServer extends AbstractApplication<ServerOperatingSystem> implements TaxiServerApplication {

    private static final int DISPATCHER_MAX_DETOUR_CONFIG = 70;
    private static final int DISPATCHER_MAX_WAIT_CONFIG = 15;
    private static final int DISPATCHER_ASSIGNED_TAXI_STATUS = 0;
    private static final int DISPATCHER_FREE_TAXI_STATUS = 1;
    private static final int DISPATCHER_RECEIVED_ORDER_STATUS = 0;
    private static final int DISPATCHER_ASSIGNED_ORDER_STATUS = 1;
    private static final int DISPATCHER_ACCEPTED_ORDER_STATUS = 2;
    private static final int DISPATCHER_PICKEDUP_ORDER_STATUS = 7;
    private static final int DISPATCHER_COMPLETED_ORDER_STATUS = 8;
    private static final int DISPATCHER_ASSIGNED_ROUTE_LEG_STATUS = 1;
    private static final int DISPATCHER_STARTED_ROUTE_LEG_STATUS = 5;
    private static final int DISPATCHER_COMPLETED_ROUTE_LEG_STATUS = 6;
    private static final String VEHICLE_MOSAIC_ID_PREFIX = "veh_";
    private static final int VEHICLE_ID_PREFIX_LENGTH = VEHICLE_MOSAIC_ID_PREFIX.length();
    private static final String ID_COLUMN_NAME = "id";
    private static final String COMMA_DELIMITER = ",";
    private static final HashMap<String, TaxiLatestData> cabsLatestData = new HashMap<>();
    private static int lastRegisteredTaxiDbIndex = 0;
    private static int lastSavedReservationMosaicIndex = -1;
    private static Connection dbConnection;

    @Override
    public void onStartup() {
        connectToDatabase();
        checkTablesState(List.of("customer", "stop", "cab"), false);
        checkTablesState(List.of("taxi_order", "leg", "route", "freetaxi_order"), true);
        createFileWithDistancesInMinutesBetweenStops();
    }

    @Override
    public void onShutdown() {
        closeDbConnection();
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
            insertNewReservationsInDb(unassignedReservations);
        }

        List<TaxiDispatchData> taxiDispatchDataList = fetchAvailableTaxiDispatchData();

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

    private void setTaxiFreeStatusInDbByIds(List<Integer> taxisToUpdateIds) {
        List<String> idsToString = taxisToUpdateIds.stream()
            .map(Objects::toString)
            .toList();

        try {
            PreparedStatement updateTaxiVehicles = dbConnection.prepareStatement(
                "UPDATE cab SET status = ? WHERE id IN (%s)".formatted(String.join(COMMA_DELIMITER, idsToString)));

            updateTaxiVehicles.setInt(1, ExampleTaxiDispatchingServer.DISPATCHER_FREE_TAXI_STATUS);

            if (updateTaxiVehicles.executeUpdate() != taxisToUpdateIds.size()) {
                throw new RuntimeException("Not all statuses were updated!");
            }
            updateTaxiVehicles.close();
        } catch(SQLException e) {
            getLog().error("Error updating taxi status", e);
        }
    }

    private List<TaxiDispatchData> fetchAvailableTaxiDispatchData() {
        try {
            PreparedStatement fetchOrders = dbConnection.prepareStatement(
                "SELECT taxi_order.id, taxi_order.sumo_id, taxi_order.cab_id " +
                    "FROM taxi_order JOIN route ON taxi_order.route_id = route.id JOIN leg ON route.id = leg.route_id " +
                    "WHERE taxi_order.status = ? ORDER BY route.id, leg.id");
            fetchOrders.setInt(1, DISPATCHER_ASSIGNED_ORDER_STATUS);
            ResultSet fetchedOrders = fetchOrders.executeQuery();

            if (!fetchedOrders.next()) {
                return List.of();
            }

            List<TaxiDispatchData> taxiDispatchDataList = new ArrayList<>();
            List<String> assignedOrderIds = new ArrayList<>(Collections.singleton(fetchedOrders.getString(ID_COLUMN_NAME)));
            List<String> currentTaxiOrders = new ArrayList<>(Collections.singleton(fetchedOrders.getString("sumo_id")));
            long currentCabId = fetchedOrders.getLong("cab_id");

            while (fetchedOrders.next()) {
                if (currentCabId != fetchedOrders.getLong("cab_id")) {
                    taxiDispatchDataList.add(
                        new TaxiDispatchData(parseTaxiDbIndexToMosaicVehicleId(currentCabId), currentTaxiOrders)
                    );

                    currentTaxiOrders = new ArrayList<>();
                    currentCabId = fetchedOrders.getLong("cab_id");
				    assignedOrderIds.add(fetchedOrders.getString(ID_COLUMN_NAME));
                }

                currentTaxiOrders.add(fetchedOrders.getString("sumo_id"));
			}

            taxiDispatchDataList.add(
                new TaxiDispatchData(parseTaxiDbIndexToMosaicVehicleId(currentCabId), currentTaxiOrders)
            );

            fetchOrders.close();
            fetchOrders.close();

			if (assignedOrderIds.isEmpty() || assignedOrderIds.size() != taxiDispatchDataList.size()) {
                throw new RuntimeException("Dispatch data is not set correctly!");
			}

            fetchAvailableRoutesAndUpdateCabLatestData(assignedOrderIds);
            markOrdersAsAccepted(assignedOrderIds);

			return taxiDispatchDataList;
        } catch(SQLException e) {
            getLog().error("Error while fetching available orders", e);
        }

        return List.of();
    }

    private void fetchAvailableRoutesAndUpdateCabLatestData(List<String> orderIds) {
        try {
            PreparedStatement fetchRoutes = dbConnection.prepareStatement(
                "SELECT taxi_order.id, taxi_order.cab_id, leg.id, leg.from_stand, leg.to_stand " +
                    "FROM taxi_order JOIN route ON taxi_order.route_id = route.id JOIN leg ON route.id = leg.route_id " +
                    "WHERE taxi_order.id IN (%s) ORDER BY route.id, leg.id".formatted(String.join(COMMA_DELIMITER, orderIds))
            );

            ResultSet fetchedRoutes = fetchRoutes.executeQuery();
            if (!fetchedRoutes.next()) {
                return;
            }

            long currentCabId =  fetchedRoutes.getLong("cab_id");
            ArrayList<Integer> legsToVisit = new ArrayList<>(Collections.singleton(fetchedRoutes.getInt("leg.id")));
            ArrayList<String> busStopIds = new ArrayList<>(Collections.singleton(fetchedRoutes.getString("to_stand")));

            while (fetchedRoutes.next()) {
                if (currentCabId != fetchedRoutes.getLong("cab_id")) {
                    cabsLatestData.put(parseTaxiDbIndexToMosaicVehicleId(currentCabId),
						new TaxiLatestData(TaxiVehicleData.EMPTY_TAXIS, fetchBusStopEdgesByIds(busStopIds), null,
							legsToVisit));

                    //reset variables and save the new values there
                    currentCabId = fetchedRoutes.getLong("cab_id");
                    busStopIds = new ArrayList<>();
                    legsToVisit = new ArrayList<>();
                }

                busStopIds.add(fetchedRoutes.getString("to_stand"));
                legsToVisit.add(fetchedRoutes.getInt("leg.id"));
            }

            cabsLatestData.put(parseTaxiDbIndexToMosaicVehicleId(currentCabId),
				new TaxiLatestData(TaxiVehicleData.EMPTY_TAXIS, fetchBusStopEdgesByIds(busStopIds), null, legsToVisit));

            fetchedRoutes.close();
            fetchRoutes.close();
        } catch(SQLException e) {
            getLog().error("Error while fetching available routes", e);
        }
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
        setTaxiFreeStatusInDbByIds(alreadyDeliveredIds);
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
                updateCabLocation(taxi.getId(), finishedLegId);
                markLegAsCompleted(finishedLegId);

                Integer currentLegId = null;
                if (latestData.getNextLegIds().isEmpty()) {
                    //delivered final customer
                    //update cab's last location, route
                    updateRouteStatusByLegId(finishedLegId, DISPATCHER_COMPLETED_ROUTE_LEG_STATUS);
                    updateOrdersByLegId(finishedLegId, DISPATCHER_COMPLETED_ORDER_STATUS, false);
                } else {
                    currentLegId = latestData.getNextLegIds().remove(0);
                    markLegAsStarted(currentLegId);
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
            markLegAsStarted(currentLeg);
            updateRouteStatusByLegId(currentLeg, DISPATCHER_STARTED_ROUTE_LEG_STATUS);
            updateOrdersByLegId(currentLeg, DISPATCHER_PICKEDUP_ORDER_STATUS, true);

            latestData.setLastStatus(TaxiVehicleData.EMPTY_TO_PICK_UP_TAXIS);
            latestData.setCurrentLegId(currentLeg);
        }
    }

    // DONE
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

        setTaxiFreeStatusInDbByIds(idsToRegister);
        lastRegisteredTaxiDbIndex += idsToRegister.size();

		for (TaxiVehicleData taxiVehicleData : taxisToRegister) {
            if (taxiVehicleData.getVehicleData() == null) {
                throw new RuntimeException("On registering: Taxi with Veh Id %s not spawned on a free position! Vehicle data is null!".formatted(taxiVehicleData.getId()));
            }

			cabsLatestData.put(taxiVehicleData.getId(),
				new TaxiLatestData(TaxiVehicleData.EMPTY_TAXIS, null, null, null));
		}
    }

    private String parseTaxiDbIndexToMosaicVehicleId(long taxiDbId) {
        taxiDbId -= 1;
        return VEHICLE_MOSAIC_ID_PREFIX + taxiDbId;
    }

    private Integer parseMosaicVehicleIdToTaxiDbIndex(String mosaicVehicleId) {
        mosaicVehicleId = mosaicVehicleId.substring(VEHICLE_ID_PREFIX_LENGTH);
        return Integer.parseInt(mosaicVehicleId) + 1; //db indices start from 1 not from 0 like in Mosaic
    }

    private void insertNewReservationsInDb(List<TaxiReservation> newTaxiReservations) {
        try {
            PreparedStatement insertReservations = dbConnection.prepareStatement(
                "INSERT INTO taxi_order (from_stand, to_stand, max_loss, max_wait, shared, " +
                "status, received, distance, customer_id, sumo_id) VALUES (?,?,?,?,true,?,?,?,?,?)");

            for (TaxiReservation reservation: newTaxiReservations) {
                List<Integer> busStopsIndices = fetchBusStopsIndicesByEdge(reservation.getFromEdge(), reservation.getToEdge());
                if (busStopsIndices.size() != 2) {
                    throw new RuntimeException("Number of fetched bus stops is not 2!");
                }

                insertReservations.setInt(1, busStopsIndices.get(0));
                insertReservations.setInt(2, busStopsIndices.get(1));
                insertReservations.setInt(3, DISPATCHER_MAX_DETOUR_CONFIG);
                insertReservations.setInt(4, DISPATCHER_MAX_WAIT_CONFIG);
                insertReservations.setInt(5, DISPATCHER_RECEIVED_ORDER_STATUS);
                insertReservations.setTimestamp(6, new Timestamp(System.currentTimeMillis())); // TODO check if there can't be a better method
                insertReservations.setInt(7,
                    calculateDistanceInMinutesBetweenTwoStops(reservation.getFromEdge(), reservation.getToEdge()));
                insertReservations.setLong(8, parsePerson(reservation.getPersonList()));
                insertReservations.setLong(9, Long.parseLong(reservation.getId()));
                insertReservations.addBatch();
                insertReservations.clearParameters();
            }

            int[] insertedReservationsResults = insertReservations.executeBatch();
            for (int batchCommandResult : insertedReservationsResults) {
                if (batchCommandResult < 0) {
                    throw new RuntimeException("Some reservations were not inserted correctly!");
                }
            }
            insertReservations.close();
            lastSavedReservationMosaicIndex +=  newTaxiReservations.size();
        } catch (SQLException e) {
            getLog().error("Could not execute insert reservations query", e);
        }
    }

    private void markOrdersAsAccepted(List<String> orderIds) {
        try {
            PreparedStatement updateOrders = dbConnection.prepareStatement(
                "UPDATE taxi_order SET status = ? WHERE id IN (%s)".formatted(String.join(COMMA_DELIMITER, orderIds))
            );
            updateOrders.setInt(1, ExampleTaxiDispatchingServer.DISPATCHER_ACCEPTED_ORDER_STATUS);

            if (updateOrders.executeUpdate() != orderIds.size()) {
                throw new RuntimeException("Not all order statuses were set to 'ACCEPTED'!");
            }
            updateOrders.close();
        } catch(SQLException e) {
            getLog().error("Error while setting a new status to the orders", e);
        }
    }

    private void updateOrdersByLegId(long legId, int status, boolean isStarted) {
        try {
            String dbField = isStarted ? "started" : "completed";
            PreparedStatement updateOrders = dbConnection.prepareStatement(
                "UPDATE taxi_order SET %s = ?, status = ? WHERE route_id = ".formatted(dbField) +
                    "(SELECT route.id FROM route WHERE route.id = " +
                    "(SELECT route_id FROM leg WHERE leg.id = ?))"
            );

            updateOrders.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            updateOrders.setInt(2, status);
            updateOrders.setLong(3, legId);

            if (updateOrders.executeUpdate() < 1) {
                throw new RuntimeException("Orders were not updated correctly using the leg ID!");
            }
            updateOrders.close();
        } catch(SQLException e) {
            getLog().error("Error while updating orders by leg ID", e);
        }
    }

    private void updateCabLocation(String mosaicVehicleId, Integer finishedLegId) {
        try {
            PreparedStatement updateCabLocation = dbConnection.prepareStatement(
                "UPDATE cab SET location = (SELECT leg.to_stand FROM leg WHERE leg.id = ?) WHERE cab.id = ?"
            );

            updateCabLocation.setLong(1, finishedLegId);
            updateCabLocation.setLong(2, parseMosaicVehicleIdToTaxiDbIndex(mosaicVehicleId));

            if (updateCabLocation.executeUpdate() != 1) {
                throw new RuntimeException("Cab's location was not updated correctly");
            }
            updateCabLocation.close();
        } catch(SQLException e) {
            getLog().error("Could not update cab location", e);
        }
    }

    private void updateRouteStatusByLegId(Integer legId, int status) {
        try {
            PreparedStatement updateRoute = dbConnection.prepareStatement(
                "UPDATE route SET status = ? WHERE route.id = (SELECT route_id FROM leg WHERE leg.id = ?)"
            );

            updateRoute.setInt(1, status);
            updateRoute.setLong(2, legId);

            if (updateRoute.executeUpdate() != 1) {
                throw new RuntimeException("Route's status was not updated correctly!");
            }
            updateRoute.close();
        } catch(SQLException e) {
            getLog().error("Could not update route's status", e);
        }
    }

    private void markLegAsStarted(Integer legId) {
        try {
            PreparedStatement updateLeg = dbConnection.prepareStatement(
                "UPDATE leg SET started = ?, status = ? WHERE id = ?"
            );

            updateLeg.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            updateLeg.setInt(2, DISPATCHER_STARTED_ROUTE_LEG_STATUS);
            updateLeg.setLong(3, legId);

            if (updateLeg.executeUpdate() != 1) {
                throw new RuntimeException("Leg was not correctly marked as started!");
            }
            updateLeg.close();
        } catch(SQLException e) {
            getLog().error("Could not mark leg as started", e);
        }
    }

    private void markLegAsCompleted(Integer legId) {
        try {
            PreparedStatement updateLeg = dbConnection.prepareStatement(
                "UPDATE leg SET completed = ?, status = ? WHERE id = ?"
            );

            updateLeg.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            updateLeg.setInt(2, DISPATCHER_COMPLETED_ROUTE_LEG_STATUS);
            updateLeg.setLong(3, legId);

            if (updateLeg.executeUpdate() != 1) {
                throw new RuntimeException("Leg was not properly marked as completed!");
            }
            updateLeg.close();
        } catch(SQLException e) {
            getLog().error("Could not mark leg as completed", e);
        }
    }

    private ArrayList<String> fetchBusStopEdgesByIds(ArrayList<String> busStopIds) {
        ArrayList<String> busStopEdgeIds = new ArrayList<>(busStopIds.size());

        try {
            String sql = String.format("SELECT sumo_edge FROM stop WHERE id IN (%1$s) ORDER BY FIELD(id, %1$s)",
                String.join(COMMA_DELIMITER, busStopIds));
            PreparedStatement fetchBusStopEdges = dbConnection.prepareStatement(sql);
            ResultSet fetchedBusStopEdges = fetchBusStopEdges.executeQuery();

            while(fetchedBusStopEdges.next()) {
                busStopEdgeIds.add(fetchedBusStopEdges.getString("sumo_edge"));
            }

            fetchedBusStopEdges.close();
            fetchBusStopEdges.close();

            if (busStopEdgeIds.size() != busStopIds.size()) {
                throw new RuntimeException("Number of fetched bus stops sumo_edge IDs is incorrect!");
            }
        } catch (SQLException e) {
            getLog().error("Could not execute get cab locations query", e);
        }

        return busStopEdgeIds;
    }

    private List<Integer> fetchBusStopsIndicesByEdge(String fromEdgeId, String toEdgeId) {
        try {
            PreparedStatement selectStopsByEdgeId = dbConnection.prepareStatement(
                "SELECT id, sumo_edge FROM stop WHERE sumo_edge IN (?, ?)");

            selectStopsByEdgeId.setString(1, fromEdgeId);
            selectStopsByEdgeId.setString(2, toEdgeId);
            ResultSet selectedStops = selectStopsByEdgeId.executeQuery();

            long fromStop;
            long toStop;
            if (!selectedStops.next()) {
                throw new RuntimeException("Number of fetched bus stops is not 2!");
            } else {
                if (fromEdgeId.equals(selectedStops.getString("sumo_edge"))) {
                    fromStop = selectedStops.getLong(ID_COLUMN_NAME);
                    if (!selectedStops.next()) {
                        throw new RuntimeException("Number of fetched bus stops is not 2!");
                    }
                    toStop = selectedStops.getLong(ID_COLUMN_NAME);
                }
                else {
                    toStop = selectedStops.getLong(ID_COLUMN_NAME);
                    if (!selectedStops.next()) {
                        throw new RuntimeException("Number of fetched bus stops is not 2!");
                    }
                    fromStop = selectedStops.getLong(ID_COLUMN_NAME);
                }
            }

            selectedStops.close();
            selectStopsByEdgeId.close();

            return List.of((int) fromStop, (int) toStop);
        } catch (SQLException e) {
            getLog().error("Could not execute select stops query", e);
        }

        return List.of();
    }

    private List<String> fetchAllBusStopEdgeIds() {
        ArrayList<String> busStopEdgeIds = new ArrayList<>();

        try {
            PreparedStatement countBusStopEdges = dbConnection.prepareStatement("SELECT COUNT(*) as total_rows FROM stop");
            ResultSet countedRows = countBusStopEdges.executeQuery();

            int rowsCount = countedRows.next() ? countedRows.getInt("total_rows") : 0;

            if (rowsCount == 0) {
                throw new RuntimeException("Wrong number of bus stops in the DB!");
            }

            countedRows.close();
            countBusStopEdges.close();

            PreparedStatement fetchAllBusStopEdges = dbConnection.prepareStatement("SELECT sumo_edge FROM stop");
            ResultSet fetchedBusStopEdges = fetchAllBusStopEdges.executeQuery();

            while(fetchedBusStopEdges.next()) {
                busStopEdgeIds.add(fetchedBusStopEdges.getString("sumo_edge"));
            }

            fetchedBusStopEdges.close();
            fetchAllBusStopEdges.close();

            if (busStopEdgeIds.size() != rowsCount) {
                throw new RuntimeException("Number of fetched bus stops sumo_edge IDs is incorrect!");
            }
        } catch (SQLException e) {
            getLog().error("Could not execute get cab locations query", e);
        }

        return busStopEdgeIds;
    }


    private long parsePerson(List<String> personList) {
        String person = personList.get(0);
        person = person.substring(1);
        return Long.parseLong(person);
    }

    // TODO check if this distance is good enough for the dispatcher
    private int calculateDistanceInMinutesBetweenTwoStops(String fromStopEdge,  String toStopEdge) {
        GeoPoint startPoint = getOs().getRoutingModule()
            .getConnection(fromStopEdge)
            .getStartNode()
            .getPosition();
        GeoPoint finalPoint = getOs().getRoutingModule()
            .getConnection(toStopEdge)
            .getEndNode()
            .getPosition();

        RoutingResponse response = getOs().getRoutingModule().calculateRoutes(
            new RoutingPosition(startPoint),
            new RoutingPosition(finalPoint),
            new RoutingParameters());
        double time = Math.ceil(response.getBestRoute().getTime() / 60);

        return (int) time;
    }

    private void createFileWithDistancesInMinutesBetweenStops() {
        String filePath = String.join(FileSystems.getDefault().getSeparator(),
            List.of("..","..","scenarios","bundle","theodorHeuss","distances.txt")); // we start from the mosaic-starter directory
        File file = new File(filePath);
        String unixNewLineCharacter = "\n";

		List<String> edges = fetchAllBusStopEdgeIds();
		long start = System.currentTimeMillis();
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(file, false));
            //first element is total count of bus stops + 1 because of Kern
			writer.append(String.valueOf(edges.size() + 1)).append(unixNewLineCharacter);

            // Kern takes also stops with id=0, so we should fill the matrix here with an irrelevant value
			for (int i = 0; i < edges.size() + 1; i++) {
				for (int j = 0; j < edges.size() + 1; j++) {
                    if (i == 0 || j == 0) {
                        writer.append("100000");
                    } else if (i == j) {
						writer.append("0");
					} else {
						writer.append(
							String.valueOf(calculateDistanceInMinutesBetweenTwoStops(edges.get(i-1), edges.get(j-1))));
					}

					if (j == edges.size()) {
						writer.append(unixNewLineCharacter);
                        continue;
					}

                    writer.append(COMMA_DELIMITER);
				}
			}
			writer.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		long finish = System.currentTimeMillis();
		System.out.println("Elapsed: " + (finish - start) + "\n");
	}

    private void checkTablesState(List<String> tableNames, boolean shouldTableBeEmpty) {
        try {
            for (String tableName : tableNames) {
                PreparedStatement checkCustomerTable = dbConnection.prepareStatement(
                    "SELECT 1 FROM %s LIMIT 1".formatted(tableName));
                ResultSet checkQueryResult = checkCustomerTable.executeQuery();

                if (shouldTableBeEmpty) {
                    if (checkQueryResult.next()) {
                        throw new RuntimeException("%s table is not empty!".formatted(tableName));
                    }
                } else {
                    if (!checkQueryResult.next()) {
                        throw new RuntimeException("%s table is empty!".formatted(tableName));
                    }
                }

                checkQueryResult.close();
                checkCustomerTable.close();
            }
        } catch(SQLException e) {
            getLog().error("Error checking tables in DB", e);
        }
    }

    private void connectToDatabase() {
        Properties properties = new Properties();
        properties.setProperty("user", "kabina");
        properties.setProperty("password", "kaboot");
        properties.setProperty("connectTimeout", "5000");

        try {
            dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/kabina", properties);
            getLog().info("Connected to database successfully");
        } catch (SQLException e) {
            getLog().error("Could not connect to database", e);
        }
    }

    private void closeDbConnection() {
        try {
            dbConnection.close();
            getLog().info("Closed database connection successfully");
        } catch (SQLException e) {
            getLog().error("Error closing connection to database", e);
        }
    }

    private record TaxiDispatchData(String taxiId, List<String> reservationIds) {

    }

    private static class TaxiLatestData {
        private int lastStatus;
        private ArrayList<String> edgesToVisit;
        private Integer currentLegId;
        private ArrayList<Integer> nextLegIds;

        public TaxiLatestData(int lastStatus, ArrayList<String> edgesToVisit, Integer currentLegId, ArrayList<Integer> nextLegIds) {
            this.lastStatus = lastStatus;
            this.edgesToVisit = edgesToVisit;
            this.currentLegId = currentLegId;
            this.nextLegIds = nextLegIds;
        }

		public int getLastStatus() {
			return lastStatus;
		}

		public void setLastStatus(int lastStatus) {
			this.lastStatus = lastStatus;
		}

		public ArrayList<String> getEdgesToVisit() {
			return edgesToVisit;
		}

		public void setEdgesToVisit(ArrayList<String> edgesToVisit) {
			this.edgesToVisit = edgesToVisit;
		}

		public Integer getCurrentLegId() {
			return currentLegId;
		}

		public void setCurrentLegId(Integer currentLegId) {
			this.currentLegId = currentLegId;
		}

		public ArrayList<Integer> getNextLegIds() {
			return nextLegIds;
		}

		public void setNextLegIds(ArrayList<Integer> nextLegIds) {
			this.nextLegIds = nextLegIds;
		}
	}
}
