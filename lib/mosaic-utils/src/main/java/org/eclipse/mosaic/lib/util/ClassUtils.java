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

package org.eclipse.mosaic.lib.util;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class ClassUtils {

    private ClassUtils() {
        // static methods only
    }

    /**
     * Returns the short class name of a class in an efficient way.
     */
    public static String createShortClassName(Class<?> theClass) {
        // do not use getClass().getSimpleName() as it has performance issues: https://bugs.openjdk.java.net/browse/JDK-8187123
        return org.apache.commons.lang3.ClassUtils.getShortClassName(theClass);
    }

    /**
     * Sets the java library path java.library.path.
     *
     * @param path the new library path
     */
    public static void setJavaLibraryPath(String path)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

        String[] presentPaths = System.getProperty("java.library.path").split(";");
        for (String presentPath : presentPaths) {
            if (presentPath.replace('/', '\\').equals(path.replace('/', '\\'))) {
                return;
            }
        }

        System.setProperty("java.library.path", StringUtils.join(presentPaths, ";") + ";" + path);
        //FIXME this produces a illegal-access-warning, but is required (at least for embedded starter from IDE). no other solution is known by now
        setSysPathsToNull();
    }

    private static void setSysPathsToNull() throws NoSuchFieldException, SecurityException,
            IllegalArgumentException, IllegalAccessException {
        final Field sysPathsField = ClassLoader.class.getDeclaredField("sys_paths");
        sysPathsField.setAccessible(true);
        sysPathsField.set(null, null);
    }

    /**
     * Add a URL to the system class loader.
     *
     * @throws IllegalStateException
     */
    public static void addUrlToClassloader(File jar) throws Exception {
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (systemClassLoader instanceof URLClassLoader) {
            Method m = ((URLClassLoader) systemClassLoader).getClass().getDeclaredMethod("addURL", URL.class);
            m.setAccessible(true);
            m.invoke(systemClassLoader, jar.toURI().toURL());
        } else {
            throw new IllegalStateException("Invalid class loader implementation. URLClassLoader required, but was "
                    + systemClassLoader.getClass().getSimpleName()
            );
        }
    }
}
