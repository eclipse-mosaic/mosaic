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

package org.eclipse.mosaic.fed.sumo.bridge.traci.reader;

import org.eclipse.mosaic.lib.objects.taxi.TaxiReservation;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;

public class TaxiReservationTraciReader extends AbstractTraciResultReader<TaxiReservation> {

    private final ListTraciReader<String> personListReader = new ListTraciReader<>(new PersonIdTraciReader(), true);

    public TaxiReservationTraciReader() {
        super(null);
    }

    @Override
    protected TaxiReservation readFromStream(DataInputStream in) throws IOException {
        readTypedInt(in); //COMPOUND type of 10 items

        final String reservationId = readTypedString(in);
        final List<String> personList = personListReader.readFromStream(in);
        numBytesRead += personListReader.getNumberOfBytesRead();

        readTypedString(in); // group
        final String fromEdge = readTypedString(in);
        final String toEdge = readTypedString(in);
        readTypedDouble(in); //departPos
        readTypedDouble(in); //arrivalPos
        readTypedDouble(in); //departTime
        readTypedDouble(in); //reservationTime
        final int reservationState = readTypedInt(in);

        return new TaxiReservation.Builder().withId(reservationId)
                .withState(TaxiReservation.ReservationState.of(reservationState))
                .withPersonList(personList)
                .withFromEdge(fromEdge)
                .withToEdge(toEdge)
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
}
