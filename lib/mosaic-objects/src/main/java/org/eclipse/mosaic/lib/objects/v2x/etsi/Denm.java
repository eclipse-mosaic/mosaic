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

package org.eclipse.mosaic.lib.objects.v2x.etsi;

import org.eclipse.mosaic.lib.enums.EnvironmentEventCause;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoPolygon;
import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * Decentralized Environment Notification Message.
 */
@Immutable
public class Denm extends V2xMessage {

    private static final long serialVersionUID = 1L;

    /**
     * The encoded message.
     */
    private final EncodedPayload payload;

    private final DenmContent denmContent;

    @Override
    @Nonnull
    public EncodedPayload getPayload() {
        return payload;
    }

    public Denm(final MessageRouting routing, DenmContent denmContent, long minimalPayloadLength) {
        super(routing);
        this.denmContent = denmContent;

        if (EtsiPayloadConfiguration.getPayloadConfiguration().encodePayloads) {
            payload = new EncodedPayload(denmContent, minimalPayloadLength);
        } else {
            payload = new EncodedPayload(0, minimalPayloadLength);
        }

    }

    public Denm(final MessageRouting routing, final Denm denm, long minimalPayloadLength) {
        this(routing, denm.denmContent, minimalPayloadLength);
    }

    /**
     * Returns the time. Unit: [ns].
     *
     * @return the time. Unit: [ns].
     */
    public long getTime() {
        return this.denmContent.getTime();
    }

    /**
     * Returns the coordinates of the sending node.
     *
     * @return the coordinate.
     */
    public GeoPoint getSenderPosition() {
        return this.denmContent.getSenderPosition();
    }

    /**
     * Returns the identifier of the road on which the sender is located.
     *
     * @return the identifier.
     */
    public String getEventRoadId() {
        return this.denmContent.getEventRoadId();
    }

    /**
     * Returns the cause of the warning event.
     *
     * @return the cause of the warning event.
     */
    public EnvironmentEventCause getEventCause() {
        return this.denmContent.getEventCause();
    }

    /**
     * TODO: Add the Unit. Unit: [???].
     * Returns the resulting speed because of the event.
     *
     * @return the resulting speed.
     */
    public float getCausedSpeed() {
        return this.denmContent.getCausedSpeed();
    }

    /**
     * TODO: Add the Unit. Unit: [???].
     *
     * @return
     */
    public float getSenderDeceleration() {
        return this.denmContent.getSenderDeceleration();
    }

    /**
     * Returns the location of the event.
     *
     * @return the coordinate.
     */
    public GeoPoint getEventLocation() {
        return this.denmContent.getEventLocation();
    }

    /**
     * Returns the area of the event.
     *
     * @return a list of coordinates.
     */
    public GeoPolygon getEventArea() {
        return this.denmContent.getEventArea();
    }

    /**
     * Returns the extended container.
     *
     * @return the additional information.
     */
    public String getExtendedContainer() {
        return this.denmContent.getExtendedContainer();
    }

}
