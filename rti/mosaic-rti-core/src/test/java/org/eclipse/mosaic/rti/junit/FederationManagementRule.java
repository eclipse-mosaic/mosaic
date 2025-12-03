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

package org.eclipse.mosaic.rti.junit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.mosaic.rti.api.FederateAmbassador;
import org.eclipse.mosaic.rti.api.FederationManagement;

import com.google.common.collect.Lists;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class FederationManagementRule implements TestRule {

    private final FederationManagement fedManagerMock = mock(FederationManagement.class);
    private final Map<String, FederateAmbassador> ambassadorMocks;

    public FederationManagementRule(String... ambassadorIds) {
        ambassadorMocks = new HashMap<>();
        for (String ambassadorId : ambassadorIds) {
            FederateAmbassador ambassador = mock(FederateAmbassador.class);
            when(ambassador.getPriority()).thenReturn((byte) 50);
            ambassadorMocks.put(ambassadorId, ambassador);
        }
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {

                when(fedManagerMock.getAmbassadors()).thenReturn(Lists.newArrayList(ambassadorMocks.values()));
                for (Entry<String, FederateAmbassador> entry : ambassadorMocks.entrySet()) {
                    when(fedManagerMock.getAmbassador(eq(entry.getKey()))).thenReturn(entry.getValue());
                    when(fedManagerMock.isFederateJoined(eq(entry.getKey()))).thenReturn(true);
                }
                base.evaluate();
            }
        };
    }

    public FederationManagement getFederationManagementMock() {
        return fedManagerMock;
    }

    public FederateAmbassador getAmbassador(String ambassadorMockId) {
        return ambassadorMocks.get(ambassadorMockId);
    }

}
