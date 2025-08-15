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

package org.eclipse.mosaic.lib.objects.v2x;

import org.eclipse.mosaic.lib.util.ClassUtils;
import org.eclipse.mosaic.lib.util.objects.Identifiable;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

/**
 * Base class for any V2X message to be exchanged among simulation entities.
 * Each {@link V2xMessage} requires to return an {@link EncodedPayload} object
 * which holds the actual payload of this message (or just the length which might be
 * sufficient for simulation). Furthermore, each {@link V2xMessage} requires a
 * {@link MessageRouting} configuration which provides information about the
 * communication path and addressing mode to use for simulating the transmission
 * of this {@link V2xMessage}.
 */
public abstract class V2xMessage implements Serializable, Identifiable {
    
    private static final long serialVersionUID = 1L;
    
    private final static AtomicInteger idGenerator = new AtomicInteger();

    /**
     * The routing configuration of this {@link V2xMessage}.
     */
    private final MessageRouting routing;

    /**
     * The globally unique Id of this {@link V2xMessage}.
     */
    private final int id;

    private Integer sequenceNumber;

    /**
     * Creates a new V2X message. The Id of this message will be generated.
     *
     * @param routing the routing configuration of this {@link V2xMessage}.
     */
    public V2xMessage(final MessageRouting routing) {
        this.routing = Objects.requireNonNull(routing);
        id = idGenerator.getAndIncrement();
    }

    /**
     * Creates a new V2X message.
     *
     * @param routing the routing configuration of this {@link V2xMessage}.
     * @param id the id of this message
     */
    protected V2xMessage(final MessageRouting routing, final int id) {
        this.routing = Objects.requireNonNull(routing);
        this.id = id;
    }

    /**
     * Returns the {@link EncodedPayload}.
     *
     * @return the {@link EncodedPayload} of this message
     */
    @Nonnull
    public abstract EncodedPayload getPayload();
    
    /**
     * Returns the globally unique id of this message.
     *
     * @return the unique id of this message.
     */
    public int getId() {
        return id;
    }
    
    /**
     * Returns the (sender-wise) unique sequence number of this message.
     *
     * @return the unique sequence number of this message.
     */
    public int getSequenceNumber() {
        return ObjectUtils.defaultIfNull(sequenceNumber, -1);
    }
    
    /**
     * Sets the unique sequence number of this message. May only be set once.
     *
     * @throws IllegalStateException if the sequenceNumber has already been set.
     */
    public void setSequenceNumber(int sequenceNumber) throws IllegalStateException {
        if (this.sequenceNumber != null) {
            throw new IllegalStateException("Sequence number of V2XMessage with id=" + getId() + " has already been set.");
        }
        this.sequenceNumber = sequenceNumber;
    }

    @Nonnull
    public MessageRouting getRouting() {
        return routing;
    }

    /**
     * Returns the simple class name of the implementation. Please prefer this method instead 
     * to get the name from {@link #getSimpleClassName()}.
     *
     * @return the simple class name of the implementation.
     */
    @Nonnull
    public String getSimpleClassName() {
        // getClass().getSimpleName() has performance issues: https://bugs.openjdk.java.net/browse/JDK-8187123
        // we use our own helper method here instead
        return ClassUtils.createShortClassName(getClass());
    }

    /**
     * Do not overwrite, compares only the id.
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(3, 67)
                        .append(id)
                        .toHashCode();
    }

    /**
     * Do not overwrite, compares only the id.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false; 
        if (obj == this) return true;
        if (obj.getClass() != getClass()) return false;
        
        V2xMessage other = (V2xMessage) obj;
        return new EqualsBuilder()
                        .append(this.id, other.id)
                        .isEquals();
    }
    
    @Override
    public String toString() {
        return "V2XMessage{" + "routing=" + routing + ", id=" + id + '}';
    }

    /**
     * Empty message for communication testing purposes.
     */
    public static class Empty extends V2xMessage {

        public Empty(MessageRouting routing) {
            super(routing);
        }

        @Nonnull
        @Override
        public EncodedPayload getPayload() {
            return EncodedPayload.EMPTY_PAYLOAD;
        }
    }

    /**
     * Simple message with string payload for communication testing purposes.
     */
    public static class Simple extends V2xMessage {

        private final EncodedPayload payload;

        public Simple(String message, MessageRouting routing) {
            super(routing);
            this.payload = new EncodedPayload(message.getBytes(StandardCharsets.UTF_8), message.length());
        }

        @Nonnull
        @Override
        public EncodedPayload getPayload() {
            return payload;
        }
    }

}

