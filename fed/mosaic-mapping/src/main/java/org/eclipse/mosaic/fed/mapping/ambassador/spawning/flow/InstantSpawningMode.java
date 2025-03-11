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

package org.eclipse.mosaic.fed.mapping.ambassador.spawning.flow;

public class InstantSpawningMode implements SpawningMode {
    private final long startingTime;

    public InstantSpawningMode(long startingTime) {
        this.startingTime = startingTime;
    }

    @Override
    public boolean isSpawningActive(long currentTime) {
        return currentTime >= startingTime;
    }

    @Override
    public long getNextSpawningTime(long currentTime) {
        return Math.max(startingTime, currentTime);
    }
}
