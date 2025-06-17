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

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.math.MathUtils;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class CartesianPointAdapter implements JsonDeserializer<CartesianPoint>, JsonSerializer<CartesianPoint> {

    @Override
    public CartesianPoint deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        final JsonObject json = jsonElement.getAsJsonObject();

        if (!json.has("x")) {
            throw new IllegalArgumentException("No member \"x\" found");
        }
        if (!json.has("y")) {
            throw new IllegalArgumentException("No member \"y\" found");
        }

        return CartesianPoint.xyz(
                json.get("x").getAsDouble(),
                json.get("y").getAsDouble(),
                json.has("z")
                        ? json.get("z").getAsDouble()
                        : 0d
        );
    }

    @Override
    public JsonElement serialize(CartesianPoint point, Type type, JsonSerializationContext jsonSerializationContext) {
        final JsonElement element = jsonSerializationContext.serialize(point);
        if (MathUtils.isFuzzyZero(point.getZ()) && element instanceof JsonObject) {
            ((JsonObject)element).remove("z");
        }
        return element;
    }
}
