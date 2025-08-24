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

package org.eclipse.mosaic.interactions.communication;

import org.eclipse.mosaic.rti.api.Interaction;

/**
 * This extension of {@link Interaction} is intended to be used to
 * exchange information about the configuration of a vehicle's radio communication
 * facilities.
 */
public abstract class CommunicationConfiguration extends Interaction {

    private final static long serialVersionUID = 1L;

    /**
     * Constructor using fields.
     *
     * @param time Simulation time at which the interaction happens.
     */
    protected CommunicationConfiguration(long time) {
        super(time);
    }
}