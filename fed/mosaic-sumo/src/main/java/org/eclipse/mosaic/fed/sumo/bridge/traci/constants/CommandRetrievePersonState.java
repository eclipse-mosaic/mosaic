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

package org.eclipse.mosaic.fed.sumo.bridge.traci.constants;

public class CommandRetrievePersonState {

    public final static int COMMAND = 0xae;

    public final static SumoVar VAR_SPEED = SumoVar.var(0x40);

    public final static SumoVar VAR_POSITION = SumoVar.var(0x42);

    public final static SumoVar VAR_POSITION_3D = SumoVar.var(0x39);

    public final static SumoVar VAR_ANGLE = SumoVar.var(0x43);

    public final static SumoVar VAR_TYPE_ID = SumoVar.var(0x4f);

}

