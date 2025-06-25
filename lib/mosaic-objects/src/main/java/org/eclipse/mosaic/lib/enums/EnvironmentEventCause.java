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

package org.eclipse.mosaic.lib.enums;

/**
 * An {@link EnvironmentEventCause} can be used to inform others about the reason for a specific hazardous event, such
 * as an obstacle on the road, a roadworks area, a bad weather condition, and more. Inspired by CauseCodeType from ETSI DENM.
 *
 * @see <a href="https://etsi.org/deliver/etsi_en/302600_302699/30263703/01.03.01_60/en_30263703v010301p.pdf">en_30263703v010301p.pdf</a>
 */
public enum EnvironmentEventCause {

    TRAFFIC_CONDITION(1),
    ACCIDENT(2),
    ROADWORKS(3),
    OBSTACLE_ON_ROAD(10),
    ADVERSE_WEATHER_CONDITION(6),
    SLOW_VEHICLE(26),
    VEHICLE_BREAKDOWN(91),
    EMERGENCY_VEHICLE_APPROACHING(95),
    COLLISION_RISK(97),
    DANGEROUS_SITUATION(99);

    /**
     * Identifier, used mainly for serialization purposes.
     */
    public final int id;

    /**
     * Default constructor.
     *
     * @param id identifying integer
     */
    EnvironmentEventCause(int id) {
        this.id = id;
    }


    /**
     * Returns the enum mapped from an integer.
     *
     * @param id identifying integer
     * @return the enum mapped from an integer.
     */
    public static EnvironmentEventCause fromId(int id) {
        for (EnvironmentEventCause type : EnvironmentEventCause.values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SensorType id " + id);
    }
}
