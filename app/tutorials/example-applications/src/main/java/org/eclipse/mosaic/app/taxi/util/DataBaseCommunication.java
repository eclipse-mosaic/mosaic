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

package org.eclipse.mosaic.app.taxi.util;

import org.eclipse.mosaic.fed.application.ambassador.util.UnitLogger;
import org.eclipse.mosaic.fed.application.app.api.navigation.RoutingModule;
import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;
import org.eclipse.mosaic.lib.objects.taxi.TaxiVehicleData;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.eclipse.mosaic.app.taxi.ExampleTaxiDispatchingServer.*;
import static org.eclipse.mosaic.app.taxi.util.Constants.*;
import static org.eclipse.mosaic.app.taxi.util.ParserUtil.*;

public class DataBaseCommunication {

	private static final String COMMA_DELIMITER = ",";
	private static final String ID_COLUMN_NAME = "id";

	private final UnitLogger unitLogger;
	private Connection dbConnection;

	public DataBaseCommunication(UnitLogger unitLogger) {
		this.unitLogger = unitLogger;
		connectToDatabase();
	}

	private void connectToDatabase() {
		Properties properties = new Properties();
		properties.setProperty("user", "kabina");
		properties.setProperty("password", "kaboot");
		properties.setProperty("connectTimeout", "5000");

		try {
			dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/kabina", properties);
			unitLogger.info("Connected to database successfully");
		} catch (SQLException e) {
			unitLogger.error("Could not connect to database", e);
		}
	}

	public void closeDbConnection() {
		try {
			dbConnection.close();
			unitLogger.info("Closed database connection successfully");
		} catch (SQLException e) {
			unitLogger.error("Error closing connection to database", e);
		}
	}

	public void checkTablesState(final List<String> tableNames, final boolean shouldTableBeEmpty) {
		try {
			for (String tableName : tableNames) {
				PreparedStatement checkCustomerTable = dbConnection.prepareStatement(
					"SELECT 1 FROM ? LIMIT 1");
				checkCustomerTable.setString(1, tableName);
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
			unitLogger.error("Error checking tables in DB", e);
		}
	}

	public void markOrdersAsAccepted(List<String> orderIds) {
		try {
			String sql = "UPDATE taxi_order SET status = ? WHERE id IN (%s)";
			String placeholders = sql.formatted(orderIds.stream()
				.map(id -> "?")
				.collect(Collectors.joining(COMMA_DELIMITER)));
			sql = sql.formatted(placeholders);

			PreparedStatement updateOrders = dbConnection.prepareStatement(sql);
			updateOrders.setInt(1, DISPATCHER_ACCEPTED_ORDER_STATUS);
			for (int i = 1; i <= orderIds.size(); i++) {
				updateOrders.setString(i + 1, orderIds.get(i - 1));
			}

			if (updateOrders.executeUpdate() != orderIds.size()) {
				throw new RuntimeException("Not all order statuses were set to 'ACCEPTED'!");
			}
			updateOrders.close();
		} catch(SQLException e) {
			unitLogger.error("Error while setting a new status to the orders", e);
		}
	}

	public void updateOrdersByLegId(long legId, int status, boolean isStarted) {
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
			unitLogger.error("Error while updating orders by leg ID", e);
		}
	}

	public void updateCabLocation(String mosaicVehicleId, Integer finishedLegId) {
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
			unitLogger.error("Could not update cab location", e);
		}
	}

	public void updateRouteStatusByLegId(Integer legId, int status) {
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
			unitLogger.error("Could not update route's status", e);
		}
	}

	public void markLegAsStarted(Integer legId) {
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
			unitLogger.error("Could not mark leg as started", e);
		}
	}

	public void markLegAsCompleted(Integer legId) {
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
			unitLogger.error("Could not mark leg as completed", e);
		}
	}

	public List<String> fetchAllBusStopEdgeIds() {
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
			unitLogger.error("Could not execute get cab locations query", e);
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
			unitLogger.error("Could not execute select stops query", e);
		}

		return List.of();
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
			unitLogger.error("Could not execute get cab locations query", e);
		}

		return busStopEdgeIds;
	}

	public void setTaxiFreeStatusInDbByIds(List<Integer> taxisToUpdateIds) {
		List<String> idsToString = taxisToUpdateIds.stream()
			.map(Objects::toString)
			.toList();

		try {
			PreparedStatement updateTaxiVehicles = dbConnection.prepareStatement(
				"UPDATE cab SET status = ? WHERE id IN (%s)".formatted(String.join(COMMA_DELIMITER, idsToString)));

			updateTaxiVehicles.setInt(1, DISPATCHER_FREE_TAXI_STATUS);

			if (updateTaxiVehicles.executeUpdate() != taxisToUpdateIds.size()) {
				throw new RuntimeException("Not all statuses were updated!");
			}
			updateTaxiVehicles.close();
		} catch(SQLException e) {
			unitLogger.error("Error updating taxi status", e);
		}
	}

	public List<TaxiDispatchData> fetchAvailableTaxiDispatchData(HashMap<String, TaxiLatestData> cabsLatestData) {
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

			fetchAvailableRoutesAndUpdateCabLatestData(assignedOrderIds, cabsLatestData);
			markOrdersAsAccepted(assignedOrderIds);

			return taxiDispatchDataList;
		} catch(SQLException e) {
			unitLogger.error("Error while fetching available orders", e);
		}

		return List.of();
	}

	public int insertNewReservationsInDb(List<TaxiReservation> newTaxiReservations, RoutingModule routingModule) {
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
				insertReservations.setInt(3, ORDER_MAX_DETOUR_IN_PERCENTAGE_DISPATCHER_CONFIG);
				insertReservations.setInt(4, ORDER_MAX_WAIT_IN_MINUTES_DISPATCHER_CONFIG);
				insertReservations.setInt(5, DISPATCHER_RECEIVED_ORDER_STATUS);
				insertReservations.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
				insertReservations.setInt(7,
					calculateDistanceInMinutesBetweenTwoStops(reservation.getFromEdge(), reservation.getToEdge(), routingModule));
				insertReservations.setLong(8, parsePerson(reservation.getPersonList()));
				insertReservations.setLong(9, Long.parseLong(reservation.getId()));
				insertReservations.addBatch();
			}

			int[] insertedReservationsResults = insertReservations.executeBatch();
			for (int batchCommandResult : insertedReservationsResults) {
				if (batchCommandResult < 0) {
					throw new RuntimeException("Some reservations were not inserted correctly!");
				}
			}
			insertReservations.close();
			return newTaxiReservations.size();
		} catch (SQLException e) {
			unitLogger.error("Could not execute insert reservations query", e);
			return 0;
		}
	}

	private void fetchAvailableRoutesAndUpdateCabLatestData(List<String> orderIds, HashMap<String, TaxiLatestData> cabsLatestData) {
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
			unitLogger.error("Error while fetching available routes", e);
		}
	}
}
