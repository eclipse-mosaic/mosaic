/*
 * Copyright (c) 2023 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.providers;

import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.PerceptionModel;
import org.eclipse.mosaic.lib.database.Database;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.spatial.Edge;

import java.util.Collection;

public abstract class WallIndex {

    private Database database = null;

    public abstract void initialize();

    public abstract Collection<Edge<Vector3d>> getSurroundingWalls(PerceptionModel perceptionModel);

    public void setDatabase(Database database) {
        if (this.database != null) {
            return;
        }
        this.database = database;
    }

    protected Database getDatabase() {
        return database;
    }
}
