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
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;
import org.eclipse.mosaic.lib.objects.taxi.TaxiVehicleData;
import org.eclipse.mosaic.lib.routing.RoutingParameters;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.routing.RoutingResponse;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.sql.*;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class ExampleTaxiDispatchingServer extends AbstractApplication<ServerOperatingSystem> implements TaxiServerApplication {

    private static final int MAX_DETOUR_DISPATCHER_CONFIG = 70;
    private static final int MAX_WAIT_DISPATCHER_CONFIG = 15;
    private static final int DISPATCHER_ASSIGNED_TAXI_STATUS = 0;
    private static final int DISPATCHER_FREE_TAXI_STATUS = 1;
    private static Connection dbConnection;

    @Override
    public void onStartup() {
        connectToDatabase();
    }

    @Override
    public void onShutdown() {

    }

    @Override
    public void onTaxiDataUpdate(List<TaxiVehicleData> taxis, List<TaxiReservation> taxiReservations) {

        // select all empty taxis
        List<String> emptyTaxis = taxis.stream()
            .filter(taxi -> taxi.getState() == TaxiVehicleData.EMPTY_TAXIS)
            .map(TaxiVehicleData::getId)
            .collect(Collectors.toList());

        // select all unassigned reservations
        List<TaxiReservation> unassignedReservations = taxiReservations.stream()
            .filter(taxiRes -> taxiRes.getReservationState() == TaxiReservation.ONLY_NEW_RESERVATIONS ||
                taxiRes.getReservationState() == TaxiReservation.ALREADY_RETRIEVED_RESERVATIONS)
            .toList();

        saveReservationsInDb(unassignedReservations);
//
//        for (TaxiReservation unassignedReservation: unassignedReservations) {
//            if (emptyTaxis.isEmpty()) {
//                break;
//            }
//            // for each unassigned reservation, just choose an empty taxi randomly
//            String emptyTaxi = emptyTaxis.remove(getRandom().nextInt(emptyTaxis.size()));
//
//            //  and send the dispatch command to SumoAmbassador via TaxiDispatch interaction
//            getOs().sendInteractionToRti(
//                    new TaxiDispatch(getOs().getSimulationTime(), emptyTaxi, Lists.newArrayList(unassignedReservation.getId()))
//            );
//
//        }
    }

    @Override
    public void processEvent(Event event) throws Exception {

    }

    private void fetchAvailableOrders() {
        try {
            PreparedStatement fetchOrders = dbConnection.prepareStatement(
                "SELECT * FROM taxi_order WHERE status IN (0,1,2,6,7)");
            ResultSet fetchedOrders = fetchOrders.executeQuery();
        } catch(SQLException e) {
            getLog().warn("Error while fetching available orders", e);
        }
    }

    private void fetchAvailableRoutes() {
        try {
            PreparedStatement fetchRoutes = dbConnection.prepareStatement(
                "SELECT l.id, from_stand, to_stand, place, distance, started, completed, " +
                    "l.status, route_id, r.cab_id, passengers FROM leg l, route r " +
                    "WHERE route_id=r.id and (l.status=1 OR l.status=5) ORDER by r.cab_id, route_id, place"
            );
            ResultSet fetchedRoutes = fetchRoutes.executeQuery();
        } catch(SQLException e) {
            getLog().warn("Error while fetching available routes", e);
        }
    }

    private void insertCabsInDb(List<TaxiVehicleData> taxis) {
        try {
            PreparedStatement insertCabs = dbConnection.prepareStatement(
              "INSERT INTO cab (location, name, status, seats) VALUES (?,?,?,?)"
            );

            for(TaxiVehicleData taxi : taxis) {
                insertCabs.setInt(1, 0); // TODO assign a correct location(maybe a from_stand)
                insertCabs.setString(2, taxi.getId());
                insertCabs.setInt(3, taxi.getState() == TaxiVehicleData.EMPTY_TAXIS
                    ? DISPATCHER_FREE_TAXI_STATUS : DISPATCHER_ASSIGNED_TAXI_STATUS);
                insertCabs.setInt(4, taxi.getPersonCapacity());
            }
        } catch(SQLException e) {
            getLog().warn("Error while inserting cabs in the DB", e);
        }
    }

    private void saveReservationsInDb(List<TaxiReservation> newTaxiReservations) {
        try {
            PreparedStatement insertReservations = dbConnection.prepareStatement(
                "INSERT INTO taxi_order (from_stand, to_stand, max_loss, max_wait, shared, " +
                "status, received, distance, customer_id) VALUES (?,?,?,?,true,0,?,?,?)");

            for(TaxiReservation reservation: newTaxiReservations) {
                List<Integer> busStopsIndices = getBusStopsIndicesByEdge(reservation.getFromEdge(), reservation.getToEdge());
                assert busStopsIndices.size() == 2;

                insertReservations.setInt(1, busStopsIndices.get(0));
                insertReservations.setInt(2, busStopsIndices.get(1));
                insertReservations.setInt(3, MAX_DETOUR_DISPATCHER_CONFIG);
                insertReservations.setInt(4, MAX_WAIT_DISPATCHER_CONFIG);
                insertReservations.setTimestamp(5, new Timestamp(System.currentTimeMillis())); // TODO check if there can't be a better method
                insertReservations.setInt(6,
                    calculateDistanceInSecondsBetweenTwoStops(reservation.getFromEdge(), reservation.getToEdge()));
                insertReservations.setLong(7, parsePerson(reservation.getPersonList()));
                insertReservations.addBatch();
                insertReservations.clearParameters();
            }

            int[] insertedReservations = insertReservations.executeBatch();
        } catch (SQLException e) {
            getLog().warn("Could not execute insert reservations query", e);
        }
    }

    private List<Integer> getBusStopsIndicesByEdge(String fromEdgeId, String toEdgeId) {
        try {
            PreparedStatement selectStopsByEdgeId = dbConnection.prepareStatement(
                "SELECT id, sumo_edge FROM stop WHERE sumo_edge IN (?, ?)");

            selectStopsByEdgeId.setString(1, fromEdgeId);
            selectStopsByEdgeId.setString(2, toEdgeId);
            ResultSet selectedStops = selectStopsByEdgeId.executeQuery();
            assert selectedStops.getFetchSize() == 2;

            long fromStop = 0L;
            long toStop = 0L;
            if(selectedStops.next()) {
                if(fromEdgeId.equals(selectedStops.getString("sumo_edge"))) {
                    fromStop = selectedStops.getLong("id");
                    selectedStops.next();
                    toStop = selectedStops.getLong("id");
                }
                else {
                    toStop = selectedStops.getLong("id");
                    selectedStops.next();
                    fromStop = selectedStops.getLong("id");
                }
            }

            return List.of((int)fromStop, (int)toStop);
        } catch (SQLException e) {
            getLog().warn("Could not execute select stops query", e);
        }

        return List.of();
    }

    private long parsePerson(List<String> personList) {
        String person = personList.get(0);
        person = person.substring(1);
        return Long.parseLong(person);
    }

    // TODO check if this distance is good enough for the dispatcher
    private int calculateDistanceInSecondsBetweenTwoStops(String fromStopEdge,  String toStopEdge) {
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
        double time = Math.ceil(response.getBestRoute().getTime());

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
            getLog().warn("Could not connect to database", e);
        }
    }
}
