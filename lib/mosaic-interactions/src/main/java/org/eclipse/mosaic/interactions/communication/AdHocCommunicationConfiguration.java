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

import org.eclipse.mosaic.lib.objects.communication.AdHocConfiguration;
import org.eclipse.mosaic.rti.api.Interaction;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * This extension of {@link Interaction} is intended to be used to
 * exchange information about the configuration of a vehicle's AdHoc communication
 * facilities.
 */
@Immutable
public final class AdHocCommunicationConfiguration extends CommunicationConfiguration {

    private final static long serialVersionUID = 1L;

    /**
     * String identifying the type of this interaction.
     */
    public final static String TYPE_ID = createTypeIdentifier(AdHocCommunicationConfiguration.class);

    private final AdHocConfiguration configuration;

    /**
     * Constructor using fields.
     *
     * @param time          Timestamp of this interaction, unit: [ns]
     * @param configuration the configuration object containing the actual parameters
     */
    public AdHocCommunicationConfiguration(long time, AdHocConfiguration configuration) {
        super(time);
        this.configuration = Objects.requireNonNull(configuration);
    }

    public AdHocConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 17)
                .append(configuration)
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

        AdHocCommunicationConfiguration other = (AdHocCommunicationConfiguration) obj;
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