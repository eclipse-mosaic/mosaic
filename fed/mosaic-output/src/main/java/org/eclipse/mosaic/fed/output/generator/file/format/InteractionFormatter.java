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

package org.eclipse.mosaic.fed.output.generator.file.format;

import org.eclipse.mosaic.lib.util.InteractionUtils;
import org.eclipse.mosaic.rti.api.Interaction;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class InteractionFormatter {

    /**
     * Map from interaction type to its formatting MethodManagers.
     */
    private final Map<String, ArrayList<MethodManager>> methodManagers;

    /**
     * Construct an InteractionFormatter according to its separator, the pairs of
     * message types and their definitions. The definition for all interaction types
     * are composed of a list of method definitions for each column in the interaction.
     *
     * @param separator              string separator
     * @param decimalSeparator       separator for floating-point numbers
     * @param interactionDefinitions message definitions
     */
    public InteractionFormatter(char separator, char decimalSeparator, Map<String,
            List<List<String>>> interactionDefinitions)
            throws SecurityException, NoSuchMethodException, ClassNotFoundException, IllegalArgumentException {

        this.methodManagers = new HashMap<>();

        for (Entry<String, List<List<String>>> e : interactionDefinitions.entrySet()) {
            String interactionId = e.getKey();

            this.methodManagers.putIfAbsent(interactionId, new ArrayList<>());

            Class<?> interactionClass = InteractionUtils.getInteractionClassForTypeId(interactionId);

            if (interactionClass != null) {
                List<List<String>> interactionDefList = e.getValue();

                for (List<String> interactionDef : interactionDefList) {
                    // create method manager for this definition
                    MethodManager methodMgr =
                            new MethodManager(separator, decimalSeparator, interactionDef, interactionClass);

                    this.methodManagers.get(interactionId).add(methodMgr);
                }
            } else {
                throw new ClassNotFoundException(interactionId + " is an unknown parameter in the visualizeMessage method set.");
            }
        }
    }

    /**
     * return the formatted interaction in string type.
     *
     * @param interaction interaction
     * @return formatted interaction
     */
    public String format(Interaction interaction) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        String messageName = interaction.getClass().getSimpleName();

        StringBuilder res = new StringBuilder();
        for (MethodManager methodManager : this.methodManagers.get(messageName)) {
            res.append(methodManager.format(interaction));
        }
        return res.toString();
    }

    /**
     * Return all the interaction types, which have been defined.
     *
     * @return collection of all interaction types
     */
    public Collection<String> getInteractionTypes() {
        return this.methodManagers.keySet();
    }
}
