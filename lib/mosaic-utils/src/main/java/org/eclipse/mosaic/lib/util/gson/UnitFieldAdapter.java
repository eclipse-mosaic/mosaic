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

import org.eclipse.mosaic.lib.util.conversion.UnitConverter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

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

    private final UnitConverter converter;

    private UnitFieldAdapter(UnitConverter converter) {
        this.converter = converter;
    }

    @Override
    public void write(JsonWriter out, Double param) throws IOException {
        String result = converter.toString(param);
        if (result == null) {
            out.nullValue();
        } else {
            out.value(result);
        }
    }

    @Override
    public Double read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            return 0D;
        } else if (in.peek() == JsonToken.NUMBER) {
            return in.nextDouble();
        } else if (in.peek() == JsonToken.STRING) {
            return converter.fromString(in.nextString());
        } else {
            return 0D;
        }
    }

    public static class DistanceMeters implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.DistanceMeters(true));
        }
    }

    public static class DistanceMetersQuiet implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.DistanceMeters(false));
        }
    }

    public static class SpeedMS implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.SpeedMS(true));
        }
    }

    public static class SpeedMSQuiet implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.SpeedMS(false));
        }
    }

    public static class WeightKiloGrams implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.WeightKiloGrams(true));
        }
    }


    public static class WeightKiloGramsQuiet implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.WeightKiloGrams(false));
        }
    }

    public static class VoltageVolt implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.VoltageVolt(true));
        }
    }

    public static class VoltageVoltQuiet implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.VoltageVolt(false));
        }
    }

    public static class CurrentAmpere implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.CurrentAmpere(true));
        }
    }

    public static class CurrentAmpereQuiet implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.CurrentAmpere(false));
        }
    }

    public static class CapacityAmpereHour implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.CapacityAmpereHour(true));
        }
    }

    public static class CapacityAmpereHourQuiet implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new UnitFieldAdapter(new UnitConverter.CapacityAmpereHour(false));
        }
    }
}

