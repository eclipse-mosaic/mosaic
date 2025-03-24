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

package org.eclipse.mosaic.starter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.eclipse.mosaic.lib.util.InteractionUtils;
import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;
import org.eclipse.mosaic.starter.config.CRuntime;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CRuntimeTest {

    /**
     * Checks if the runtime.json which is bundled with Eclipse MOSAIC has only valid subscriptions.
     * This test requires the maven call {@code mvn process-test-resources} or {@code mvn install} each
     * time the runtime.xml has been changed in the {@code bundle} project.
     */
    @Test
    public void validSubscriptionsInRuntimeConfiguration() throws IOException, InstantiationException {
        CRuntime runtimeConfiguration;
        try (InputStream resource = new FileInputStream("etc/runtime.json")) {
            assertNotNull(resource);
            runtimeConfiguration = new ObjectInstantiation<>(CRuntime.class).read(resource);
        }
        assertNotNull(runtimeConfiguration);

        for (CRuntime.CFederate federate : runtimeConfiguration.federates) {
            assertFalse("Federate " + federate.id + " should have at least one subscription",
                    federate.subscriptions.isEmpty()
            );
            for (String subscription : federate.subscriptions) {
                assertNotNull("\"" + subscription + "\" is not a valid subscription (see federate \"" + federate.id + "\")",
                        InteractionUtils.getInteractionClassForTypeId(subscription)
                );
            }
        }
    }

}
