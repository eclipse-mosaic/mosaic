/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.starter.cli;

import org.eclipse.mosaic.lib.util.cli.ArgumentsOptionsParser;
import org.eclipse.mosaic.lib.util.cli.CliOption;

import java.io.Serializable;

/**
 * This class holds the values of options parsed by the {@link ArgumentsOptionsParser}. Also, all
 * options are described here using the {@link CliOption} annotation.
 */
public class MosaicParameters implements Serializable {

    private static final long serialVersionUID = -1;

    @CliOption(shortOption = "c", longOption = "config", argumentHint = "PATH", description = "Path to MOSAIC scenario configuration file (scenario_config.json). Can be used instead of \"-s\" parameter. (mandatory).", group = "config", isRequired = true)
    public String configurationPath = null;

    @CliOption(shortOption = "s", longOption = "scenario", argumentHint = "NAME", description = "The name of the MOSAIC scenario. Can be used instead of \"-c\" parameter. (mandatory)", group = "config", isRequired = true)
    public String scenarioName = null;

    @CliOption(shortOption = "w", longOption = "watchdog-interval", argumentHint = "SECONDS", description = "Kill MOSAIC process after n seconds if a federate is not responding. 0 disables the watchdog. (default: 30)")
    public int watchdogInterval = 30;

    @CliOption(shortOption = "r", longOption = "random-seed", argumentHint = "SEED", description = "Overrides the random seed which is given in the scenario configuration file.")
    public Long randomSeed = null;

    @CliOption(shortOption = "v", longOption = "start-visualizer", description = "Opens the 2D web visualizer in the default browser.")
    public boolean startVisualizer = false;

    @CliOption(shortOption = "b", longOption = "realtime-brake", argumentHint = "FACTOR", description = "Use this parameter to slow down simulation execution if it's too fast. A value of '1' slows down to realtime, a value of '5' slows down to 5 times as fast as realtime.")
    public Double realtimeBrake = null;

    @CliOption(shortOption = "o", longOption = "log-level", argumentHint = "LOGLEVEL", description = "Overrides the overall log level to the provided value (e.g., TRACE, DEBUG, INFO, WARN, ERROR, OFF)")
    public String logLevel = null;

    @CliOption(longOption = "runtime", argumentHint = "PATH", description = "Path to MOSAIC RTI configuration file (default: etc/runtime.json)")
    public String runtimeConfiguration = null;

    @CliOption(longOption = "hosts", argumentHint = "PATH", description = "Path to host configuration file (default: etc/hosts.json)")
    public String hostsConfiguration = null;

    @CliOption(shortOption = "l", longOption = "logger", argumentHint = "PATH", description = "Path to logback configuration file (default: etc/logback.xml)")
    public String loggerConfiguration = null;

    @CliOption(shortOption = "e", longOption = "external-watchdog", argumentHint = "PORT", description = "Specific external watchdog port number")
    public Integer externalWatchDog = null;

}

