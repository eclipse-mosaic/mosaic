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

package org.eclipse.mosaic.lib.util.objects;

/**
 * Provides methods to transform IDs if the identifier scheme of a federate differs to MOSAIC.
 * For example, OMNeT++ uses sequential numbers as unit IDs, whereas MOSAIC conform IDs of the units
 * are based on their type of unit, e.g., ("rsu_0", "veh_0", "veh_1", etc.) *
 */
public interface IdTransformer<ExternalT, InternalT> {

    /**
     * Maps the internal MOSAIC conform unit ID (e.g., "veh_1", "agent_1", ...) to the federate specific unit ID
     *
     * @param internalId the internal ID of type ExternalT
     * @return the federate specific external ID of a unit
     */
    ExternalT toExternalId(InternalT internalId);

    /**
     * Maps the federate specific unit ID back to the internal MOSAIC conform unit ID (e.g., "veh_1", "agent_1", ...)
     *
     * @param externalId the federate specific external ID of a unit
     * @return the MOSAIC conform unit ID, e.g., "veh_1"
     */
    InternalT fromExternalId(ExternalT externalId);

    /**
     * Resets the {@link IdTransformer}. (e.g. new Map and new counter)
     */
    void reset();

    /**
     * Simple default implementation which expects external and internal IDs to be equal.
     */
    class Identity implements IdTransformer<String, String> {
        @Override
        public String toExternalId(String internalId) {
            return internalId;
        }

        @Override
        public String fromExternalId(String externalId) {
            return externalId;
        }

        @Override
        public void reset() {
        }
    }
}
