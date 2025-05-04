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
import java.util.List;

public class TaxiReservationTraciReader extends AbstractTraciResultReader<TaxiReservation> {

    public TaxiReservationTraciReader() {
        super(null);
    }

    @Override
    protected TaxiReservation readFromStream(DataInputStream in) throws IOException {
        final String reservationId = readString(in);
        final String personList = readString(in);
        final String group = readString(in);
        final String fromEdge = readString(in);
        final String toEdge = readString(in);
        final double departPos = readDouble(in);
        final double arrivalPos = readDouble(in);
        final double depart = readDouble(in);
        final double reservationTime = readDouble(in);

        return new TaxiReservation.Builder().withId(reservationId)
            .withPersonList(List.of(personList))
            .withGroup(group)
            .withFromEdge(fromEdge)
            .withToEdge(toEdge)
            .withDepartPos(departPos)
            .withArrivalPos(arrivalPos)
            .withDepart(depart)
            .withReservationTime(reservationTime)
            .build();
    }
}
