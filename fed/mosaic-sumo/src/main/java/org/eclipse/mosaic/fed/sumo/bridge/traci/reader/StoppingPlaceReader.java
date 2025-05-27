/*
 * Copyright (c) 2023 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.sumo.bridge.traci.reader;

import org.eclipse.mosaic.lib.enums.VehicleStopMode;
import org.eclipse.mosaic.lib.objects.pt.PtVehicleData;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StoppingPlaceReader extends AbstractTraciResultReader<List<PtVehicleData.StoppingPlace>> {

    /**
     * Use this mode to read "next stops" from SUMO versions <= 1.22.0.
     */
    private final boolean legacyMode;

    public StoppingPlaceReader() {
        this(false);
    }

    public StoppingPlaceReader(boolean legacyMode) {
        super(null);
        this.legacyMode = legacyMode;
    }

    @Override
    protected List<PtVehicleData.StoppingPlace> readFromStream(DataInputStream in) throws IOException {
        int count = readIntWithType(in);
        List<PtVehicleData.StoppingPlace> stoppingPlaces = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PtVehicleData.StoppingPlace.Builder stoppingPlaceBuilder = new PtVehicleData.StoppingPlace.Builder();
            stoppingPlaceBuilder.laneId(readStringWithType(in));
            stoppingPlaceBuilder.endPos(readDoubleWithType(in));
            stoppingPlaceBuilder.stoppingPlaceId(readStringWithType(in));
            stoppingPlaceBuilder.stopFlags(VehicleStopMode.fromSumoInt(readIntWithType(in)));
            stoppingPlaceBuilder.stopDuration(readDoubleWithType(in));
            stoppingPlaceBuilder.stoppedUntil(readDoubleWithType(in));

            if (!legacyMode) { // since SUMO 1.23.0, more stop data is returned
                stoppingPlaceBuilder.startPos(readDoubleWithType(in)); // startpos
                readDoubleWithType(in); // intended arrival
                readDoubleWithType(in); // arrival
                readDoubleWithType(in); // depart
                readStringWithType(in); // split
                readStringWithType(in); // join
                readStringWithType(in); // actType
                readStringWithType(in); // tripId
                readStringWithType(in); // lineId
                readDoubleWithType(in); // speed
            }
            stoppingPlaces.add(stoppingPlaceBuilder.build());
        }
        return stoppingPlaces;
    }

    private String readStringWithType(DataInputStream in) throws IOException {
        readByte(in);
        return readString(in);
    }

    private double readDoubleWithType(DataInputStream in) throws IOException {
        readByte(in);
        return readDouble(in);
    }

    private int readIntWithType(DataInputStream in) throws IOException {
        readByte(in);
        return readInt(in);
    }
}
