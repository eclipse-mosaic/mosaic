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
import org.eclipse.mosaic.lib.enums.VehicleClass;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.routing.RoutingParameters;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.routing.RoutingResponse;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

public class ExternalFilesUtil {

	public static void executePythonScripts(UnitLogger unitLogger, boolean shouldIncludeScriptLogs, String scenarioName) {
		try {
			// Path to the script directory
			File scriptDir = getRootFolder()
				.resolve("scenarios/bundle/pythonScripts")
				.toFile();

			// Create the process and set the working directory to the script folder
			String pythonCmd = isWindows() ? "python" : "python3"; // you might have to adjust this if you are not working on Windows
			ProcessBuilder pb = new ProcessBuilder(pythonCmd, "executeScripts.py", scenarioName);
			pb.directory(scriptDir);
			pb.redirectErrorStream(true);

			Process process = pb.start();

			if (shouldIncludeScriptLogs) {
				// Read the output
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String line;
				while((line = reader.readLine()) != null) {
					System.out.println("Python output: " + line);
				}
			}

			int exitCode = process.waitFor();
			if (exitCode != 0) {
				throw new RuntimeException("Python script execution failed: " + exitCode);
			}
		} catch (IOException | InterruptedException e) {
			unitLogger.error("Failed to execute python scripts: {}", e.getMessage());
		}
	}

	public static void startDispatcher(UnitLogger unitLogger, String scenarioName, String pathToDispatcher) {
		if (pathToDispatcher.endsWith("/PUT/YOUR/PATH/HERE")) {
			throw new RuntimeException("Path to dispatcher is not set");
		}

		try {
			File log = getDispatcherLogFileInScenarioDirectory(scenarioName);
			if (!log.exists()) {
				if (log.createNewFile()) {
					System.out.println("Created new dispatcher log file: " + log.getAbsolutePath());
				} else {
					System.out.println("Dispatcher log file: " + log.getAbsolutePath());
				}
			}

			// Shell command to go to the dispatcher project's path and run it using cargo
			String shellCommand = String.format("cd '%s' && cargo run --release", pathToDispatcher);

			ProcessBuilder pb;

			if (isWindows()) {
				pb = new ProcessBuilder("wsl", "bash", "-l", "-c", shellCommand);
			} else {
				pb = new ProcessBuilder("bash", "-c", shellCommand);
			}

			pb.redirectError(log).redirectOutput(log).redirectInput(log);
			Process process = pb.start();

			try (RandomAccessFile reader = new RandomAccessFile(log, "r")) {
				reader.seek(log.length());
				System.out.println("Waiting for dispatcher to start");

				while(true) {
					String line = reader.readLine();

					if (line == null) {
						// No new line, wait a bit
						Thread.sleep(500);
						continue;
					}

					if (line.contains("Starting up with config")) {
						System.out.println("Dispatcher has started!");
						break;
					}
				}
			}

			Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));
		} catch (IOException | InterruptedException e) {
			unitLogger.error("Failed to execute the bash command: {}", e.getMessage());
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static File getDispatcherLogFileInScenarioDirectory(String scenarioName) {
		return getRootFolder()
			.resolve("scenarios/bundle")
			.resolve(scenarioName)
			.resolve("dispatcher.log")
			.toFile();
	}

	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}

	public static void createFileWithDistanceInMinutesBetweenStops(String pathToDispatcherWindows, List<BusStop> busStops, RoutingModule routingModule) {
		if (pathToDispatcherWindows.endsWith("/PUT/YOUR/PATH/HERE")) {
			throw new IllegalStateException("Path to dispatcher is not set");
		}

		File file = new File(pathToDispatcherWindows, "distances.txt");

		long start = System.currentTimeMillis();
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
			writeDistanceMatrixHeader(writer, busStops.size());
			writeDistanceMatrixBody(writer, busStops, routingModule);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		long finish = System.currentTimeMillis();
		System.out.printf("Distances file created! Time elapsed: %s ms%n", finish - start);
	}

	private static void writeDistanceMatrixHeader(BufferedWriter writer, int edgesSum) throws IOException {
		// first element is total count of bus stops + 1
		// because Kern is implemented to start from 0 when handling IDs and here they start from 1
		writer.append(String.valueOf(edgesSum + 1)).append("\n");
	}

	private static void writeDistanceMatrixBody(BufferedWriter writer, List<BusStop> busStops, RoutingModule routingModule) throws IOException {
		// Kern takes also stops with id=0, so we should fill the matrix here with an irrelevant value
		for (int i = 0; i < busStops.size() + 1; i++) {
			for (int j = 0; j < busStops.size() + 1; j++) {
				if (i == 0 || j == 0) {
					writer.append("10000");
				}
				else if (i == j) {
					writer.append("0");
				}
				else {
					DistanceBetweenStops distanceBetweenStops = calculateDistanceInMinutesBetweenTwoStops(
						busStops.get(i - 1), busStops.get(j - 1),
						routingModule
					);

					writer.append(String.valueOf(distanceBetweenStops.getDistanceInMinutes()));
				}

				if (j == busStops.size()) {
					writer.append("\n");
					continue;
				}

				writer.append(",");
			}
		}
	}

	public static DistanceBetweenStops calculateDistanceInMinutesBetweenTwoStops(BusStop fromStop, BusStop toStop, RoutingModule routingModule) {
		GeoPoint startPoint = GeoPoint.latLon(fromStop.latitude(), fromStop.longitude());
		GeoPoint finalPoint = GeoPoint.latLon(toStop.latitude(), toStop.longitude());

		RoutingResponse response = routingModule.calculateRoutes(
			new RoutingPosition(startPoint),
			new RoutingPosition(finalPoint),
			new RoutingParameters().vehicleClass(VehicleClass.Taxi));

		return new DistanceBetweenStops(response.getBestRoute().getTime(), response.getBestRoute().getLength());
	}

	public static void getDirectRoutesFromBusStopsToTrainStation(HashMap<String, Integer> directRoutes) {
		File directRoutesCsv = getRootFolder()
			.resolve("scenarios/bundle/pythonScripts/csv/directRoutes.csv")
			.toFile();

		try (BufferedReader br = new BufferedReader(new FileReader(directRoutesCsv))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split(",");
				if (values[0].equals("stop_id")) {
					continue;
				}

				directRoutes.put((values[0]), Integer.parseInt(values[1]));
			}
		}
		catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static Path getRootFolder() {
		return Paths.get(System.getProperty("user.dir")) // -> root/rti/mosaic-starter
			.getParent()  // -> root/rti
			.getParent(); // -> root
	}
}
