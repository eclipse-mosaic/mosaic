package org.eclipse.mosaic.app.taxi.util;

import org.eclipse.mosaic.fed.application.ambassador.util.UnitLogger;

import java.io.*;
import java.nio.file.Paths;

public class ExternalFilesUtil {

	public static void executePythonScripts(UnitLogger unitLogger, boolean shouldIncludeScriptLogs, String scenarioName) {
		try {
			// Path to the script directory
			File scriptDir = Paths.get(System.getProperty("user.dir")) // -> root/rti/mosaic-starter
				.getParent()  // -> root/rti
				.getParent()  // -> root
				.resolve("scenarios/bundle/pythonScripts")
				.toFile();

			// Create the process and set the working directory to the script folder
			String pythonCmd = isWindows() ? "python" : "python3";
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
		return Paths.get(System.getProperty("user.dir")) // -> root/rti/mosaic-starter
			.getParent()  // -> root/rti
			.getParent()  // -> root
			.resolve("scenarios/bundle")
			.resolve(scenarioName) // Change this for other scenarios
			.resolve("dispatcher.log")
			.toFile();
	}

	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}
}
