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

import org.eclipse.mosaic.lib.util.conversion.DataConverter;

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
 * actual long values representing the number of bits, e.g.,
 * <br>for size: "10 Bytes" -> 80, "2kbit" -> 2000
 * <br>for bandwidth: "10 MBps km/h" -> 80000000, "5 kbps" -> 5000
 * <br><br>
 * Note: using "b" or "B" is a difference, as former stands for bits and later for bytes.
 * <br><br>
 * Usage:
 * <pre>
 *
 *  &#64;JsonAdapter(DataFieldAdapter.Size.class)
 *  public long size;
 *
 * </pre>
 */
public class DataFieldAdapter extends TypeAdapter<Long> {

    private final DataConverter converter;

    private DataFieldAdapter(DataConverter converter) {
        this.converter = converter;
    }

    @Override
    public void write(JsonWriter out, Long param) throws IOException {
        String result = converter.toString(param);
        if (result == null) {
            out.nullValue();
        } else {
            out.value(result);
        }
    }

    @Override
    public Long read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            return 0L;
        } else if (in.peek() == JsonToken.NUMBER) {
            return in.nextLong();
        } else if (in.peek() == JsonToken.STRING) {
            return converter.fromString(in.nextString());
        } else {
            return 0L;
        }
    }

    public static class Bandwidth implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new DataFieldAdapter(new DataConverter.Bandwidth(true));
        }
    }

    public static class Size implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new DataFieldAdapter(new DataConverter.Size(true));
        }
    }

    public static class SizeQuiet implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new DataFieldAdapter(new DataConverter.Size(false));
        }
    }

    public static class BandwidthQuiet implements TypeAdapterFactory {

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            return (TypeAdapter<T>) new DataFieldAdapter(new DataConverter.Bandwidth(false));
        }
    }

}

