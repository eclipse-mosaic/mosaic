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

package org.eclipse.mosaic.app.taxi.util;

import org.eclipse.mosaic.lib.objects.UnitNameGenerator;
import org.eclipse.mosaic.lib.objects.UnitType;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class ParserUtil {

    /* FIXME the whole ID-matching needs re-thinking. Currently, taxi/cabs are added in a preprocessing step using python. This
     * process currently uses vehicle IDs from the rou.xml, converts them to long and inserts them as cab-ids to the KABINA database.
     * This should be refactored in some way, that taxis/cabs are added to the database dynamically during startup. */

    public static long parsePerson(List<String> personList) {
        String person = personList.get(0);
        if (UnitNameGenerator.isAgent(person)) {
            return Long.parseLong(StringUtils.substringAfter(person, UnitType.AGENT.prefix + "_"));
        }
        person = person.substring(1);
        return Long.parseLong(person);
    }

    public static String parseTaxiDbIndexToMosaicVehicleId(long taxiDbId) {
        taxiDbId -= 1;
        return UnitType.VEHICLE.prefix + "_" + taxiDbId;
    }

    public static Integer parseMosaicVehicleIdToTaxiDbIndex(String mosaicVehicleId) {
        mosaicVehicleId = StringUtils.substringAfter(mosaicVehicleId, UnitType.VEHICLE.prefix + "_");
        return Integer.parseInt(mosaicVehicleId) + 1; //db indices start from 1 not from 0 like in Mosaic
    }
}
