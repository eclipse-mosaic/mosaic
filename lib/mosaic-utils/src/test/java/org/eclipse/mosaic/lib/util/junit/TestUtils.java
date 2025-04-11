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

package org.eclipse.mosaic.lib.util.junit;

import java.lang.reflect.Field;

public class TestUtils {

    /**
     * Sets the value for a private or protected field of an object. Please be very careful and avoid doing so.
     *
     * @param object    the object to set the field of, use a class to set a static field
     * @param fieldName the name of the field to set
     * @param value     the value to set
     * @throws IllegalStateException if the field could not be found or set with the given value
     */
    public static void setPrivateField(Object object, String fieldName, Object value) throws IllegalStateException {
        try {
            final Field field;
            if (object instanceof Class<?>) {
                field = ((Class<?>) object).getDeclaredField(fieldName);
            } else {
                field = object.getClass().getDeclaredField(fieldName);
            }
            field.setAccessible(true);
            field.set(object, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
