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

package org.eclipse.mosaic.lib.perception.objects;

import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.spatial.Edge;

import java.io.Serializable;
import java.util.List;

/**
 * This interface shall be implemented by all classes representing the bounding box of a {@link SpatialObject}.
 * All points are returned in the global coordinate system as {@link Vector3d}.
 */
public interface SpatialObjectBoundingBox extends Serializable {

    /**
     * Returns all corners spanning the 2D bounding box of a {@link SpatialObject} as global coordinates.
     */
    List<Vector3d> getAllCorners();

    /**
     * Returns all sides spanning the 2D bounding box of a {@link SpatialObject} as {@link Edge edges} in the global
     * coordinate system.
     */
    List<Edge<Vector3d>> getAllEdges();
}
