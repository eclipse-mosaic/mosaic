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

package org.eclipse.mosaic.interactions.application;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import org.eclipse.mosaic.rti.api.Interaction;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serial;

/**
 * Interaction to enable object context subscription specifically for SUMO.<br><br>
 *
 * This is currently as an alternative to our perception index, which requires
 * all vehicles of the simulation to be subscribed. Using the SumoPerception instead
 * works by utilizing SUMO's Object Context Subscriptions mechanism. In that case, a list
 * of surrounding entities (currently only vehicles) is returned for each subscribed
 * entity. This list is directly used in mosaic-application instead of its own Spatial Index.
 */
public class SumoSurroundingObjectsSubscription extends Interaction {

    @Serial
    private static final long serialVersionUID = 1L;

    public final static String TYPE_ID = createTypeIdentifier(SumoSurroundingObjectsSubscription.class);

    /**
     * The unit's for which the surrounding objects should be subscribed.
     */
    private final String unitId;

    /**
     * The range limit to retrieve surrounding objects.
     */
    private final double range;

    /**
     * The opening angle of the sight area of the unit.
     */
    private final double fieldOfView;

    /**
     * Creates an interaction that includes the unit name, its subscription range (meters) and field of view (degrees).
     *
     * @param time        Timestamp of this interaction, unit: [ns]
     * @param unitId      unit identifier
     * @param range       The range limit to retrieve surrounding objects. unit: [m]
     * @param fieldOfView The opening angle of the sight area of the unit. unit: [deg]
     */
    public SumoSurroundingObjectsSubscription(long time, String unitId, double range, double fieldOfView) {
        super(time);
        this.unitId = unitId;
        this.range = range;
        this.fieldOfView = fieldOfView;
    }

    public String getUnitId() {
        return unitId;
    }

    public double getRange() {
        return range;
    }

    public double getFieldOfView() {
        return fieldOfView;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(13, 27)
                .append(unitId)
                .append(range)
                .append(fieldOfView)
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

        SumoSurroundingObjectsSubscription other = (SumoSurroundingObjectsSubscription) obj;
        return new EqualsBuilder()
                .append(this.unitId, other.unitId)
                .append(this.range, other.range)
                .append(this.fieldOfView, other.fieldOfView)
                .isEquals();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("unitId", unitId)
                .append("range", range)
                .append("fieldOfView", fieldOfView)
                .toString();
    }
}
