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

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.MutableCartesianPoint;
import org.eclipse.mosaic.lib.math.Vector3d;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public abstract class SpatialObject<T extends SpatialObject<T>> extends Vector3d {

    private final String id;

    protected final MutableCartesianPoint cartesianPosition = new MutableCartesianPoint();

    public SpatialObject(String id) {
        this.id = id;
    }

    /**
     * Sets the position of the {@link SpatialObject}.
     *
     * @param x x-coordinate
     * @param y y-coordinate
     * @param z z-coordinate
     */
    public void setPosition(double x, double y, double z) {
        this.set(x, y, z);
        cartesianPosition.set(this.toCartesian());
    }

    public abstract T setPosition(CartesianPoint position);

    public abstract SpatialObjectBoundingBox getBoundingBox();

    /**
     * Returns the unique identifier of this spatial object.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the projected position on the X,Y-plane of this spatial object.
     */
    public CartesianPoint getProjectedPosition() {
        return cartesianPosition;
    }

    /**
     * Returns the position as a {@link Vector3d}.
     */
    public Vector3d getPosition() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SpatialObject<?> that = (SpatialObject<?>) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(id, that.id)
                .append(cartesianPosition, that.cartesianPosition)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(5, 11)
                .appendSuper(super.hashCode())
                .append(id)
                .append(cartesianPosition)
                .toHashCode();
    }

    /**
     * Returns a hard copy of the {@link SpatialObject}, this should be used
     * when the data of a perceived object is to be altered or stored in memory.
     *
     * @return a copy of the {@link SpatialObject}
     */
    public abstract T copy();
}
