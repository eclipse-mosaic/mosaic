/*
 * Copyright (c) 2024 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.application.app.api.perception;

import org.eclipse.mosaic.lib.objects.environment.Sensor;

import java.util.Optional;

/**
 * A basic sensor module which provides single integer values for a given {@link Sensor}.
 */
public interface BasicSensorModule {

    /**
     * Enables this basic sensor module.
     */
    void enable();

    /**
     * @return {@code true}, if this module has been enabled.
     */
    boolean isEnabled();

    /**
     * Disables this basic sensor module. {@link #getSensorValue(Sensor)} will always return null.
     */
    void disable();

    /**
     * Returns the value of the provided environment {@link Sensor}.
     *
     * @param sensor The {@link Sensor} to use.
     * @return Strength of the measured environment sensor data.
     */
    <T> Optional<T> getSensorValue(Sensor<T> sensor);

}

