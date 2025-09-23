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

import static org.eclipse.mosaic.app.taxi.TaxiDispatchingServer.*;
import static org.eclipse.mosaic.app.taxi.util.Constants.*;
import static org.eclipse.mosaic.app.taxi.util.ParserUtil.*;

public class DatabaseCommunication {

	private static final String COMMA_DELIMITER = ",";
	private static final String ID_COLUMN_NAME = "id";

	private final UnitLogger unitLogger;
	private Connection dbConnection;

	public DatabaseCommunication(UnitLogger unitLogger) {
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
		//todo: fix case when one reservation is completed during the drop-off
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

	private void initializeCabLatestData(List<String> orderIds, HashMap<String, TaxiLatestData> cabsLatestData) {
		String sqlOrders = """
			SELECT id, route_id, cab_id
			FROM taxi_order
			WHERE id IN (%s)
		""".formatted(String.join(COMMA_DELIMITER, orderIds));

		String sqlLegs = """
			SELECT id, from_stand, to_stand, route_id
			FROM leg
			WHERE route_id = ? ORDER BY id
		""";

		List<TaxiOrder> orders = new ArrayList<>();
		try (PreparedStatement ps = dbConnection.prepareStatement(sqlOrders)) {
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					orders.add(new TaxiOrder(
						rs.getLong("id"),
						-1,
						-1,
						rs.getLong("route_id"),
						"",
						rs.getLong("cab_id")
					));
				}

				if (orders.isEmpty()) {
					return;
				}
			}
		} catch(SQLException e) {
			unitLogger.error("Error while fetching orders", e);
		}

		for (int i = 0; i < orders.size(); i++) {
			long currentRouteId = orders.get(i).routeId;
			long currentCabId = orders.get(i).cabId;

			if (i != orders.size() - 1) {
				if (orders.get(i + 1).routeId == currentRouteId) {
					continue;
				}
			}

			List<Leg> legs = new ArrayList<>();
			try (PreparedStatement ps = dbConnection.prepareStatement(sqlLegs)) {
				ps.setLong(1, currentRouteId);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						legs.add(new Leg(
							rs.getLong("id"),
							rs.getInt("from_stand"),
							rs.getInt("to_stand"),
							rs.getLong("route_id")
						));
					}
				}
			} catch(SQLException e) {
				unitLogger.error("Error while fetching legs", e);
			}

			ArrayList<Integer> legsToVisit = new ArrayList<>();
			ArrayList<String> busStopIds = new ArrayList<>();

			for (Leg leg : legs) {
				legsToVisit.add((int) leg.id);
				busStopIds.add(String.valueOf(leg.toStand));
			}

			cabsLatestData.put(parseTaxiDbIndexToMosaicVehicleId(currentCabId),
				new TaxiLatestData(TaxiVehicleData.EMPTY_TAXIS, fetchBusStopEdgesByIds(busStopIds), null,
					legsToVisit));
		}
	}

	public List<TaxiDispatchData> newMethodForRouteFetching(HashMap<String, TaxiLatestData> cabsLatestData) {
		// Query 2: get legs for the route
		String sqlLegs = """
			SELECT id, from_stand, to_stand, route_id
			FROM leg
			WHERE route_id = ? ORDER BY id
		""";

		// Fill the lists
		List<TaxiOrder> orders = fetchAssignedTaxiOrders();
		if (orders.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> acceptedOrderIds = new ArrayList<>();
		List<TaxiDispatchData> taxiDispatchData = new ArrayList<>();
		List<TaxiOrder> currentOrders = new ArrayList<>();
		for (int i = 0; i < orders.size(); i++) {
			long currentRouteId = orders.get(i).routeId;
			currentOrders.add(orders.get(i));

			// Collect orders with the same route
			if (i != orders.size() - 1) {
				if (orders.get(i + 1).routeId == currentRouteId) {
					continue;
				}
			}

			// Taxi has still not finished with previous order
			if (cabsLatestData.get(parseTaxiDbIndexToMosaicVehicleId(currentOrders.get(0).cabId)).getLastStatus() != TaxiVehicleData.EMPTY_TAXIS) {
				currentOrders.clear();
				continue;
			}

			List<Leg> legs = new ArrayList<>();
			try (PreparedStatement ps = dbConnection.prepareStatement(sqlLegs)) {
				ps.setLong(1, currentRouteId);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						legs.add(new Leg(
							rs.getLong("id"),
							rs.getInt("from_stand"),
							rs.getInt("to_stand"),
							rs.getLong("route_id")
						));
					}
				}
			} catch(SQLException e) {
				unitLogger.error("Error while fetching legs", e);
			}

			// Only 1 order
			if (currentOrders.size() == 1) {
				taxiDispatchData.add(new TaxiDispatchData(parseTaxiDbIndexToMosaicVehicleId(currentOrders.get(0).cabId), List.of(currentOrders.get(0).sumoId)));
				acceptedOrderIds.add(String.valueOf(currentOrders.get(0).id));
				currentOrders.clear();
				continue;
			}

			// More than one orders
			List<String> dispatchSequence = new ArrayList<>();

			// Only 1 leg
			if (legs.size() == 1) {
				for(TaxiOrder order : currentOrders) {
					if(legs.get(0).fromStand == order.fromStand) {
						dispatchSequence.add(order.sumoId);
					}
				}
				for(TaxiOrder order : currentOrders) {
					if(legs.get(0).toStand == order.toStand) {
						dispatchSequence.add(order.sumoId);
					}
				}
			} else { // More than one legs
				for (int j = 0; j < legs.size(); j++) {
					// First leg
					if (j == 0) {
						for (TaxiOrder order: currentOrders) {
							if (legs.get(j).fromStand == order.fromStand) {
								dispatchSequence.add(order.sumoId);
							}
						}
						continue;
					}

					// Orders to pick up
					for (TaxiOrder order: currentOrders) {
						if (legs.get(j).fromStand == order.fromStand) {
							dispatchSequence.add(order.sumoId);
						}
					}
					// Orders to drop off
					for (TaxiOrder order: currentOrders) {
						if (legs.get(j).fromStand == order.toStand) {
							dispatchSequence.add(order.sumoId);
						}
					}

					// Last leg
					if (j == legs.size() - 1) {
						for (TaxiOrder order: currentOrders) {
							if (legs.get(j).toStand == order.toStand) {
								dispatchSequence.add(order.sumoId);
							}
						}
					}
				}
			}

			if (dispatchSequence.size() % 2 != 0) {
				throw new RuntimeException("Odd size of dispatch sequence: %s".formatted(String.join(",", dispatchSequence)));
			}

			taxiDispatchData.add(new TaxiDispatchData(parseTaxiDbIndexToMosaicVehicleId(currentOrders.get(0).cabId), dispatchSequence));
			acceptedOrderIds.addAll(currentOrders.stream()
				.map(taxiOrder -> String.valueOf(taxiOrder.id))
				.toList()
			);
			currentOrders.clear();
		}

		initializeCabLatestData(acceptedOrderIds, cabsLatestData);
		markOrdersAsAccepted(acceptedOrderIds);

		return taxiDispatchData;
	}

	private List<TaxiOrder> fetchAssignedTaxiOrders() {
		String sqlOrders = """
			SELECT id, from_stand, to_stand, route_id, sumo_id, cab_id
			FROM taxi_order
			WHERE status = ?
		""";

		List<TaxiOrder> taxiOrders = new ArrayList<>();
		try (PreparedStatement ps = dbConnection.prepareStatement(sqlOrders)) {
			ps.setLong(1, DISPATCHER_ASSIGNED_ORDER_STATUS);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					taxiOrders.add(new TaxiOrder(
						rs.getLong("id"),
						rs.getInt("from_stand"),
						rs.getInt("to_stand"),
						rs.getLong("route_id"),
						rs.getString("sumo_id"),
						rs.getLong("cab_id")
					));
				}
			}
		} catch(SQLException e) {
			unitLogger.error("Error while fetching orders", e);
		}
		return taxiOrders;
	}

	private static class TaxiOrder {
		long id;
		int fromStand;
		int toStand;
		long routeId;
		String sumoId;
		long cabId;

		public TaxiOrder(long id, int fromStand, int toStand, long routeId, String sumoId, long cabId) {
			this.id = id;
			this.fromStand = fromStand;
			this.toStand = toStand;
			this.routeId = routeId;
			this.sumoId = sumoId;
			this.cabId = cabId;
		}
	}

	private static class Leg {
		long id;
		int fromStand;
		int toStand;
		long routeId;

		public Leg(long id, int fromStand, int toStand, long routeId) {
			this.id = id;
			this.fromStand = fromStand;
			this.toStand = toStand;
			this.routeId = routeId;
		}
	}
}
