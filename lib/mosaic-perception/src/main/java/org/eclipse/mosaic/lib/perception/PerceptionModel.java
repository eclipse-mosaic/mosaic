/*
 * Copyright (c) 2022 Fraunhofer FOKUS and others. All rights reserved.
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

import org.eclipse.mosaic.lib.perception.objects.SpatialObject;
import org.eclipse.mosaic.lib.spatial.BoundingBox;

/**
 * The {@link PerceptionModel} calculates for a given {@link SpatialObject}, if it is in
 * viewing range from the perspective of the perceiving ego entity.
 */
public interface PerceptionModel {

    /**
     * Checks, if the other spatial object is within this perception range.
     */
    boolean isInRange(SpatialObject<?> other);

    /**
     * Returns the minimum bounding box around this perception area. This is used for range search queries in the perception index.
     */
    BoundingBox getBoundingBox();
}
