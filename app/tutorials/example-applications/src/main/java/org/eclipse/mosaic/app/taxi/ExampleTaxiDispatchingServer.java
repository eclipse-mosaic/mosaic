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

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class ExampleTaxiDispatchingServer extends AbstractApplication<ServerOperatingSystem> implements TaxiServerApplication {

    private static final int DISPATCHER_MAX_DETOUR_CONFIG = 70;
    private static final int DISPATCHER_MAX_WAIT_CONFIG = 15;
    private static final int DISPATCHER_ASSIGNED_TAXI_STATUS = 0;
    private static final int DISPATCHER_FREE_TAXI_STATUS = 1;
    private static final int DISPATCHER_RECEIVED_ORDER_STATUS = 0;
    private static final int DISPATCHER_ASSIGNED_ORDER_STATUS = 1;
    private static final int DISPATCHER_ACCEPTED_ORDER_STATUS = 2;
    private static final String VEHICLE_MOSAIC_ID_PREFIX = "veh_";
    private static final int VEHICLE_ID_PREFIX_LENGTH = VEHICLE_MOSAIC_ID_PREFIX.length();
    private static final String ID_COLUMN_NAME = "id";
    private static final String COMMA_ID_DELIMITER = ",";
    private static Connection dbConnection;
    private static int lastRegisteredTaxiIndex = 0;
    private static int lastSavedReservationIndex = -1;

    @Override
    public void onStartup() {
        connectToDatabase();
        checkTablesState(List.of("customer", "stop", "cab"), false);
        checkTablesState(List.of("taxi_order", "leg", "route", "freetaxi_order"), true);
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
            registerTaxisInDb(emptyTaxis);
        }

        // select all unassigned reservations
        List<TaxiReservation> unassignedReservations = taxiReservations.stream()
            .filter(taxiRes -> taxiRes.getReservationState() == TaxiReservation.ONLY_NEW_RESERVATIONS ||
                taxiRes.getReservationState() == TaxiReservation.ALREADY_RETRIEVED_RESERVATIONS)
            .filter(taxiRes -> Integer.parseInt(taxiRes.getId()) > lastSavedReservationIndex)
            .toList();

        if (!unassignedReservations.isEmpty()) {
            insertNewReservationsInDb(unassignedReservations);
        }

        List<TaxiDispatchData> taxiDispatchDataList = fetchAvailableTaxiDispatchData();

        if (!taxiDispatchDataList.isEmpty()) {
            for (TaxiDispatchData taxiDispatchData : taxiDispatchDataList) {
                getOs().sendInteractionToRti(
                    new TaxiDispatch(getOs().getSimulationTime(), taxiDispatchData.taxiId(), taxiDispatchData.customerIds())
                );
            }
        }

        List<TaxiVehicleData> assignedTaxis = taxis.stream()
            .filter(taxi -> taxi.getState() == TaxiVehicleData.OCCUPIED_TAXIS)
            .toList();

        if(!assignedTaxis.isEmpty()) {
            checkAssignedTaxis(assignedTaxis);
        }
    }

    @Override
    public void processEvent(Event event) throws Exception {

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

    private void updateTaxiStatusInDbByIds(String taxisToUpdateIds, int statusToUpdate) {
        try {
            PreparedStatement updateTaxiVehicles = dbConnection.prepareStatement(
                "UPDATE cab SET status = ? WHERE id IN (%s)".formatted(taxisToUpdateIds));

            updateTaxiVehicles.setInt(1, statusToUpdate);
            int updatedRows = updateTaxiVehicles.executeUpdate();

            if (updatedRows != taxisToUpdateIds.split(COMMA_ID_DELIMITER).length) {
                throw new RuntimeException("Not all statuses were updated!");
            }
            updateTaxiVehicles.close();
        } catch(SQLException e) {
            getLog().error("Error updating taxi status", e);
        }
    }

    private List<TaxiDispatchData> fetchAvailableTaxiDispatchData() {
        //Order status list
        //RECEIVED = 0
        //ASSIGNED = 1
        //ACCEPTED = 2
        //CANCELLED = 3
        //REJECTED = 4
        //ABANDONED = 5
        //REFUSED = 6
        //PICKEDUP = 7
        //COMPLETED = 8
        try {
            PreparedStatement fetchOrders = dbConnection.prepareStatement(
                "SELECT id, sumo_id, cab_id FROM taxi_order WHERE status = ?");
            fetchOrders.setInt(1, DISPATCHER_ASSIGNED_ORDER_STATUS);
            ResultSet fetchedOrders = fetchOrders.executeQuery();
            List<TaxiDispatchData> taxiDispatchDataList = new ArrayList<>();
            List<String> orderIdsToUpdate = new ArrayList<>();

            while (fetchedOrders.next()) {
				orderIdsToUpdate.add(fetchedOrders.getString(ID_COLUMN_NAME));
				taxiDispatchDataList.add(
					new TaxiDispatchData(
						VEHICLE_MOSAIC_ID_PREFIX + (fetchedOrders.getLong("cab_id") - 1),
						List.of(String.valueOf(fetchedOrders.getLong("sumo_id")))));
			}

            fetchOrders.close();
            fetchOrders.close();

            if (!orderIdsToUpdate.isEmpty()) {
                PreparedStatement updateOrders = dbConnection.prepareStatement(
                    "UPDATE taxi_order SET status = ? WHERE id IN (%s)".formatted(String.join(COMMA_ID_DELIMITER, orderIdsToUpdate))
                );
                updateOrders.setInt(1, DISPATCHER_ACCEPTED_ORDER_STATUS);

                int updatedRows = updateOrders.executeUpdate();
                if (updatedRows != orderIdsToUpdate.size()) {
                    throw new RuntimeException("Not all order statuses were set to 'ACCEPTED'!");
                }

                updateOrders.close();
            }

            return taxiDispatchDataList;
        } catch(SQLException e) {
            getLog().error("Error while fetching available orders", e);
        }

        return List.of();
    }

    private void fetchAvailableRoutes() {
        try {
            PreparedStatement fetchRoutes = dbConnection.prepareStatement(
                "SELECT l.id, from_stand, to_stand, place, distance, started, completed, " +
                    "l.status, route_id, r.cab_id, passengers FROM leg l, route r " +
                    "WHERE route_id=r.id and (l.status=1 OR l.status=5) ORDER by r.cab_id, route_id, place"
            );
            ResultSet fetchedRoutes = fetchRoutes.executeQuery();
            fetchedRoutes.close();
            fetchRoutes.close();
        } catch(SQLException e) {
            getLog().error("Error while fetching available routes", e);
        }
    }

    private void checkAssignedTaxis(List<TaxiVehicleData> taxis) {
        String assignedTaxisIds = parseMosaicVehicleIdToDbIndex(taxis).stream()
            .map(Object::toString)
            .collect(Collectors.joining(COMMA_ID_DELIMITER));

        if (assignedTaxisIds.isEmpty()) {
            return;
        }

        updateTaxiStatusInDbByIds(assignedTaxisIds, DISPATCHER_ASSIGNED_TAXI_STATUS);
        //TODO set current stop as location
        //getBusStopsIndicesByEdge(taxis.get(0))
    }

    private void registerTaxisInDb(List<TaxiVehicleData> taxis) {
        String idsToRegister = parseMosaicVehicleIdToDbIndex(taxis).stream()
            .filter(id -> id > lastRegisteredTaxiIndex)
            .map(Object::toString)
            .collect(Collectors.joining(COMMA_ID_DELIMITER));

        if (idsToRegister.isEmpty()) {
            return;
        }

        updateTaxiStatusInDbByIds(idsToRegister, DISPATCHER_FREE_TAXI_STATUS);
        lastRegisteredTaxiIndex += idsToRegister.split(COMMA_ID_DELIMITER).length;
    }

    private List<Integer> parseMosaicVehicleIdToDbIndex(List<TaxiVehicleData> taxis) {
        return taxis.stream()
            .map(TaxiVehicleData::getId)
            .map(id -> id.substring(VEHICLE_ID_PREFIX_LENGTH))
            .map(Integer::parseInt)
            .map(id -> id + 1) //db indices start from 1 not from 0 like in Mosaic
            .toList();
    }

    private void insertNewReservationsInDb(List<TaxiReservation> newTaxiReservations) {
        try {
            PreparedStatement insertReservations = dbConnection.prepareStatement(
                "INSERT INTO taxi_order (from_stand, to_stand, max_loss, max_wait, shared, " +
                "status, received, distance, customer_id, sumo_id) VALUES (?,?,?,?,true,?,?,?,?,?)");

            for (TaxiReservation reservation: newTaxiReservations) {
                List<Integer> busStopsIndices = getBusStopsIndicesByEdge(reservation.getFromEdge(), reservation.getToEdge());
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
            lastSavedReservationIndex +=  newTaxiReservations.size();
        } catch (SQLException e) {
            getLog().error("Could not execute insert reservations query", e);
        }
    }

    private List<Integer> getBusStopsIndicesByEdge(String fromEdgeId, String toEdgeId) {
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

    private void connectToDatabase() {
        Properties properties = new Properties();
        properties.setProperty("user", "kabina");
        properties.setProperty("password", "kaboot");
        properties.setProperty("connectTimeout", "5000");

        try {
            dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/kabina?serverTimezone=UTC", properties);
            getLog().info("Connected to database successfully");
        } catch (SQLException e) {
            getLog().error("Could not connect to database", e);
        }
    }

    private void closeDbConnection() {
        try {
            dbConnection.close();
        } catch (SQLException e) {
            getLog().error("Error closing connection to database", e);
        }
    }

    private record TaxiDispatchData(String taxiId, List<String> customerIds) {

    }
}
