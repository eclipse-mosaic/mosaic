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

package org.eclipse.mosaic.fed.environment.config;

import java.io.Serializable;

/**
 * Single event configuration.
 */
public class CEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The location of the event,, this can either be a
     * {@link org.eclipse.mosaic.lib.geo.GeoArea} or
     * a {@link String} representing a specific road segment.
     */
    public CEventLocation location = new CEventLocation();

    /**
     * Time of the event.
     */
    public CEventTime time = new CEventTime();

    /**
     * This represents the type of the in that area, e.g. Ice, or Snow.
     */
    public String type;

    /**
     * This is a value used for assigning a value to the event,
     * it can be used as the strength of an event, or the
     * amount of free parking spots in a parking lot, etc.
     */
    public Object value = null;
}

