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

package org.eclipse.mosaic.lib.objects.v2x.etsi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.eclipse.mosaic.lib.enums.EventCause;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoPolygon;
import org.eclipse.mosaic.lib.junit.EtsiPayloadConfigurationRule;
import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.rti.TIME;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DenmContentTest {

    @Rule
    public EtsiPayloadConfigurationRule messageConf = new EtsiPayloadConfigurationRule();

    @Test
    public void decodeMessage() throws IOException {
        //SETUP
        GeoPoint position = GeoPoint.latLon(52.5, 13.3);

        List<GeoPoint> event = new ArrayList<>();
        event.add(GeoPoint.latLon(52.6, 13.4));
        event.add(GeoPoint.latLon(52.7, 13.5));
        event.add(GeoPoint.latLon(52.8, 13.6));
        GeoPolygon eventArea = new GeoPolygon(event);

        //First test scenario:
        Denm denm = new Denm(mock(MessageRouting.class),
                new DenmContent(4 * TIME.SECOND, position, "1_1_2_0", EventCause.ACCIDENT, 5f, 6f, position, eventArea, "test"),
                200);

        EncodedPayload encodedMessage = denm.getPayload();

        //PRE-ASSERT
        assertTrue(encodedMessage.getBytes().length > 0);

        //RUN
        DenmContent decodedDENM = (DenmContent) encodedMessage.decodePayload();

        //ASSERT
        assertNotNull(decodedDENM);
        assertEquals(denm.getTime(), decodedDENM.getTime());
        assertEquals(denm.getEventRoadId(), decodedDENM.getEventRoadId());
        assertEquals(denm.getSenderPosition(), decodedDENM.getSenderPosition());
        assertEquals(denm.getSenderDeceleration(), decodedDENM.getSenderDeceleration(), 0.0001f);
        assertEquals(denm.getCausedSpeed(), decodedDENM.getCausedSpeed(), 0.0001f);
        assertSame(denm.getEventCause(), decodedDENM.getEventCause());
        assertEquals(denm.getEventLocation(), decodedDENM.getEventLocation());
        assertEquals(denm.getEventArea(), decodedDENM.getEventArea());
        assertEquals(denm.getExtendedContainer(), decodedDENM.getExtendedContainer());

        //Second test scenario:
        Denm denm2 = new Denm(mock(MessageRouting.class),
                new DenmContent(4 * TIME.SECOND, position, "1_1_2_0", EventCause.ACCIDENT, 5f, 6f, null, null, null),
                200);

        EncodedPayload encodedMessage2 = denm2.getPayload();

        //RUN
        DenmContent decodedDENM2 = (DenmContent) encodedMessage2.decodePayload();

        //ASSERT
        assertEquals(denm2.getEventLocation(), decodedDENM2.getEventLocation());
        assertEquals(denm2.getExtendedContainer(), decodedDENM2.getExtendedContainer());
    }

}
