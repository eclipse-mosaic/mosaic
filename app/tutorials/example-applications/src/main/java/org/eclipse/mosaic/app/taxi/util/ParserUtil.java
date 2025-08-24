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

import java.util.List;

public class ParserUtil {

	private static final String VEHICLE_MOSAIC_ID_PREFIX = "veh_";
	private static final int VEHICLE_ID_PREFIX_LENGTH = VEHICLE_MOSAIC_ID_PREFIX.length();

	public static long parsePerson(List<String> personList) {
		String person = personList.get(0);
		person = person.substring(1);
		return Long.parseLong(person);
	}

	public static String parseTaxiDbIndexToMosaicVehicleId(long taxiDbId) {
		taxiDbId -= 1;
		return VEHICLE_MOSAIC_ID_PREFIX + taxiDbId;
	}

	public static Integer parseMosaicVehicleIdToTaxiDbIndex(String mosaicVehicleId) {
		mosaicVehicleId = mosaicVehicleId.substring(VEHICLE_ID_PREFIX_LENGTH);
		return Integer.parseInt(mosaicVehicleId) + 1; //db indices start from 1 not from 0 like in Mosaic
	}
}
