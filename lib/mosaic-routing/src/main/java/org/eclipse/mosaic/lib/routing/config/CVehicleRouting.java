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

package org.eclipse.mosaic.lib.routing.config;

import java.io.Serializable;

/**
 * Base Class for the navigation configuration.
 */
public class CVehicleRouting implements Serializable {

    /**
     * The source for the route calculation, e.g., the path to the database containing the road network.
     */
    public String source = null;

}
