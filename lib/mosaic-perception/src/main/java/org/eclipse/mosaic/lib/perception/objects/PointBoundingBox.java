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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

/**
 * This bounding box can be used for objects that should be treated to have a single-point position and not span into any dimensions.
 */
public class PointBoundingBox implements SpatialObjectBoundingBox {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * A singleton list, defining the position of a {@link SpatialObject} as the only corner.
     * This point is in global coordinates.
     */
    private final List<Vector3d> allCorners;
    /**
     * An empty list as a no bounding box is spanned by a single point.
     */
    private final List<Edge<Vector3d>> allEdges;

    public PointBoundingBox(Vector3d position) {
        allCorners = Collections.singletonList(position);
        allEdges = Collections.emptyList();
    }

    @Override
    public List<Vector3d> getAllCorners() {
        return allCorners;
    }

    @Override
    public List<Edge<Vector3d>> getAllEdges() {
        return allEdges;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PointBoundingBox that = (PointBoundingBox) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(allCorners, that.allCorners)
                .append(allEdges, that.allEdges)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(5, 11)
                .appendSuper(super.hashCode())
                .append(allCorners)
                .append(allEdges)
                .toHashCode();
    }

}
