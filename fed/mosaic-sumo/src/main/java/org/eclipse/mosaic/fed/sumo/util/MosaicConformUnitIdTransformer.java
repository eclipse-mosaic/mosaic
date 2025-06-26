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

package org.eclipse.mosaic.fed.sumo.util;

import org.eclipse.mosaic.lib.util.objects.IdTransformer;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Several components of Eclipse MOSAIC expect the identifier of the vehicles to
 * match the following expression: ^veh_[0-9]+$. However, predefined
 * scenarios usually come with custom vehicle ids which do not match this
 * pattern, so we need to transform them into the required format which is
 * accomplished by this class.
 */
public class MosaicConformUnitIdTransformer implements IdTransformer<String, String> {

    private final static Logger log = LoggerFactory.getLogger(MosaicConformUnitIdTransformer.class);

    private final BiMap<String, String> unitIdMap = HashBiMap.create(1024);

    private final Supplier<String> nextUnitName;

    public MosaicConformUnitIdTransformer(Supplier<String> nextUnitName) {
        this.nextUnitName = nextUnitName;
    }

    /**
     * Takes a MOSAIC conform vehicle id (e.g. veh_1) and returns the saved external id in {@link #unitIdMap}.
     * If a new vehicle from MOSAIC has to be added to an external simulator we use the same id.
     *
     * @param mosaicUnitId the MOSAIC conform vehicle id
     * @return the corresponding external id
     */
    @Override
    public String toExternalId(String mosaicUnitId) {
        String externalVehicleId = unitIdMap.inverse().get(mosaicUnitId);
        if (externalVehicleId == null) {
            unitIdMap.inverse().put(mosaicUnitId, mosaicUnitId);
            externalVehicleId = mosaicUnitId; // return incoming id
        }
        return externalVehicleId;
    }

    /**
     * Takes an external vehicle id, creates a MOSAIC-conform vehicle id and adds it to {@link #unitIdMap}.
     *
     * @param externalUnitId the id from the external traffic/vehicle simulator
     * @return the created MOSAIC conform vehicle id
     */
    @Override
    public String fromExternalId(String externalUnitId) {
        String mosaicUnitId = unitIdMap.get(externalUnitId);
        if (mosaicUnitId == null) {
            mosaicUnitId = nextUnitName.get();
            unitIdMap.put(externalUnitId, mosaicUnitId);
            log.info("Assigned unit MOSAIC-ID: \"{}\" to external unit: \"{}\"", mosaicUnitId, externalUnitId);
        }
        return mosaicUnitId;
    }

    @Override
    public void reset() {
        unitIdMap.clear();
    }
}