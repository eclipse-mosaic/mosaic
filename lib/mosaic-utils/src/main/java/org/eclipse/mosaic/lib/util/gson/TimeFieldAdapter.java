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

import org.eclipse.mosaic.lib.util.conversion.TimeConverter;
import org.eclipse.mosaic.rti.TIME;

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
 * actual long values (nanoseconds), e.g., "10 ms" -> 10_000_000, "20 ns" -> 20, "0.5h" -> 1_800_000_000_000
 * <br><br>
 * Usage:
 * <pre>
 *
 * &#64;JsonAdapter(TimeFieldAdapter.NanoSeconds.class)
 * public long startTime;
 *
 * </pre>
 */
public abstract class TimeFieldAdapter<N extends Number> extends TypeAdapter<N> {

    private final TimeConverter<N> converter;
    private final N emptyValue;
    private final N defaultValue;

    private TimeFieldAdapter(TimeConverter<N> converter, N emptyValue, N defaultValue) {
        this.converter = converter;
        this.emptyValue = emptyValue;
        this.defaultValue = defaultValue;
    }

    @Override
    public void write(JsonWriter out, N param) throws IOException {
        String result = converter.toString(param, emptyValue, defaultValue);
        if (result == null) {
            out.nullValue();
            return;
        }
        out.value(result);
    }

    abstract N readNumber(JsonReader in) throws IOException;

    @Override
    public N read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            return emptyValue;
        } else if (in.peek() == JsonToken.NUMBER) {
            return readNumber(in);
        } else if (in.peek() == JsonToken.STRING) {
            return converter.fromString(in.nextString());
        } else {
            return defaultValue;
        }
    }

    private static class LongTimeFieldAdapter extends TimeFieldAdapter<Long> {

        private LongTimeFieldAdapter(TimeConverter<Long> converter) {
            super(converter, 0L, 0L);
        }

        @Override
        Long readNumber(JsonReader in) throws IOException {
            return in.nextLong();
        }
    }

    private static class DoubleTimeFieldAdapter extends TimeFieldAdapter<Double> {

        private DoubleTimeFieldAdapter(TimeConverter<Double> converter, Double emptyValue) {
            super(converter, emptyValue, 0D);
        }

        @Override
        Double readNumber(JsonReader in) throws IOException {
            return in.nextDouble();
        }
    }

    public static class NanoSeconds implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new LongTimeFieldAdapter(new TimeConverter.LongTimeConverter(true));
        }
    }

    public static class LegacyMilliSeconds implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new LongTimeFieldAdapter(new TimeConverter.LongTimeConverter(TIME.MILLI_SECOND, true));
        }
    }

    public static class LegacySeconds implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new LongTimeFieldAdapter(new TimeConverter.LongTimeConverter(TIME.SECOND, true));
        }
    }

    public static class NanoSecondsQuiet implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new LongTimeFieldAdapter(new TimeConverter.LongTimeConverter(false));
        }
    }

    public static class LegacyMilliSecondsQuiet implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new LongTimeFieldAdapter(new TimeConverter.LongTimeConverter(TIME.MILLI_SECOND, false));
        }
    }

    public static class LegacySecondsQuiet implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new LongTimeFieldAdapter(new TimeConverter.LongTimeConverter(TIME.SECOND, false));
        }
    }

    public static class DoubleSeconds implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new DoubleTimeFieldAdapter(new TimeConverter.DoubleTimeConverter(true), 0D);
        }
    }

    public static class DoubleSecondsQuiet implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new DoubleTimeFieldAdapter(new TimeConverter.DoubleTimeConverter(false), 0D);
        }
    }

    public static class DoubleSecondsNullable implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new DoubleTimeFieldAdapter(new TimeConverter.DoubleTimeConverter(true), null);
        }
    }

    public static class DoubleSecondsQuietNullable implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new DoubleTimeFieldAdapter(new TimeConverter.DoubleTimeConverter(false), null);
        }
    }
}

