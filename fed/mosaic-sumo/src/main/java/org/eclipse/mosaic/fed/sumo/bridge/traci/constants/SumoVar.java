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

package org.eclipse.mosaic.fed.sumo.bridge.traci.constants;

import org.eclipse.mosaic.fed.sumo.bridge.TraciVersion;

public class SumoVar {

    public final int var;
    private final TraciVersion since;
    private final TraciVersion deprecatedSince;

    /**
     * Use factory methods to create Objects of this class.
     *
     * @see #var(int)
     * @see #varSince(int, TraciVersion)
     * @see #varDeprecated(int, TraciVersion)
     * @see #varSinceAndDeprecated(int, TraciVersion, TraciVersion)
     */
    private SumoVar(int var, TraciVersion since, TraciVersion deprecatedSince) {
        this.var = var;
        this.since = since;
        this.deprecatedSince = deprecatedSince;
    }

    public static SumoVar var(int var) {
        return new SumoVar(var, TraciVersion.LOWEST, null);
    }

    public static SumoVar varSince(int var, TraciVersion since) {
        return new SumoVar(var, since, null);
    }

    public static SumoVar varDeprecated(int var, TraciVersion deprecatedSince) {
        return new SumoVar(var, TraciVersion.LOWEST, deprecatedSince);
    }

    public static SumoVar varSinceAndDeprecated(int var, TraciVersion since, TraciVersion deprecatedSince) {
        return new SumoVar(var, since, deprecatedSince);
    }

    /**
     * Checks whether this sumo variable is available in the current version.
     *
     * @return {@code true}, if this variable is available in the present TraCI API
     */
    public boolean isAvailable(TraciVersion currentVersion) {
        return (since == null || currentVersion.getApiVersion() >= since.getApiVersion())
                && (deprecatedSince == null || currentVersion.getApiVersion() < deprecatedSince.getApiVersion());
    }

    public static class WithParam extends SumoVar {

        private Double value;

        private WithParam(int var, Double value, TraciVersion since, TraciVersion deprecatedSince) {
            super(var, since, deprecatedSince);
            this.value = value;
        }

        public Double getValue() {
            return value;
        }

        static SumoVar.WithParam var(int var, double value) {
            return new WithParam(var, value, TraciVersion.LOWEST, null);
        }
    }

    public static class WithIntParam extends SumoVar {

        private Integer value;

        private WithIntParam(int var, Integer value, TraciVersion since, TraciVersion deprecatedSince) {
            super(var, since, deprecatedSince);
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }

        static SumoVar.WithIntParam var(int var, int value) {
            return new WithIntParam(var, value, TraciVersion.LOWEST, null);
        }

        static SumoVar.WithIntParam varSince(int var, int value, TraciVersion since) {
            return new WithIntParam(var, value, since, null);
        }
    }
}
