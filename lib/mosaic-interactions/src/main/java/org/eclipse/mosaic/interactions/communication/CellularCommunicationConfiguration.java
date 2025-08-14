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

package org.eclipse.mosaic.interactions.communication;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import org.eclipse.mosaic.lib.objects.communication.CellConfiguration;
import org.eclipse.mosaic.rti.api.Interaction;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.annotation.concurrent.Immutable;

/**
 * This extension of {@link Interaction} is intended to be used to
 * configure cell communication of a node, e.g. enabling or disabling
 * communication via cellular networks.
 */
@Immutable
public final class CellularCommunicationConfiguration extends CommunicationConfiguration {

    private static final long serialVersionUID = 1L;

    /**
     * String identifying the type of this interaction.
     */
    public final static String TYPE_ID = createTypeIdentifier(CellularCommunicationConfiguration.class);

    private final CellConfiguration configuration;

    /**
     * Constructor using fields.
     *
     * @param time          Timestamp of this interaction, unit: [ns]
     * @param configuration the specified configuration of the cellular communication modules
     */
    public CellularCommunicationConfiguration(long time, final CellConfiguration configuration) {
        super(time);
        this.configuration = configuration;
    }

    public CellConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 17)
                .append(getConfiguration())
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

        CellularCommunicationConfiguration other = (CellularCommunicationConfiguration) obj;
        return new EqualsBuilder()
                .append(this.getConfiguration(), other.getConfiguration())
                .isEquals();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("configuration", getConfiguration())
                .toString();
    }
}