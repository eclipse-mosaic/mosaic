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

package org.eclipse.mosaic.lib.coupling;

import static org.junit.Assert.assertEquals;

import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;

import org.junit.Test;

public class CAbstractNetworkAmbassadorTest {

    @Test
    public void tiergartenOmnetpp() throws InstantiationException {
        new ObjectInstantiation<>(CAbstractNetworkAmbassador.class).read(
                getClass().getResourceAsStream("/Tiergarten/omnetpp_config.json"), CAbstractNetworkAmbassador.createConfigBuilder()
        );
    }

    @Test
    public void tiergartenNs3() throws InstantiationException {
        new ObjectInstantiation<>(CAbstractNetworkAmbassador.class).read(
                getClass().getResourceAsStream("/Tiergarten/ns3_config.json"), CAbstractNetworkAmbassador.createConfigBuilder()
        );
    }

    @Test
    public void withBaseStations() throws InstantiationException {
        CAbstractNetworkAmbassador config = new ObjectInstantiation<>(CAbstractNetworkAmbassador.class).read(
                getClass().getResourceAsStream("/ns3_config_with_basestations.json"), CAbstractNetworkAmbassador.createConfigBuilder()
        );
        assertEquals(2, config.baseStations.size());
        assertEquals(52.5131, config.baseStations.get(0).geoPosition.getLatitude(), 0.0001d);
        assertEquals(13.3249, config.baseStations.get(0).geoPosition.getLongitude(), 0.0001d);

        assertEquals(719.3, config.baseStations.get(1).cartesianPosition.getX(), 0.1d);
        assertEquals(118.2, config.baseStations.get(1).cartesianPosition.getY(), 0.1d);
    }
}
