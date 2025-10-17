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

package org.eclipse.mosaic.fed.application.ambassador.simulation.electric;

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.electricity.ChargingStationData;
import org.eclipse.mosaic.lib.perception.objects.PointBoundingBox;
import org.eclipse.mosaic.lib.perception.objects.SpatialObject;
import org.eclipse.mosaic.lib.perception.objects.SpatialObjectBoundingBox;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ChargingStationObject extends SpatialObject<ChargingStationObject> {
    private static final long serialVersionUID = 1L;
    /**
     * The data object that stores all static and dynamic information of the charging station.
     */
    private ChargingStationData chargingStationData;

    /**
     * The bounding box of a charging station is represented by a single point.
     */
    private transient PointBoundingBox boundingBox;

    public ChargingStationObject(String id) {
        super(id);
    }

    public ChargingStationObject setChargingStationData(ChargingStationData chargingStationData) {
        this.chargingStationData = chargingStationData;
        return this;
    }

    public ChargingStationData getChargingStationData() {
        return chargingStationData;
    }

    @Override
    public ChargingStationObject setPosition(CartesianPoint position) {
        cartesianPosition.set(position);
        position.toVector3d(this);
        return this;
    }

    @Override
    public SpatialObjectBoundingBox getBoundingBox() {
        if (boundingBox == null) {
            boundingBox = new PointBoundingBox(this);
        }
        return boundingBox;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChargingStationObject that = (ChargingStationObject) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(chargingStationData, that.chargingStationData)
                .append(boundingBox, that.boundingBox)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(5, 11)
                .appendSuper(super.hashCode())
                .append(chargingStationData)
                .toHashCode();
    }

    /**
     * Returns a hard copy of the {@link ChargingStationObject}, this should be used
     * when the data of a perceived traffic light is to be altered or stored in memory.
     *
     * @return a copy of the {@link ChargingStationObject}
     */
    @Override
    public ChargingStationObject copy() {
        return new ChargingStationObject(getId())
                .setChargingStationData(chargingStationData)
                .setPosition(getProjectedPosition());
    }
}
