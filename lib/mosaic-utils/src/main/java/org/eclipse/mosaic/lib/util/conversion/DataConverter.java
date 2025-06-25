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

import org.eclipse.mosaic.rti.DATA;

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
 * Converter which translates values as string representatives to actual long values
 * representing the number of bits, e.g.,
 * <br>for size: "10 Bytes" -> 80, "2kbit" -> 2000
 * <br>for bandwidth: "10 MBps km/h" -> 80000000, "5 kbps" -> 5000
 * <br><br>
 * Note: using "b" or "B" is a difference, as former stands for bits and later for bytes.
 */
public class DataConverter {

    private static final Logger LOG = LoggerFactory.getLogger(DataConverter.class);

    private static final String BANDWIDTH_SUFFIX = "ps";

    private final static String UNLIMITED = "unlimited";

    private final static Pattern BANDWIDTH_PATTERN = Pattern.compile("^([0-9]+\\.?[0-9]*) ?(|(|k|M|G|T|Ki|Mi|Gi|Ti)(B|b|bit|Bit|bits|Bits|byte|Byte|bytes|Bytes)(|ps))$");
    private final static Map<String, Long> MULTIPLIERS = ImmutableMap.<String, Long>builder()
            .put("", DATA.BIT)
            .put("k", DATA.KILOBIT)
            .put("m", DATA.MEGABIT)
            .put("g", DATA.GIGABIT)
            .put("t", DATA.TERABIT)
            .put("ki", DATA.KIBIBIT)
            .put("mi", DATA.MEBIBIT)
            .put("gi", DATA.GIBIBIT)
            .put("ti", DATA.TEBIBIT)
            .build();

    private final String unitSuffix;
    private final boolean failOnError;

    private DataConverter(boolean failOnError) {
        this("", failOnError);
    }

    private DataConverter(String unitSuffix, boolean failOnError) {
        this.unitSuffix = unitSuffix;
        this.failOnError = failOnError;
    }

    /**
     * Convert the provided human-readable String to a number.
     */
    public Long fromString(@Nonnull String dataString) {
        String value = dataString.trim();
        return UNLIMITED.equals(value)
                ? Long.valueOf(Long.MAX_VALUE)
                : parseBandwidth(value);
    }

    /**
     * Create a human-readable String from the provided value.
     */
    public String toString(Long dataValue) {
        if (dataValue != null && dataValue == Long.MAX_VALUE) {
            return UNLIMITED;
        }
        long value = ObjectUtils.defaultIfNull(dataValue, 0L);
        String unitPrefix = "";

        if (value == 0) {
            unitPrefix = "";
        } else if (value % DATA.TERABIT == 0) {
            unitPrefix = "T";
            value /= DATA.TERABIT;
        } else if (value % DATA.GIGABIT == 0) {
            unitPrefix = "G";
            value /= DATA.GIGABIT;
        } else if (value % DATA.MEGABIT == 0) {
            unitPrefix = "M";
            value /= DATA.MEGABIT;
        } else if (value % DATA.KILOBIT == 0) {
            unitPrefix = "k";
            value /= DATA.KILOBIT;
        }
        return value + " " + unitPrefix + "b" + this.unitSuffix;
    }

    private Long parseBandwidth(String bandwidthDesc) {
        Matcher m = BANDWIDTH_PATTERN.matcher(bandwidthDesc);
        if (m.matches()) {
            double value = Double.parseDouble(m.group(1));
            long multiplier;
            if (StringUtils.isBlank(m.group(2))) {
                multiplier = 1;
            } else {
                if (!unitSuffix.equals(m.group(5))) {
                    if (failOnError) {
                        throw new IllegalArgumentException("Suffix \"" + m.group(5) + "\" does not match with \"" + unitSuffix + "\"");
                    }
                    LOG.warn("Suffix \"{}\" does not match with \"{}\". Ignoring.", m.group(5), unitSuffix);
                }
                multiplier = determineMultiplier(m.group(3), m.group(4));
            }
            return (long) (value * multiplier);
        }
        if (failOnError) {
            throw new IllegalArgumentException("Could not resolve \"" + bandwidthDesc + "\"");
        }
        LOG.error("Could not resolve \"{}\"", bandwidthDesc);
        return 0L;
    }

    private long determineMultiplier(String prefix, String unit) {
        if (prefix == null || unit == null) {
            return DATA.BIT;
        }

        long multiplier = Validate.notNull(MULTIPLIERS.get(prefix.toLowerCase()), "Invalid unit " + prefix + unit);
        if ("bytes".equalsIgnoreCase(unit) || "byte".equalsIgnoreCase(unit) || "B".equals(unit)) {
            return multiplier * DATA.BYTE;
        } else {
            return multiplier;
        }
    }

    public static class Bandwidth extends DataConverter {

        public Bandwidth(boolean failOnError) {
            super(BANDWIDTH_SUFFIX, failOnError);
        }
    }

    public static class Size extends DataConverter {

        public Size(boolean failOnError) {
            super(failOnError);
        }
    }
}
