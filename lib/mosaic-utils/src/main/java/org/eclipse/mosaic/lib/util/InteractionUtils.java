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

import static com.google.common.reflect.ClassPath.ClassInfo;
import static com.google.common.reflect.ClassPath.from;

import org.eclipse.mosaic.rti.api.Interaction;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class InteractionUtils {

    private static final Logger LOG = LoggerFactory.getLogger(InteractionUtils.class);

    /**
     * Cached list of all top level classes found in the classpath.
     */
    private static final List<ClassInfo> TOP_LEVEL_CLASSES = new ArrayList<>();
    /**
     * All packages to scan in that particular order.
     */
    private static final List<String> PACKAGES_FOR_SCAN = Lists.newArrayList("org.eclipse.mosaic");

    /**
     * Cached scan results for each provided package to scan.
     *
     * @see #PACKAGES_FOR_SCAN
     */
    private static final Map<String, Map<String, Class<?>>> SCAN_RESULTS_PER_PACKAGE = new HashMap<>();

    /**
     * All cached interaction classes found in the classpath.
     */
    private static final Map<String, Class<?>> INTERACTIONS = new HashMap<>();

    /**
     * Returns the {@link Interaction} class for the given interaction type id.
     * <br>
     * If no interaction could be found for the given type ID, the whole classpath
     * is searched for a class which extends {@link Interaction} and provides a
     * field named {@code TYPE_ID} which equals the provided {@code typeId} parameter.
     * <br>
     * The default package to scan is {@code org.eclipse.mosaic}. To search within
     * further packages, it must be added via {@link #addPackageForScan(String)}.
     */
    public static Class<?> getInteractionClassForTypeId(String typeId) {
        Class<?> interactionClass = INTERACTIONS.get(typeId);
        if (interactionClass != null) {
            return interactionClass;
        }

        // If not cached, then search in packages one after another
        for (String packageName : PACKAGES_FOR_SCAN) {
            Map<String, Class<?>> scanResult = SCAN_RESULTS_PER_PACKAGE.get(packageName);
            if (scanResult == null) {
                scanResult = getInteractionsWithinPackage(packageName);
                SCAN_RESULTS_PER_PACKAGE.put(packageName, scanResult);

                scanResult.forEach(INTERACTIONS::putIfAbsent);
            }

            interactionClass = scanResult.get(typeId);
            if (interactionClass != null) {
                return interactionClass;
            }
        }

        return null;
    }

    /**
     * Registers an additional package to scan for Interaction classes
     * which have not yet been cached using {@link #storeInteractionClass(Class)}.
     *
     * @param packageName the name of the additional package.
     */
    public static void addPackageForScan(String packageName) {
        if (!PACKAGES_FOR_SCAN.contains(packageName)) {
            PACKAGES_FOR_SCAN.add(packageName);
        }
    }

    /**
     * Caches the provided class to be accessible via {@link #getInteractionClassForTypeId(String)}.
     * If the class is not cached but requested, a full scan of classes in the classpath
     * is executed which may result in slow startup times.
     *
     * @param interactionClass a subclass of {@link Interaction}
     */
    public static void storeInteractionClass(Class<?> interactionClass) {
        if (Interaction.class.isAssignableFrom(interactionClass)) {
            String typeId = extractTypeId(interactionClass)
                    .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Could not extract TYPE_ID from provided interaction class '%s'", interactionClass.getName()))
                    );
            INTERACTIONS.put(typeId, interactionClass);
        } else {
            throw new IllegalArgumentException(
                    String.format("Provided class '%s' is not an Interaction.", interactionClass.getName())
            );
        }
    }

    /**
     * Helper method, which returns all classes which are supported to be visualized. A class
     * is supported, if it is within the (sub)package of \"org.eclipse.mosaic\" and is a subclass
     * of {@link Interaction}. The key of the returned map refers to the type id of each message. The
     * type id is extracted from the class' constant field TYPE_ID, if present. Otherwise, the type
     * id is equal to the simple name of the class.
     *
     * @return the map with all supported message classes
     */
    @SuppressWarnings({"unchecked", "UnstableApiUsage"})
    private static Map<String, Class<?>> getInteractionsWithinPackage(String searchPackage) {
        final StopWatch sw = new StopWatch();
        sw.start();

        final Map<String, Class<?>> result = new HashMap<>();
        try {
            if (TOP_LEVEL_CLASSES.isEmpty()) {
                TOP_LEVEL_CLASSES.addAll(from(ClassLoader.getSystemClassLoader()).getTopLevelClasses());
            }
            for (ClassInfo info : TOP_LEVEL_CLASSES) {
                if (info.getName().equals(Interaction.class.getName())) {
                    continue;
                }
                if (!info.getPackageName().startsWith(searchPackage)) {
                    continue;
                }
                if (info.getName().contains("ClientServerChannelProtos")) {
                    continue;
                }
                try {
                    Class<?> messageClass = info.load();
                    if (Interaction.class.isAssignableFrom(messageClass)) {
                        String msgType = extractTypeId(messageClass)
                                .orElse(Interaction.createTypeIdentifier((Class<? extends Interaction>) messageClass));
                        Class<?> knownClass = result.putIfAbsent(msgType, messageClass);
                        if (knownClass != null && knownClass != messageClass) {
                            LOG.warn("Ambiguous interaction type '{}'. Already registered with class {}", msgType, knownClass);
                        }
                    }
                } catch (Throwable e) {
                    //nop
                }
            }
        } catch (Exception e) {
            LOG.error("Could not generate list of supported interaction types", e);
        }
        sw.stop();
        LOG.info("Scanning for {} interactions in '{}' took {} ms", result.size(), searchPackage, sw.getTime(TimeUnit.MILLISECONDS));
        return result;
    }

    /**
     * Extracts the type ID from the interaction class.
     *
     * @param interactionClass the interaction class
     * @return the type ID read from the field TYPE_ID of the interaction
     */
    public static Optional<String> extractTypeId(Class<?> interactionClass) {
        try {
            if (Modifier.isAbstract(interactionClass.getModifiers())) {
                return Optional.empty();
            }
            Field typeIdField = interactionClass.getDeclaredField("TYPE_ID");
            Class<?> cl = typeIdField.getType();
            if (cl.equals(String.class)) {
                return Optional.of((String) typeIdField.get(null));
            }
        } catch (Throwable e) {
            LOG.warn("Could not extract field TYPE_ID of class {}", interactionClass.getName());
        }
        return Optional.empty();
    }
}
