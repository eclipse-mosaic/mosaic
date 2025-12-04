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
import java.util.List;

/**
 * Provides information for assigning taxi reservations to a taxi vehicle.
 */
public class TaxiDispatch extends Interaction {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String TYPE_ID = createTypeIdentifier(TaxiDispatch.class);

    private final String taxiId;
    private final List<String> reservations;

    public TaxiDispatch(long time, String taxiId, List<String> reservations) {
        super(time);
        this.taxiId = taxiId;
        this.reservations = reservations;
    }

    /**
     * Getter for the vehicle identifier.
     *
     * @return String identifying the vehicle sending this interaction
     */
    public String getTaxiId() {
        return taxiId;
    }

    /**
     * The list of reservation IDs to be handled by the taxi in the given order.
     */
    public List<String> getReservations() {
        return reservations;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(5, 67)
                .append(taxiId)
                .append(reservations)
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

        TaxiDispatch other = (TaxiDispatch) obj;
        return new EqualsBuilder()
                .append(this.taxiId, other.taxiId)
                .append(this.reservations, other.reservations)
                .isEquals();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("taxiId", taxiId)
                .append("reservations", reservations)
                .toString();
    }
}
