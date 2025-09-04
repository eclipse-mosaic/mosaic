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

import org.eclipse.mosaic.lib.objects.ToDataOutput;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings(value = "EI_EXPOSE_REP2", justification = "Exposing encoded array is not dangerous here.")
public final class EncodedPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    public final static EncodedPayload EMPTY_PAYLOAD = new EncodedPayload(0L);

    private final String contentClassName;

    /**
     * Contains the actual message payload, which can be empty if not needed.
     */
    private final byte[] bytes;

    /**
     * The actual length of this message payload [in bytes].
     */
    private final long lengthInBytes;

    /**
     * The minimal assumed length of this message payload [in bytes].
     */
    private final long minimalLength;

    /**
     * Creates a new payload which encodes the given {@link ToDataOutput} into an byte array beforehand.
     * <b>Warning,</b> this constructor should be only used if you want to
     * evaluate the content of the message in other simulators outside of the
     * application simulator. Please prefer the method
     * {@link EncodedPayload#EncodedPayload(long, long)} if the evaluate is not needed.
     * <b>This method can cause a high memory consumption.</b>
     */
    public EncodedPayload(@Nonnull ToDataOutput content, long minimalLength) {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream(1024); final DataOutputStream dos = new DataOutputStream(baos)) {
            content.toDataOutput(dos);

            this.bytes = Objects.requireNonNull(baos.toByteArray());
            this.contentClassName = content.getClass().getCanonicalName();
            this.lengthInBytes = bytes.length;
            this.minimalLength = minimalLength;

        } catch (Exception e) {
            throw new IllegalArgumentException("Could not decode class", e);
        }
    }

    /**
     * Creates a new encoded message using the size of the payload length.
     *
     * @param lengthInBytes Unit: [bytes].
     */
    public EncodedPayload(long lengthInBytes) {
        this.bytes = null;
        this.contentClassName = null;
        this.lengthInBytes = lengthInBytes;
        this.minimalLength = 0;
    }

    /**
     * Creates a new payload using the full byte array with an minimal payload length.
     * <b>Warning,</b> this constructor should be only used if you want to
     * evaluate the content of the message in other simulators, outside of the
     * application simulator. Please prefer the method
     * {@link EncodedPayload#EncodedPayload(long, long)} if the evaluate is
     * not needed.
     * <b>This method can cause a high memory consumption.</b>
     *
     * @param bytes         the message payload.
     * @param minimalLength Unit: [bytes].
     */
    public EncodedPayload(@Nonnull byte[] bytes, long minimalLength) {
        this.bytes = Objects.requireNonNull(bytes);
        this.contentClassName = null;
        this.lengthInBytes = bytes.length;
        this.minimalLength = minimalLength;
    }

    /**
     * Creates a new encoded message using the size of the payload length with
     * an minimal payload length.
     *
     * @param lengthInBytes the actual length of the message's payload. Unit: [bytes].
     * @param minimalLength the minimal assumed length of the message's payload. Unit: [bytes].
     */
    public EncodedPayload(long lengthInBytes, long minimalLength) {
        this.bytes = null;
        this.contentClassName = null;
        this.lengthInBytes = lengthInBytes;
        this.minimalLength = minimalLength;
    }

    /**
     * Return a copy of the byte array, if a byte array exist.
     *
     * @return the encoded bytes of the payload
     */
    @Nonnull
    public byte[] getBytes() {
        if (bytes == null) {
            return new byte[0];
        }
        return bytes.clone();
    }

    /**
     * Returns the length of the message. Please prefer this method instead
     * to get the length from {@link EncodedPayload#getBytes()}.
     * Unit: [bytes].
     */
    public long getActualLength() {
        return lengthInBytes;
    }

    /**
     * Returns the minimum message length. Unit: [bytes].
     */
    public long getMinimalLength() {
        return minimalLength;
    }

    /**
     * Returns the effective length. Use this method for all calculations
     * which consider the physical message length. Unit: [bytes].
     */
    public long getEffectiveLength() {
        return Math.max(lengthInBytes, minimalLength);
    }

    /**
     * Tries to decode the payload byte array into the original object. This
     * only works if this object has been initialized with a implementation of {@link ToDataOutput}.
     *
     * @param <T> the object type to decode the byte array to
     * @return the decoded object of type {@code T}.
     * @throws IllegalStateException if something went wrong during decoding
     */
    @SuppressWarnings("unchecked")
    public final <T extends ToDataOutput> T decodePayload() throws IllegalStateException {
        if (contentClassName != null && getBytes() != null && getBytes().length > 0) {
            try {
                Class<?> contentClass = Class.forName(contentClassName);
                Constructor<?> constructorDataInput = contentClass.getConstructor(DataInput.class);
                return (T) constructorDataInput.newInstance(new DataInputStream(new ByteArrayInputStream(getBytes())));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(3, 67)
                .append(bytes)
                .append(contentClassName)
                .append(lengthInBytes)
                .append(minimalLength)
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

        EncodedPayload other = (EncodedPayload) obj;
        return new EqualsBuilder()
                .append(this.bytes, other.bytes)
                .append(this.contentClassName, other.contentClassName)
                .append(this.lengthInBytes, other.lengthInBytes)
                .append(this.minimalLength, other.minimalLength)
                .isEquals();
    }

    @Override
    public String toString() {
        return "EncodedPayload{"
                + "bytes=" + Arrays.toString(bytes)
                + ", contentClassName=" + contentClassName
                + ", lengthInBytes=" + lengthInBytes
                + ", minimalLength=" + minimalLength
                + '}';
    }
}
