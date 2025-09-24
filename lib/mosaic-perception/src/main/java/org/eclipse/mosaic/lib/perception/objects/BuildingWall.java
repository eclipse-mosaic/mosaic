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

package org.eclipse.mosaic.lib.perception.objects;

import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.spatial.Edge;

/**
 * A building wall consists of two corners given in {@link Vector3d}
 * coordinates. Extends {@link Edge<Vector3d>} for readability
 * purposes.
 */
public class BuildingWall extends Edge<Vector3d> {

    public BuildingWall(Vector3d cornerA, Vector3d cornerB) {
        super(cornerA, cornerB);
    }
}
