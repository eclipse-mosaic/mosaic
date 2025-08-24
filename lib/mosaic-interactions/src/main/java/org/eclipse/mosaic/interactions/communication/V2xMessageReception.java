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

import org.eclipse.mosaic.lib.objects.v2x.V2xReceiverInformation;
import org.eclipse.mosaic.rti.api.Interaction;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * This extension of {@link Interaction} is intended to be used to
 * exchange information about a received V2X message.
 */
public class V2xMessageReception extends Interaction {

    private static final long serialVersionUID = 1L;

    /**
     * String identifying the type of this interaction.
     */
    public final static String TYPE_ID = createTypeIdentifier(V2xMessageReception.class);

    /**
     * String identifying the receiving node.
     */
    private final String receiverName;

    /**
     * Int identifying the V2X messages.
     */
    private final int messageId;

    /**
     * Additional receiver information. <code>null</code> if no additional
     * information given.
     */
    @Nonnull
    private final V2xReceiverInformation receiverInformation;

    /**
     * Constructor using fields.
     *
     * @param time                Timestamp of this interaction, unit: [ns]
     * @param receiverName        Identifies the receiving node.
     * @param msgId               Identifies the V2X message.
     * @param receiverInformation Additional receiver information.
     *                            {@code null} if no additional information given.
     */
    public V2xMessageReception(final long time, @Nonnull final String receiverName, final int msgId,
                               final V2xReceiverInformation receiverInformation) {
        super(time);
        this.messageId = msgId;
        this.receiverName = Objects.requireNonNull(receiverName);
        this.receiverInformation = receiverInformation;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public int getMessageId() {
        return messageId;
    }

    @Nonnull
    public V2xReceiverInformation getReceiverInformation() {
        return receiverInformation;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 53)
                .append(receiverName)
                .append(messageId)
                .append(receiverInformation)
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

        V2xMessageReception other = (V2xMessageReception) obj;
        return new EqualsBuilder()
                .append(this.receiverName, other.receiverName)
                .append(this.messageId, other.messageId)
                .append(this.receiverInformation, other.receiverInformation)
                .isEquals();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("receiverName", receiverName)
                .append("messageId", messageId)
                .append("receiverInformation", receiverInformation)
                .toString();
    }
}
