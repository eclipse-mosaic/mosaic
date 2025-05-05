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

package org.eclipse.mosaic.fed.sumo.bridge.traci.reader;

import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TaxiReservationTraciReader extends AbstractTraciResultReader<TaxiReservation> {

    public TaxiReservationTraciReader() {
        super(null);
    }

    @Override
    protected TaxiReservation readFromStream(DataInputStream in) throws IOException {
        readTypedInt(in); //COMPOUND type of 10 items

        final String reservationId = readTypedString(in);
        final List<String> personList = readTypedStringList(in);
        final String group = readTypedString(in);
        final String fromEdge = readTypedString(in);
        final String toEdge = readTypedString(in);
        final double departPos = readTypedDouble(in);
        final double arrivalPos = readTypedDouble(in);
        final double depart = readTypedDouble(in);
        final double reservationTime = readTypedDouble(in);
        final int reservationState = readTypedInt(in); //TODO

        return new TaxiReservation.Builder().withId(reservationId)
                .withPersonList(personList)
                .withGroup(group)
                .withFromEdge(fromEdge)
                .withToEdge(toEdge)
                .withDepartPos(departPos)
                .withArrivalPos(arrivalPos)
                .withDepart(depart)
                .withReservationTime(reservationTime)
                .build();
    }



    private int readTypedInt(DataInputStream in) throws IOException {
        readByte(in);
        return readInt(in);
    }

    private double readTypedDouble(DataInputStream in) throws IOException {
        readByte(in);
        return readDouble(in);
    }

    private String readTypedString(DataInputStream in) throws IOException {
        readByte(in);
        return readString(in);
    }

    private List<String> readTypedStringList(DataInputStream in) throws IOException {
        readByte(in);
        List<String> result = new ArrayList<>();
        int len = readInt(in);
        for (int i = 0; i < len; i++) {
            readString(in);
        }
        return result;
    }
}
