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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;
import javax.annotation.Nonnull;

public final class EnvironmentEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public final String type;
    public final Object value;
    public final long from;
    public final long until;

    /**
     * Creates a new environment event. The event type should correspond to the name of
     * an existing {@link Sensor}.
     *
     * @param type  event type
     * @param value event type value
     * @param from  beginning time of the event
     * @param until ending time of the event
     */
    public EnvironmentEvent(@Nonnull String type, Object value, long from, long until) {
        this.type = type;
        this.value = value;
        this.from = from;
        this.until = until;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 37)
                .append(type)
                .append(value)
                .append(from)
                .append(until)
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

        EnvironmentEvent rhs = (EnvironmentEvent) obj;
        return new EqualsBuilder()
                .append(this.type, rhs.value)
                .append(this.value, rhs.value)
                .append(this.from, rhs.from)
                .append(this.until, rhs.until)
                .isEquals();
    }

    @Override
    public String toString() {
        return "EnvironmentEvent{" + "type=" + type + ", value=" + value + ", from=" + from + ", until=" + until + '}';
    }
}
