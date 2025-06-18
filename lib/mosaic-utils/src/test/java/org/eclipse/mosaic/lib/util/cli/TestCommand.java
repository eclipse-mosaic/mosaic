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

import java.io.File;

@CliCommand(command = "test command", description = "")
public class TestCommand extends TestParameters implements Runnable {

    @CliArgument(argumentHint = "SECOND", index = 1, description = "")
    File second;

    @CliArgument(argumentHint = "FIRST", index = 0, description = "")
    String first;

    @Override
    public void run() {

    }
}
