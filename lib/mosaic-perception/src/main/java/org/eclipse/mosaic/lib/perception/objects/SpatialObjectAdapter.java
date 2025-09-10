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

package org.eclipse.mosaic.lib.perception.objects;

import org.eclipse.mosaic.lib.spatial.SpatialItemAdapter;

public class SpatialObjectAdapter<T extends SpatialObject<?>> implements SpatialItemAdapter<T> {

    @Override
    public int getItemHash(T item) {
        return item.getId().hashCode();
    }

    @Override
    public double getCenterX(T item) {
        return item.getProjectedPosition().getX();
    }

    @Override
    public double getCenterY(T item) {
        return item.getProjectedPosition().getZ();
    }

    @Override
    public double getCenterZ(T item) {
        return -item.getProjectedPosition().getY();
    }

    @Override
    public double getMinX(T item) {
        return getCenterX(item);
    }

    @Override
    public double getMinY(T item) {
        return getCenterY(item);
    }

    @Override
    public double getMinZ(T item) {
        return getCenterZ(item);
    }
}
