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

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.lib.util.objects.IdTransformer;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * This class reads a person ID from the TraCI connection during
 * command execution. The person ID read out by this writer is
 * transformed according to the configured {@link IdTransformer}.
 *
 * @see Bridge#PERSON_ID_TRANSFORMER
 */
public class PersonIdTraciReader extends StringTraciReader {

    @Override
    protected String readFromStream(DataInputStream in) throws IOException {
        return Bridge.PERSON_ID_TRANSFORMER.fromExternalId(super.readFromStream(in));
    }
}
