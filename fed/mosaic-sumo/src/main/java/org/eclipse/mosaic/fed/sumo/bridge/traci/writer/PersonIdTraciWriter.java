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

package org.eclipse.mosaic.fed.sumo.bridge.traci.writer;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.lib.util.objects.IdTransformer;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * This class writes a vehicle ID to the TraCI connection during
 * command execution. The vehicle ID passed to this writer is
 * transformed according to the configured {@link IdTransformer}.
 *
 * @see Bridge#VEHICLE_ID_TRANSFORMER
 */
public class PersonIdTraciWriter extends StringTraciWriter {

    public PersonIdTraciWriter() {
        super();
    }

    @Override
    public int getVariableLength(String argument) {
        return super.getVariableLength(Bridge.PERSON_ID_TRANSFORMER.toExternalId(argument));
    }

    @Override
    public void writeVariableArgument(DataOutputStream out, String argument) throws IOException {
        super.writeVariableArgument(out, Bridge.PERSON_ID_TRANSFORMER.toExternalId(argument));
    }
}
