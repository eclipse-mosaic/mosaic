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

package org. eclipse.mosaic.fed.output.generator.file.format;

import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.rti.api.Interaction;

import java.util.List;

public class MyInteraction extends Interaction {

    private static final long serialVersionUID = 1L;

    public final static String TYPE_ID = createTypeIdentifier(MyInteraction.class);

    private final List<VehicleUpdates> interactions;

    MyInteraction(List<VehicleUpdates> interactions) {
        super(0);
        this.interactions = interactions;
    }

    public List<VehicleUpdates> getMessageList() {
        return interactions;
    }

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }
}

