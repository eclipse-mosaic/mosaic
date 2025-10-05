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

import static org.eclipse.mosaic.app.taxi.TaxiDispatchingServer.TAXI_ORDER_MAX_DETOUR_IN_PERCENTAGE;
import static org.eclipse.mosaic.app.taxi.TaxiDispatchingServer.TAXI_ORDER_MAX_WAIT_IN_MINUTES;
import static org.eclipse.mosaic.app.taxi.util.Constants.*;
import static org.eclipse.mosaic.app.taxi.util.ExternalFilesUtil.calculateDistanceInMinutesBetweenTwoStops;
import static org.eclipse.mosaic.app.taxi.util.ParserUtil.*;

public class DatabaseCommunication {

	private final UnitLogger unitLogger;
	private final RoutingModule routingModule;
	private Connection dbConnection;

	public DatabaseCommunication(UnitLogger unitLogger, RoutingModule routingModule) {
		this.unitLogger = unitLogger;
		this.routingModule = routingModule;
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
		for (String tableName : tableNames) {
			String sql = "SELECT COUNT(*) AS cnt FROM " + tableName;
			try (PreparedStatement ps = dbConnection.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {

				if (rs.next()) {
					int count = rs.getInt("cnt");
					if (shouldTableBeEmpty && count > 0) {
						throw new IllegalStateException(tableName + " table is not empty!");
					}
					if (!shouldTableBeEmpty && count == 0) {
						throw new IllegalStateException(tableName + " table is empty!");
					}
				}

			} catch (SQLException e) {
				unitLogger.error("Error checking table state: {}", tableName, e);
			}
		}
	}

	public void markOrdersAsAccepted(List<Long> orderIds) {
		if (orderIds.isEmpty()) {
			return;
		}

		String placeholders = orderIds.stream()
			.map(id -> "?")
			.collect(Collectors.joining(", "));

		String sql = "UPDATE taxi_order SET status = ? WHERE id IN (%s)".formatted(placeholders);

		try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
			ps.setInt(1, DISPATCHER_ACCEPTED_ORDER_STATUS);

			for (int i = 0; i < orderIds.size(); i++) {
				ps.setLong(i + 2, orderIds.get(i));
			}

			int updated = ps.executeUpdate();
			if (updated != orderIds.size()) {
				throw new RuntimeException("Expected to update %s orders, but updated %s instead".formatted(orderIds.size(), updated));
			}
		} catch (SQLException e) {
			unitLogger.error("Error while setting order statuses to ACCEPTED", e);
		}
	}

	public void updateOrdersByLegId(long legId, int status, boolean isStarted, long simulationTimeInSeconds) {
		try {
			String dbField = isStarted ? "started" : "completed";
			String secondsField = isStarted ? "started_seconds" : "completed_seconds";
			String standField = isStarted ? "from_stand" : "to_stand";

			String sql = """
				UPDATE taxi_order o
				JOIN leg l ON o.route_id = l.route_id
				SET o.%s = ?, o.status = ?, o.%s = ?
				WHERE l.id = ? AND l.passengers > 0 AND o.%s = (SELECT %s FROM leg WHERE id = ?)
				""".formatted(dbField, secondsField, standField, standField);

			try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
				ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
				ps.setInt(2, status);
				ps.setLong(3, simulationTimeInSeconds);
				ps.setLong(4, legId);
				ps.setLong(5, legId);
				int updated = ps.executeUpdate();

				if (updated > 0) {
					unitLogger.debug("{} orders updated for leg {}", updated, legId);
				}
			}
		} catch (SQLException e) {
			unitLogger.error("Error while updating orders by leg ID", e);
		}
	}

	public void markOrdersAsStartedByLegId(long legId, long simulationTimeInSeconds) {
		String sql = """
			UPDATE taxi_order
			SET started = ?, status = ?, started_seconds = ?
			WHERE route_id = (SELECT route_id FROM leg WHERE leg.id = ?)
			""";

		try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
			ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
			ps.setInt(2, DISPATCHER_PICKEDUP_ORDER_STATUS);
			ps.setLong(3, simulationTimeInSeconds);
			ps.setLong(4, legId);

			if (ps.executeUpdate() < 1) {
				throw new RuntimeException("No orders updated for legId=%s with status=%s".formatted(legId, DISPATCHER_PICKEDUP_ORDER_STATUS));
			}
		} catch (SQLException e) {
			unitLogger.error("Error while updating orders by leg ID {}", legId, e);
		}
	}

	public void updateCabLocation(String mosaicVehicleId, Integer finishedLegId) {
		String sql = "UPDATE cab SET location = (SELECT leg.to_stand FROM leg WHERE leg.id = ?) WHERE cab.id = ?";

		try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
			ps.setLong(1, finishedLegId);
			ps.setLong(2, parseMosaicVehicleIdToTaxiDbIndex(mosaicVehicleId));

			if (ps.executeUpdate() != 1) {
				throw new RuntimeException("Cab's location was not updated correctly");
			}
		} catch(SQLException e) {
			unitLogger.error("Could not update cab location", e);
		}
	}

	public void updateRouteStatusByLegId(Integer legId, int status) {
		String sql = "UPDATE route SET status = ? WHERE route.id = (SELECT route_id FROM leg WHERE leg.id = ?)";

		try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
			ps.setInt(1, status);
			ps.setLong(2, legId);

			if (ps.executeUpdate() != 1) {
				throw new RuntimeException("Route status was not updated correctly!");
			}
		} catch(SQLException e) {
			unitLogger.error("Could not update route status", e);
		}
	}

	public void markLegAsStarted(Integer legId, long simulationTimeSeconds) {
		String sql = "UPDATE leg SET started = ?, status = ?, started_seconds = ? WHERE id = ?";

		try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
			ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
			ps.setInt(2, DISPATCHER_STARTED_ROUTE_LEG_STATUS);
			ps.setLong(3, simulationTimeSeconds);
			ps.setLong(4, legId);

			if (ps.executeUpdate() != 1) {
				throw new RuntimeException("Leg was not correctly marked as started!");
			}
		} catch(SQLException e) {
			unitLogger.error("Could not mark leg as started", e);
		}
	}

	public void markLegAsCompleted(Integer legId, long simulationTimeInSeconds) {
		String sql = "UPDATE leg SET completed = ?, status = ?, completed_seconds = ? WHERE id = ?";

		try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
			ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
			ps.setInt(2, DISPATCHER_COMPLETED_ROUTE_LEG_STATUS);
			ps.setLong(3, simulationTimeInSeconds);
			ps.setLong(4, legId);

			if (ps.executeUpdate() != 1) {
				throw new RuntimeException("Leg was not properly marked as completed!");
			}
		} catch(SQLException e) {
			unitLogger.error("Could not mark leg as completed", e);
		}
	}

	public List<BusStop> fetchAllBusStops() {
		String sql = "SELECT id, sumo_edge, latitude, longitude FROM stop";
		List<BusStop> busStops = new ArrayList<>();

		try (PreparedStatement ps = dbConnection.prepareStatement(sql);
			ResultSet rs = ps.executeQuery()) {

			while (rs.next()) {
				BusStop busStop = new BusStop(rs.getLong("id"), rs.getString("sumo_edge"),
					rs.getDouble("latitude"), rs.getDouble("longitude"));

				busStops.add(busStop);
			}

			if (busStops.isEmpty()) {
				throw new IllegalStateException("No bus stops found in the DB!");
			}

		} catch (SQLException e) {
			unitLogger.error("Could not fetch bus stop edges", e);
		}

		return busStops;
	}

	private List<BusStop> fetchBusStopsByEdge(String fromEdgeId, String toEdgeId) {
		String sql = "SELECT id, sumo_edge, latitude, longitude FROM stop WHERE sumo_edge IN (?, ?)";

		try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
			ps.setString(1, fromEdgeId);
			ps.setString(2, toEdgeId);

			Map<String, BusStop> stopByEdge = new HashMap<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					BusStop busStop = new BusStop(rs.getLong("id"), rs.getString("sumo_edge"),
						rs.getDouble("latitude"), rs.getDouble("longitude"));

					stopByEdge.put(busStop.sumoEdge(), busStop);
				}
			}

			BusStop fromStop = stopByEdge.get(fromEdgeId);
			BusStop toStop = stopByEdge.get(toEdgeId);

			if (fromStop == null || toStop == null) {
				throw new IllegalStateException("Did not fetch both bus stops for edges %s and %s"
					.formatted(fromEdgeId, toEdgeId));
			}

			return List.of(fromStop, toStop);

		} catch (SQLException e) {
			unitLogger.error("Error while fetching stops by edge IDs [{} , {}]", fromEdgeId, toEdgeId, e);
			return List.of();
		}
	}

	private ArrayList<String> fetchBusStopEdgesByIds(ArrayList<String> busStopIds) {
		String placeholders = busStopIds.stream()
			.map(id -> "?")
			.collect(Collectors.joining(", "));

		String sql = """
        	SELECT id, sumo_edge
        	FROM stop
        	WHERE id IN (%s)
        	""".formatted(placeholders);

		Map<String, String> edgesById = new HashMap<>();

		try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
			int index = 1;
			for (String id : busStopIds) {
				ps.setString(index++, id);
			}

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					edgesById.put(rs.getString("id"), rs.getString("sumo_edge"));
				}
			}
		} catch (SQLException e) {
			unitLogger.error("Error while fetching bus stop edges", e);
		}

		// Preserve the order of input IDs
		List<String> busStopEdgeIds = busStopIds.stream()
			.map(id -> {
				String edge = edgesById.get(id);
				if (edge == null) {
					throw new IllegalStateException("Missing sumo_edge for stop id " + id);
				}
				return edge;
			})
			.toList();

		return new ArrayList<>(busStopEdgeIds);
	}

	public void setTaxiFreeStatusInDbById(Integer taxiToUpdateId) {
		String sql = "UPDATE cab SET status = ? WHERE id = ?";

		try (PreparedStatement updateTaxiVehicles = dbConnection.prepareStatement(sql)) {
			updateTaxiVehicles.setInt(1, DISPATCHER_FREE_TAXI_STATUS);
			updateTaxiVehicles.setInt(2, taxiToUpdateId);

			if (updateTaxiVehicles.executeUpdate() != 1) {
				throw new RuntimeException("Free status was not set correctly for cab ID: %s".formatted(taxiToUpdateId));
			}
		} catch(SQLException e) {
			unitLogger.error("Error updating taxi status", e);
		}
	}

	public int insertNewReservationsInDb(List<TaxiReservation> newTaxiReservations, long simulationTimeInSeconds) {
		if (newTaxiReservations.isEmpty()) {
			return 0;
		}

		String sql = """
			INSERT INTO taxi_order (
				from_stand, to_stand, max_loss, max_wait, shared,
				status, received, distance, customer_id, sumo_id, distance_seconds, received_seconds
			) VALUES (?, ?, ?, ?, true, ?, ?, ?, ?, ?, ?, ?)
			""";
		try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
			for (TaxiReservation reservation: newTaxiReservations) {
				prepareInsertReservationStatement(ps, reservation, simulationTimeInSeconds);
				ps.addBatch();
			}

			int[] insertedReservationsResults = ps.executeBatch();

			long failures = Arrays.stream(insertedReservationsResults)
				.filter(r -> r < 0)
				.count();

			if (failures > 0) {
				throw new RuntimeException("Some reservations were not inserted correctly!");
			}
			return insertedReservationsResults.length;
		} catch (SQLException e) {
			unitLogger.error("Could not execute insert reservations query", e);
			return 0;
		}
	}

	private void prepareInsertReservationStatement(PreparedStatement ps, TaxiReservation reservation, long simulationTimeInSeconds)
		throws SQLException {
		List<BusStop> busStops = fetchBusStopsByEdge(reservation.getFromEdge(), reservation.getToEdge());
		if (busStops.size() != 2) {
			throw new IllegalArgumentException("Expected 2 bus stops, got " + busStops.size());
		}

		ps.setInt(1, (int) busStops.get(0).id());
		ps.setInt(2, (int) busStops.get(1).id());
		ps.setInt(3, TAXI_ORDER_MAX_DETOUR_IN_PERCENTAGE);
		ps.setInt(4, TAXI_ORDER_MAX_WAIT_IN_MINUTES);
		ps.setInt(5, DISPATCHER_RECEIVED_ORDER_STATUS);
		ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));

		DistanceBetweenStops distanceBetweenStops = calculateDistanceInMinutesBetweenTwoStops(
			busStops.get(0),
			busStops.get(1),
			routingModule
		);

		ps.setInt(7, distanceBetweenStops.getDistanceInMinutes());
		ps.setLong(8, parsePerson(reservation.getPersonList()));
		ps.setLong(9, Long.parseLong(reservation.getId()));
		ps.setInt(10, (int) Math.ceil(distanceBetweenStops.distanceSeconds()));
		ps.setLong(11, simulationTimeInSeconds);
	}

	private void initializeCabLatestData(List<Long> orderIds, HashMap<String, TaxiLatestData> cabsLatestData) {
		if (orderIds.isEmpty()) {
			return;
		}

		List<TaxiOrder> orders = fetchOrdersByIds(orderIds);
		if (orders.isEmpty()) {
			return;
		}

		Map<Long, List<TaxiOrder>> ordersByRoute =
			orders.stream().collect(Collectors.groupingBy(TaxiOrder::routeId));

		Map<Long, List<Leg>> legsByRoute = fetchLegsByRoute(ordersByRoute.keySet());

		for (var entry : ordersByRoute.entrySet()) {
			long routeId = entry.getKey();
			List<TaxiOrder> routeOrders = entry.getValue();

			// All orders on a route should have same cabId
			long cabId = routeOrders.get(0).cabId();
			String cabKey = parseTaxiDbIndexToMosaicVehicleId(cabId);

			List<Leg> legs = legsByRoute.getOrDefault(routeId, List.of());

			List<Integer> legsToVisit = legs.stream()
				.map(leg -> (int) leg.id())
				.toList();

			List<String> busStopIds = legs.stream()
				.map(leg -> String.valueOf(leg.toStand()))
				.toList();

			TaxiLatestData data = new TaxiLatestData(
				TaxiVehicleData.EMPTY_TAXIS,
				fetchBusStopEdgesByIds(new ArrayList<>(busStopIds)),
				null,
				new ArrayList<>(legsToVisit)
			);

			cabsLatestData.put(cabKey, data);
		}
	}

	public List<TaxiDispatchData> fetchTaxiDispatchDataAndUpdateLatestData(HashMap<String, TaxiLatestData> cabsLatestData) {
		List<TaxiOrder> orders = fetchAssignedTaxiOrders();
		if (orders.isEmpty()) {
			return Collections.emptyList();
		}

		Map<Long, List<TaxiOrder>> ordersGroupedByRoute = orders.stream()
			.collect(Collectors.groupingBy(TaxiOrder::routeId));

		Map<Long, List<Leg>> legsGroupedByRoute = fetchLegsByRoute(ordersGroupedByRoute.keySet());

		List<Long> acceptedOrderIds = new ArrayList<>();
		List<TaxiDispatchData> taxiDispatchData = new ArrayList<>();

		for (var entry : ordersGroupedByRoute.entrySet()) {
			long routeId = entry.getKey();
			List<TaxiOrder> routeOrders = entry.getValue();

			TaxiOrder firstOrder = routeOrders.get(0);
			String cabId = parseTaxiDbIndexToMosaicVehicleId(firstOrder.cabId());

			// Skip if cab is busy
			if (cabsLatestData.get(cabId).getLastStatus() != TaxiVehicleData.EMPTY_TAXIS) {
				continue;
			}

			List<Leg> legs = legsGroupedByRoute.getOrDefault(routeId, List.of());

			List<String> dispatchSequence = (routeOrders.size() == 1)
				? List.of(firstOrder.sumoId())
				: buildDispatchSequence(legs, routeOrders);

			taxiDispatchData.add(new TaxiDispatchData(cabId, dispatchSequence));

			acceptedOrderIds.addAll(routeOrders.stream()
				.map(TaxiOrder::id)
				.toList());
		}

		initializeCabLatestData(acceptedOrderIds, cabsLatestData);
		markOrdersAsAccepted(acceptedOrderIds);

		return taxiDispatchData;
	}

	private List<TaxiOrder> fetchOrdersByIds(List<Long> orderIds) {
		String placeholders = orderIds.stream()
			.map(id -> "?")
			.collect(Collectors.joining(","));

		String sql = """
        	SELECT id, route_id, cab_id
        	FROM taxi_order
        	WHERE id IN (%s)
        	""".formatted(placeholders);

		List<TaxiOrder> orders = new ArrayList<>();

		try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
			int index = 1;
			for (Long id : orderIds) {
				ps.setLong(index++, id);
			}

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					orders.add(
						new TaxiOrder(rs.getLong("id"), -1, -1, rs.getLong("route_id"), "", rs.getLong("cab_id")));
				}
			}
		} catch (SQLException e) {
			unitLogger.error("Error while fetching orders by IDs", e);
		}

		return orders;
	}

	private List<String> buildDispatchSequence(List<Leg> legs, List<TaxiOrder> orders) {
		List<String> sequence = new ArrayList<>();
		Set<String> pickedUp = new HashSet<>();
		Set<String> droppedOff = new HashSet<>();

		for (Leg leg : legs) {
			// Pick-ups at this leg's fromStand
			orders.stream()
				.filter(o -> o.fromStand() == leg.fromStand())
				.filter(o -> !pickedUp.contains(o.sumoId())) // skip if already picked
				.forEach(o -> {
					sequence.add(o.sumoId());
					pickedUp.add(o.sumoId());
				});

			// Drop-offs at this leg's toStand
			orders.stream()
				.filter(o -> o.toStand() == leg.toStand())
				.filter(o -> pickedUp.contains(o.sumoId())) // only drop if picked up
				.filter(o -> !droppedOff.contains(o.sumoId())) // skip if already dropped
				.forEach(o -> {
					sequence.add(o.sumoId());
					droppedOff.add(o.sumoId());
				});
		}

		if (sequence.size() % 2 != 0 || sequence.size() < 4) {
			throw new IllegalStateException("Wrong dispatch sequence: " + sequence);
		}

		return sequence;
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
					taxiOrders.add(new TaxiOrder(rs.getLong("id"), rs.getInt("from_stand"), rs.getInt("to_stand"),
						rs.getLong("route_id"), rs.getString("sumo_id"), rs.getLong("cab_id")));
				}
			}
		} catch(SQLException e) {
			unitLogger.error("Error while fetching orders", e);
		}
		return taxiOrders;
	}

	private Map<Long, List<Leg>> fetchLegsByRoute(Set<Long> routeIds) {
		if (routeIds.isEmpty()) {
			return Map.of();
		}

		String placeholders = routeIds.stream()
			.map(id -> "?")
			.collect(Collectors.joining(", "));

		String sql = """
			SELECT id, from_stand, to_stand, passengers, route_id
			FROM leg
			WHERE route_id IN (%s)
			ORDER BY route_id, id
			""".formatted(placeholders);

		Map<Long, List<Leg>> legsByRoute = new HashMap<>();

		try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
			int index = 1;
			for (Long routeId : routeIds) {
				ps.setLong(index++, routeId);
			}

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Leg leg = new Leg(
						rs.getLong("id"),
						rs.getInt("from_stand"),
						rs.getInt("to_stand"),
						rs.getInt("passengers"),
						rs.getLong("route_id")
					);

					legsByRoute
						.computeIfAbsent(leg.routeId, k -> new ArrayList<>())
						.add(leg);
				}
			}
		} catch (SQLException e) {
			unitLogger.error("Error while fetching legs for routes: {}", routeIds, e);
		}

		return legsByRoute;
	}

	private record TaxiOrder(long id, int fromStand, int toStand, long routeId, String sumoId, long cabId) { }

	private record Leg(long id, int fromStand, int toStand, int passengers, long routeId) {	}
}
