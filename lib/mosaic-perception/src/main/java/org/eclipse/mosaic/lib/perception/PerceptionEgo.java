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

package org.eclipse.mosaic.lib.perception;

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.perception.objects.BuildingWall;

import java.util.Collection;

/**
 * Interface providing access to methods required by the {@link PerceptionModel} and
 * {@link PerceptionModifier}s.
 */
public interface PerceptionEgo {

    /**
     * Returns the unit ID of this perceiving ego entity.
     */
    String getId();

    /**
     * Returns the projected position in cartesian coordinates of this perceiving ego entity.
     */
    CartesianPoint getProjectedPosition();

    /**
     * Returns the heading in degrees of this perceiving ego entity.
     */
    double getHeading();

    /**
     * Returns the maximum viewing range of this perceiving ego entity.
     */
    double getViewingRange();

    /**
     * Returns a list of walls in vicinity of this perceiving ego entity.
     */
    Collection<BuildingWall> getSurroundingWalls();
}
