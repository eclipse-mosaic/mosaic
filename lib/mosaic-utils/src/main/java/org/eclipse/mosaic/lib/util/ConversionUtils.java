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

package org.eclipse.mosaic.lib.util;

import javax.annotation.Nullable;

public class ConversionUtils {

    public static @Nullable Double toDouble(@Nullable Object o) {
        if (o == null) {
            return null;
        } else if (o instanceof Boolean) {
            return (Boolean) o ? 1.0 : 0.0;
        } else if (o instanceof String) {
            return Double.parseDouble((String) o);
        } else if (o instanceof Integer) {
            return ((Integer) o).doubleValue();
        } else if (o instanceof Long) {
            return ((Long) o).doubleValue();
        } else if (o instanceof Double) {
            return (Double) o;
        } else {
            throw new IllegalArgumentException("Could not translate object of type " + o.getClass() + " to Double.");
        }
    }

    public static @Nullable Integer toInteger(@Nullable Object o) {
        if (o == null) {
            return null;
        } else if (o instanceof Boolean) {
            return (Boolean) o ? 1 : 0;
        } else if (o instanceof String) {
            return Integer.parseInt((String) o);
        } else if (o instanceof Integer) {
            return (Integer) o;
        } else if (o instanceof Long) {
            return ((Long) o).intValue();
        } else if (o instanceof Double) {
            return ((Double) o).intValue();
        } else {
            throw new IllegalArgumentException("Could not translate object of type " + o.getClass() + " to Integer.");
        }
    }
}
