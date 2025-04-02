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

package org.eclipse.mosaic.lib.util.gson;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter for JSON fields which translates values as string representatives to
 * actual double values, e.g.
 * <br>for distances: "10 km" -> 10000, "10 cm" -> 0.1, "0.5m" -> 0.5
 * <br>for speeds: "10 km/h" -> 2.77, "10 m/s" -> 10, "35 mph" -> 15.6464
 * <br><br>
 * Usage:
 * <pre>
 *
 *  &#64;JsonAdapter(UnitFieldAdapter.DistanceMeters.class)
 *  public double distance;
 *
 * </pre>
 */
public class UnitFieldAdapter extends TypeAdapter<Double> {

    private static final Logger log = LoggerFactory.getLogger(UnitFieldAdapter.class);

    private final static Pattern DISTANCE_PATTERN = Pattern.compile("^(-?[0-9]+\\.?[0-9]*) ?((|k|d|c|m|\\u00b5|n|kilo|deci|centi|milli|micro|nano)(miles|mile|meter|metre|m))$");
    private final static Pattern SPEED_PATTERN = Pattern.compile("^([0-9]+\\.?[0-9]*) ?(mph|kmh|(?:(|k|d|c|m|\\u00b5|n|kilo|deci|centi|milli|micro|nano)(meter|metre|m)(?:p|per|\\/)(h|hr|s|sec|second|hour)))$");

    private final static Pattern WEIGHT_PATTERN = Pattern.compile("^(-?[0-9]+\\.?[0-9]*) ?((|k|d|c|m|\\u00b5|n|kilo|deci|centi|milli|micro|nano)(g|gram|grams))$");

    private final static Pattern VOLTAGE_PATTERN = Pattern.compile("^(-?[0-9]+\\.?[0-9]*) ?((|k|d|c|m|\\u00b5|n|kilo|deci|centi|milli|micro|nano)(volt|volts|v))$");
    private final static Pattern CURRENT_PATTERN = Pattern.compile("^(-?[0-9]+\\.?[0-9]*) ?((|k|d|c|m|\\u00b5|n|kilo|deci|centi|milli|micro|nano)(ampere|amperes|a))$");
    private final static Pattern CAPACITY_PATTERN = Pattern.compile("^(-?[0-9]+\\.?[0-9]*) ?((|k|d|c|m|\\u00b5|n|kilo|deci|centi|milli|micro|nano)(amperehour|ampereshour|amperehours|ampereshours|ah|ahr))$");

    private final static Map<String, Double> UNIT_MULTIPLIERS = ImmutableMap.<String, Double>builder()
            .put("n", 1 / 1_000_000_000d).put("nano", 1 / 1_000_000_000d)
            .put("\u00b5", 1 / 1_000_000d).put("micro", 1 / 1_000_000d)
            .put("m", 1 / 1000d).put("milli", 1 / 1000d)
            .put("c", 1 / 100d).put("centi", 1 / 100d)
            .put("d", 1 / 100d).put("deci", 1 / 100d)
            .put("k", 1000d).put("kilo", 1000d)
            .build();

    private final boolean failOnError;
    private final Pattern pattern;
    private final String unit;

    private UnitFieldAdapter(boolean failOnError, Pattern pattern, String unit) {
        this.failOnError = failOnError;
        this.pattern = pattern;
        this.unit = ObjectUtils.defaultIfNull(unit, "");
    }

    @Override
    public void write(JsonWriter out, Double param) throws IOException {
        out.value(new DecimalFormat("#.#####", DecimalFormatSymbols.getInstance(Locale.ENGLISH))
                .format(ObjectUtils.defaultIfNull(param, 0d)) + " " + unit);
    }

    @Override
    public Double read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            return 0d;
        } else if (in.peek() == JsonToken.NUMBER) {
            return in.nextDouble();
        } else if (in.peek() == JsonToken.STRING) {
            String value = StringUtils.lowerCase(in.nextString()).trim();
            return parseValue(value);
        } else {
            return 0d;
        }
    }

    private Double parseValue(String valueRaw) {
        Matcher m = pattern.matcher(valueRaw);
        if (m.matches()) {
            final double value = Double.parseDouble(m.group(1));
            double multiplier = 1d;
            if ("mph".equals(m.group(2))) { //special cases which otherwise would mixed up with meters per hour
                multiplier = determineUnitMultiplier("", "miles") / determineSpeedDivisor("h");
            } else if ("kmh".equals(m.group(2))) { //special case allowing no slash in km/h
                multiplier = determineUnitMultiplier("kilo", "meter") / determineSpeedDivisor("h");
            } else if (m.groupCount() >= 3) {
                multiplier = determineUnitMultiplier(m.group(3), m.group(4));
                if (m.groupCount() == 5) {
                    multiplier /= determineSpeedDivisor(m.group(5));
                }
            }
            return value * multiplier;
        }
        if (failOnError) {
            throw new IllegalArgumentException("Could not resolve \"" + valueRaw + "\"");
        }
        log.warn("Could not resolve \"{}\"", valueRaw);
        return 0d;
    }

    private double determineSpeedDivisor(String timeUnit) {
        if (timeUnit.startsWith("h")) {
            return 3600d;
        } else {
            return 1d;
        }
    }

    double determineUnitMultiplier(String prefix, String unit) {
        if (unit.startsWith("mile")) {
            //special case
            return 1609.344;
        } else if (StringUtils.isNotEmpty(prefix)) {
            return UNIT_MULTIPLIERS.getOrDefault(prefix, 1d);
        }
        return 1d;
    }

    public static class DistanceMeters implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(true, DISTANCE_PATTERN, "m");
        }
    }

    public static class DistanceMetersQuiet implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(false, DISTANCE_PATTERN, "m");
        }
    }

    public static class SpeedMS implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(true, SPEED_PATTERN, "m/s");
        }
    }

    public static class SpeedMSQuiet implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(false, SPEED_PATTERN, "m/s");
        }
    }

    public static class WeightKiloGrams implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(true, WEIGHT_PATTERN, "kg") {
                @Override
                double determineUnitMultiplier(String prefix, String unit) {
                    double multiplier = 1;
                    if (StringUtils.isNotEmpty(prefix)) {
                        multiplier = UNIT_MULTIPLIERS.getOrDefault(prefix, 1d);
                    }
                    return multiplier * 0.001;
                }
            };
        }
    }


    public static class WeightKiloGramsQuiet implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(false, WEIGHT_PATTERN, "kg") {
                @Override
                double determineUnitMultiplier(String prefix, String unit) {
                    double multiplier = 1;
                    if (StringUtils.isNotEmpty(prefix)) {
                        multiplier = UNIT_MULTIPLIERS.getOrDefault(prefix, 1d);
                    }
                    return multiplier * 0.001;
                }
            };
        }
    }

    public static class VoltageVolt implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(true, VOLTAGE_PATTERN, "V");
        }
    }

    public static class VoltageVoltQuiet implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(false, VOLTAGE_PATTERN, "V");
        }
    }

    public static class CurrentAmpere implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(true, CURRENT_PATTERN, "A");
        }
    }

    public static class CurrentAmpereQuiet implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(false, CURRENT_PATTERN, "A");
        }
    }

    public static class CapacityAmpereHour implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(true, CAPACITY_PATTERN, "Ah");
        }
    }

    public static class CapacityAmpereHourQuiet implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(false, CAPACITY_PATTERN, "Ah");
        }
    }
}

