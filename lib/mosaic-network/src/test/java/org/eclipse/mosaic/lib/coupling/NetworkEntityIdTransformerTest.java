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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

public class NetworkEntityIdTransformerTest {

    private NetworkEntityIdTransformer idTransformer;

    @Before
    public void setup() {
        idTransformer = new NetworkEntityIdTransformer();
    }

    @Test
    public void putElement_internalId() {
        //RUN
        idTransformer.toExternalId("nodeId");
        //ASSERT
        assertTrue(idTransformer.containsInternalId("nodeId"));
    }

    @Test
    public void accessElement_internalId() {
        //RUN
        int firstAccess = idTransformer.toExternalId("nodeId");
        int secondAccess = idTransformer.toExternalId("nodeId");
        //ASSERT
        assertEquals(firstAccess, secondAccess);
    }

    @Test
    public void accessElement_externalId() {
        //RUN
        idTransformer.toExternalId("nodeId");
        //ASSERT
        assertTrue(idTransformer.containsExternalId(0));
    }

    @Test
    public void accessNonExistentElement_externalId() {
        //RUN + ASSERT
        try {
            idTransformer.fromExternalId(9999);
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), containsString("No element with the external ID 9999"));
        }

    }

    @Test
    public void removeElement() {
        //RUN
        idTransformer.toExternalId("nodeId");
        assertTrue(idTransformer.containsInternalId("nodeId"));
        idTransformer.removeUsingInternalId("nodeId");
        //ASSERT
        assertFalse(idTransformer.containsInternalId("nodeId"));
    }

    @Test
    public void removeNonExistentElement() {
        //RUN
        Integer id = idTransformer.removeUsingInternalId("nodeId"); //Map.remove() with additional logging
        //ASSERT
        assertNull(id);
    }
}
