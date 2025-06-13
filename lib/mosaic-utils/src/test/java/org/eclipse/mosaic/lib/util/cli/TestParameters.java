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

package org.eclipse.mosaic.lib.util.cli;

import java.io.Serializable;

public class TestParameters implements Serializable {
    private static final long serialVersionUID = 1L;

    @CliOption(shortOption = "c", longOption = "config", valueHint = "PATH", description = "...", group = "config")
    public String configurationPath = null;

    @CliOption(shortOption = "s", longOption = "scenario", valueHint = "NAME", description = "...", group = "config")
    public String scenarioName = null;

    @CliOption(shortOption = "w", longOption = "watchdog-interval", valueHint = "SECONDS", description = "...")
    public int watchdogInterval = -1;

    @CliOption(shortOption = "v", longOption = "start-visualizer", description = "...")
    public boolean startVisualizer = false;

    @CliOption(shortOption = "u", longOption = "user", valueHint = "USERID", description = "...", isRequired = true)
    public String userid = null;

}