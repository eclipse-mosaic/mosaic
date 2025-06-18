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

package org.eclipse.mosaic.rti.api;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import javax.annotation.Nonnull;

/**
 * This class provides the version of the currently used MOSAIC build.
 */
public class MosaicVersion implements Comparable<MosaicVersion> {

    private static String mosaicVersion;

    private final int major;
    private final int minor;
    private final boolean isSnapshot;

    private MosaicVersion(@Nonnull String version) {
        // make sure there is something to check
        Objects.requireNonNull(version);
        // check for snapshot
        String[] split = version.split("-");
        this.isSnapshot = (split.length > 1 && "SNAPSHOT".equals(split[1]));

        // now check for actual version
        split = split[0].split("\\.");
        this.major = (split.length > 0) ? Integer.parseInt(split[0]) : 0;
        this.minor = (split.length > 1) ? Integer.parseInt(split[1]) : 0;

    }

    public static MosaicVersion get() {
        if (mosaicVersion == null) {
            Properties properties = new Properties();
            try (InputStream inputStream = MosaicVersion.class.getResourceAsStream("/application.properties")) {
                properties.load(inputStream);
                mosaicVersion = properties.getProperty("mosaic.version", null);
            } catch (IOException e) {
                LoggerFactory.getLogger(MosaicVersion.class).error("Could not retrieve MOSAIC version", e);
            }
        }
        return new MosaicVersion(mosaicVersion);
    }

    public static MosaicVersion createFromString(String version) {
        return new MosaicVersion(version);
    }

    @Override
    public int compareTo(@Nonnull MosaicVersion o) {
        // failsafe according to interface
        Objects.requireNonNull(o);

        // first major version
        if (this.major < o.major) {
            return -1;
        } else if (this.major > o.major) {
            return 1;
        }

        // major is equal => check minor
        if (this.minor < o.minor) {
            return -1;
        } else if (this.minor > o.minor) {
            return 1;
        }

        // version itself is equal => check snapshot marker
        if (this.isSnapshot && !o.isSnapshot) {
            return -1;
        } else if (o.isSnapshot && !this.isSnapshot) {
            return 1;
        }

        // seemingly all numbers and the snapshot marker are equal
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MosaicVersion other = (MosaicVersion) o;
        return new EqualsBuilder()
                .append(major, other.major)
                .append(minor, other.minor)
                .append(isSnapshot, other.isSnapshot)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(major)
                .append(minor)
                .append(isSnapshot)
                .toHashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append(".").append(minor);
        if (isSnapshot) {
            sb.append("-SNAPSHOT");
        }
        return sb.toString();
    }
}
