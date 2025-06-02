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

package org.eclipse.mosaic.lib.util.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.ParseException;
import org.junit.Test;

import java.io.File;

public class CommandParserTest {

    @Test
    public void knownCommand_everythingFine() throws ParseException {
        String args[] = {
                "test", "command", "firstValue", "path/to/second",
                "-u", "theUser",
                "-w", "30"
        };

        //RUN
        Runnable selectedCommand = new CommandParser(new TestCommand()).parseArguments(args);

        assertTrue(selectedCommand instanceof TestCommand);

        //ASSERT
        TestCommand command = (TestCommand) selectedCommand;

        assertEquals("firstValue", command.first);
        assertEquals(new File("path/to/second"), command.second);

        assertEquals("theUser", command.userid);
        assertEquals(30, command.watchdogInterval);

        assertNull(command.scenarioName);
        assertNull(command.configurationPath);
        assertFalse(command.startVisualizer);
    }

    @Test
    public void knownCommand_missingArgument() throws ParseException {
        String args[] = {
                "test", "command", "firstValue",
                "-u", "theUser",
                "-w", "30"
        };

        //RUN
        try {
            new CommandParser(new TestCommand()).parseArguments(args);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Missing argument: "));
        }
    }

    @Test
    public void knownCommand_missingParameter() throws ParseException {
        String args[] = {
                "test", "command", "firstValue", "path/to/second",
                "-w", "30"
        };

        //RUN
        try {
            new CommandParser(new TestCommand()).parseArguments(args);
            fail();
        } catch (MissingOptionException e) {
            assertTrue(e.getMessage().contains("Missing required option: u" ));
        }
    }

    @Test
    public void unknownCommand() throws ParseException {
        String args[] = {
                "test",
                "-u", "theUser",
                "-w", "30"
        };

        //RUN
        try {
            new CommandParser(new TestCommand()).parseArguments(args);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown command: test"));
        }
    }

}