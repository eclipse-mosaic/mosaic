/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.lib.objects.environment;

import org.eclipse.mosaic.lib.geo.GeoArea;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;
import javax.annotation.Nonnull;

/**
 * Holds the Environment location. Can be used to visualize the areas in which certain events are configured.
 */
public class EnvironmentEventLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The geographic area (e.g. Circle) the event is located in.
     */
    private final GeoArea area;

    /**
     * The type of sensor which is sensing values in the event area.
     * Should correspond to the name of an existing {@link Sensor}.
     */
    private final String eventType;

    /**
     * Creates a new environment event location. The event type should correspond to the name of
     * an existing {@link Sensor}.
     *
     * @param eventArea the geographic area in which the event occurs
     * @param eventType event type
     */
    public EnvironmentEventLocation(@Nonnull GeoArea eventArea, @Nonnull String eventType) {
        this.area = eventArea;
        this.eventType = eventType;
    }

    @Nonnull
    public GeoArea getArea() {
        return area;
    }

    @Nonnull
    public String getEventType() {
        return eventType;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 37)
                .append(eventType)
                .append(area)
                .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        EnvironmentEventLocation rhs = (EnvironmentEventLocation) obj;
        return new EqualsBuilder()
                .append(this.eventType, rhs.eventType)
                .append(this.area, rhs.area)
                .isEquals();
    }

    @Override
    public String toString() {
        return "EnvironmentEventLocation{" + "eventType=" + eventType + ", area=" + area + '}';
    }
}
