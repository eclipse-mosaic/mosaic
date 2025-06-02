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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.commons.cli.ParseException;
import org.junit.Test;

import java.util.List;

public class ParameterParserTest {

    @Test
    public void parseCLI() throws ParseException {
        String args[] = {
                "-u", "myUserId",
                "--config", "path/to/config",
                "-w" ,"0",
                "--start-visualizer"
        };

        //RUN
        final TestParameters params = new ParameterParser<>(TestParameters.class).parseArguments(args, new TestParameters());

        //ASSERT
        assertEquals("myUserId", params.userid);
        assertEquals("path/to/config", params.configurationPath);
        assertEquals(0, params.watchdogInterval);
        assertTrue(params.startVisualizer);

        assertNull(params.scenarioName);
    }

    @Test
    public void transformToArguments() {
        final TestParameters parameters = new TestParameters();
        parameters.configurationPath = "path/to/config";
        parameters.watchdogInterval = 30;
        parameters.startVisualizer = true;

        //RUN
        List<String> arguments = new ParameterParser<>(TestParameters.class).transformToArguments(parameters);

        //ASSERT
        assertEquals("--config", arguments.get(0));
        assertEquals("path/to/config", arguments.get(1));
        assertEquals("--watchdog-interval", arguments.get(2));
        assertEquals("30", arguments.get(3));
        assertEquals("--start-visualizer", arguments.get(4));
    }

}