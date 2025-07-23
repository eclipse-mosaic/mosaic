package org.eclipse.mosaic.app.taxi.util;

import org.eclipse.mosaic.fed.application.ambassador.util.UnitLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public class ExternalFilesUtil {

	public static void executePythonScripts(UnitLogger unitLogger, boolean shouldIncludeScriptLogs) {
		try {
			// Path to the script directory
			File scriptDir = getFileInScenarioDirectory("pythonScripts");

			// Create the process and set the working directory to the script folder
			ProcessBuilder pb = new ProcessBuilder("python", "executeScripts.py");
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

	// This method currently works only for a Windows system with WSL
	public static void startDispatcher(UnitLogger unitLogger) {
		try {
			File log = getFileInScenarioDirectory("kern_github.log");
			if (!log.exists()) {
				log.createNewFile();
			}

			String wslPath = "/mnt/c/Users/Kotse/VSCodeProjects/kern_Github"; // Something like /mnt/c/....
			// WSL command to go to the project's path and run it using cargo
			String command = String.format("cd %s && cargo run", wslPath);
			ProcessBuilder processBuilder = new ProcessBuilder("wsl", "bash", "-l", "-c", command);
			processBuilder.redirectError(log).redirectOutput(log).redirectInput(log);

			Process process = processBuilder.start();

			try (RandomAccessFile reader = new RandomAccessFile(log, "r")) {
				String line;

				while(true) {
					line = reader.readLine();

					if (line == null) {
						// No new line, wait a bit
						Thread.sleep(500);
						continue;
					}

					String decodedLine = new String(line.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
					System.out.println(decodedLine);
					if (decodedLine.contains("Starting up with config")) {
						reader.close();
						break;
					}
				}
			}

			Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));
		} catch (IOException | InterruptedException e) {
			unitLogger.error("Failed to execute in WSL: {}", e.getMessage());
		}
	}

	private static File getFileInScenarioDirectory(String fileName) {
		return Paths.get(System.getProperty("user.dir")) // -> root/rti/mosaic-starter
			.getParent()  // -> root/rti
			.getParent()  // -> root
			.resolve("scenarios/bundle")
			.resolve("theodorHeuss") // Change this for other scenarios
			.resolve(fileName)
			.toFile();
	}
}
