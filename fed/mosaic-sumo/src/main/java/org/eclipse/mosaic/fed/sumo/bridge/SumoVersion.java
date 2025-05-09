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

package org.eclipse.mosaic.fed.sumo.bridge;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum SumoVersion {

    UNKNOWN("0.0.*", TraciVersion.UNKNOWN),

    SUMO_1_0_x("1.0.*", TraciVersion.API_18),
    SUMO_1_1_x("1.1.*", TraciVersion.API_19),
    SUMO_1_2_x("1.2.*", TraciVersion.API_20),
    SUMO_1_3_x("1.3.*", TraciVersion.API_20),
    SUMO_1_4_x("1.4.*", TraciVersion.API_20),
    SUMO_1_5_x("1.5.*", TraciVersion.API_20),
    SUMO_1_6_x("1.6.*", TraciVersion.API_20),
    SUMO_1_7_x("1.7.*", TraciVersion.API_20),
    SUMO_1_8_x("1.8.*", TraciVersion.API_20),
    SUMO_1_9_x("1.9.*", TraciVersion.API_20),
    SUMO_1_10_x("1.10.*", TraciVersion.API_20),
    SUMO_1_11_x("1.11.*", TraciVersion.API_20),
    SUMO_1_12_x("1.12.*", TraciVersion.API_20),
    SUMO_1_13_x("1.13.*", TraciVersion.API_20),
    SUMO_1_14_x("1.14.*", TraciVersion.API_20),
    SUMO_1_15_x("1.15.*", TraciVersion.API_20),
    SUMO_1_16_x("1.16.*", TraciVersion.API_20),
    SUMO_1_17_x("1.17.*", TraciVersion.API_20),
    SUMO_1_18_x("1.18.*", TraciVersion.API_20),
    SUMO_1_19_x("1.19.*", TraciVersion.API_21),
    SUMO_1_20_x("1.20.*", TraciVersion.API_21),
    SUMO_1_21_x("1.21.*", TraciVersion.API_21),
    SUMO_1_22_x("1.22.*", TraciVersion.API_21),
    SUMO_1_23_x("1.23.*", TraciVersion.API_22),

    /**
     * the lowest version supported by this client.
     */
    LOWEST(SUMO_1_0_x.sumoVersion, SUMO_1_0_x.traciVersion),

    /**
     * the highest version supported by this client.
     */
    HIGHEST(SUMO_1_23_x.sumoVersion, SUMO_1_23_x.traciVersion);

    private final String sumoVersion;
    private final TraciVersion traciVersion;
    private final int major;
    private final int minor;

    SumoVersion(String sumoVersion, TraciVersion traciVersion) {
        this.sumoVersion = sumoVersion;
        this.traciVersion = traciVersion;
        this.major = Integer.parseInt(StringUtils.substringBefore(sumoVersion, "."));
        this.minor = Integer.parseInt(StringUtils.substringBetween(sumoVersion, ".", "."));
    }

    public int getApiVersion() {
        return traciVersion.getApiVersion();
    }

    public String getSumoVersion() {
        return sumoVersion;
    }

    public static SumoVersion getSumoVersion(String sumoVersion) {
        for (SumoVersion version : SumoVersion.values()) {
            if (matches(sumoVersion, version.getSumoVersion())) {
                return version;
            }
        }
        return UNKNOWN;
    }

    public TraciVersion getTraciVersion() {
        return traciVersion;
    }

    private final static Pattern VERSION_PATTERN = Pattern.compile("^SUMO v?([0-9]\\.[0-9]+)\\..*$");

    private static boolean matches(String sumoVersionString, String sumoVersionPattern) {
        final Matcher matcher = VERSION_PATTERN.matcher(sumoVersionString);
        return matcher.matches() && (matcher.group(1) + ".*").equals(sumoVersionPattern);

    }

    public boolean isGreaterOrEqualThan(SumoVersion other) {
        return this.major > other.major || (this.major == other.major && this.minor >= other.minor);
    }
}
