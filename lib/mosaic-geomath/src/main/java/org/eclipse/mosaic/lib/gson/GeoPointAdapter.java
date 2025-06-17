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

package org.eclipse.mosaic.lib.gson;

import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;
import org.eclipse.mosaic.lib.math.MathUtils;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class GeoPointAdapter implements JsonDeserializer<GeoPoint>, JsonSerializer<GeoPoint> {

    @Override
    public GeoPoint deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return jsonDeserializationContext.deserialize(jsonElement, MutableGeoPoint.class);
    }

    @Override
    public JsonElement serialize(GeoPoint point, Type type, JsonSerializationContext jsonSerializationContext) {
        final JsonElement element = jsonSerializationContext.serialize(point);
        if (MathUtils.isFuzzyZero(point.getAltitude()) && element instanceof JsonObject) {
            ((JsonObject)element).remove("altitude");
        }
        return element;
    }
}
