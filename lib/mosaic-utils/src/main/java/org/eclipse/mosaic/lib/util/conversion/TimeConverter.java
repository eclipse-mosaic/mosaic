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

package org.eclipse.mosaic.lib.util.conversion;

import org.eclipse.mosaic.rti.TIME;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

/**
 * Adapter for JSON fields which translates values as string representatives to actual long or double values (nanoseconds),
 * e.g. "10 ms" -> 10_000_000, "20 ns" -> 20, "0.5h" -> 1_800_000_000_000
 */
public abstract class TimeConverter<N extends Number> {

    private static final Logger LOG = LoggerFactory.getLogger(TimeConverter.class);

    private final static Pattern TIME_PATTERN = Pattern.compile("^([0-9]+[0-9_]*?\\.?[0-9]*) ?(min|minute|minutes|h|hour|hours|(|m|\\u00b5|n|milli|micro|nano)(?:|s|sec|second|seconds))$");
    private final static Map<String, Long> MULTIPLIERS = ImmutableMap.<String, Long>builder()
            .put("", TIME.SECOND)
            .put("n", TIME.NANO_SECOND)
            .put("\u00b5", TIME.MICRO_SECOND)
            .put("m", TIME.MILLI_SECOND)
            .put("nano", TIME.NANO_SECOND)
            .put("micro", TIME.MICRO_SECOND)
            .put("milli", TIME.MILLI_SECOND)
            .build();

    private final long legacyDivisor;
    private final boolean failOnError;

    private TimeConverter(long legacyDivisor, boolean failOnError) {
        this.legacyDivisor = legacyDivisor;
        this.failOnError = failOnError;
    }

    protected abstract long toNanoseconds(@Nonnull N time);

    protected abstract N fromNanoseconds(long nanoseconds);

    /**
     * Convert the provided human-readable String to a time value.
     */
    public N fromString(String timeString) {
        return fromNanoseconds(parseTime(StringUtils.lowerCase(timeString).trim()));
    }

    /**
     * Create a human-readable String from the provided time value.
     */
    public String toString(N time, N emptyValue, N defaultValue) {
        if (time == null && emptyValue == null) {
            return null;
        }

        long value = toNanoseconds(ObjectUtils.defaultIfNull(time, defaultValue));
        final String unit;
        if (value == 0) {
            unit = "s";
        } else if (value % TIME.HOUR == 0) {
            unit = "h";
            value /= TIME.HOUR;
        } else if (value % TIME.MINUTE == 0) {
            unit = "min";
            value /= TIME.MINUTE;
        } else if (value % TIME.SECOND == 0) {
            unit = "s";
            value /= TIME.SECOND;
        } else if (value % TIME.MILLI_SECOND == 0) {
            unit = "ms";
            value /= TIME.MILLI_SECOND;
        } else {
            unit = "ns";
        }
        return value + " " + unit;
    }

    private Long parseTime(String timeDesc) {
        Matcher m = TIME_PATTERN.matcher(timeDesc);
        if (m.matches()) {
            final double value = Double.parseDouble(StringUtils.remove(m.group(1), '_'));
            final long multiplier;
            if (StringUtils.isEmpty(m.group(2))) {
                multiplier = legacyDivisor;
            } else {
                multiplier = determineMultiplier(m.group(2), m.group(3));
                if (multiplier < legacyDivisor) {
                    LOG.warn("Given prefix in time description {} is lower than expected. This might result in wrong behavior.", timeDesc);
                }
            }
            return ((long) (value * multiplier)) / legacyDivisor;
        }
        if (failOnError) {
            throw new IllegalArgumentException("Could not resolve \"" + timeDesc + "\"");
        }
        LOG.warn("Could not resolve \"{}\"", timeDesc);
        return 0L;
    }

    private long determineMultiplier(String timeUnit, String secondPrefix) {
        if (secondPrefix != null) {
            return determineMultiplierFromSubsecond(secondPrefix);
        } else if (timeUnit.startsWith("m")) {
            return TIME.MINUTE;
        } else if (timeUnit.startsWith("h")) {
            return TIME.HOUR;
        }
        return 1;
    }

    private long determineMultiplierFromSubsecond(String prefix) {
        return Validate.notNull(MULTIPLIERS.get(prefix), "Invalid time prefix " + prefix);
    }

    public static class LongTimeConverter extends TimeConverter<Long> {

        public LongTimeConverter(boolean failOnError) {
            this(TIME.NANO_SECOND, failOnError);
        }

        public LongTimeConverter(long legacyDivisor, boolean failOnError) {
            super(legacyDivisor, failOnError);
        }

        @Override
        protected Long fromNanoseconds(long nanoseconds) {
            return nanoseconds;
        }

        @Override
        protected long toNanoseconds(@Nonnull Long time) {
            return time;
        }
    }

    public static class DoubleTimeConverter extends TimeConverter<Double> {

        public DoubleTimeConverter(boolean failOnError) {
            super(TIME.NANO_SECOND, failOnError);
        }

        @Override
        protected Double fromNanoseconds(long nanoseconds) {
            return ((double) nanoseconds) / TIME.SECOND;
        }

        @Override
        protected long toNanoseconds(@Nonnull Double time) {
            return (long) (time * TIME.SECOND);
        }

    }
}
