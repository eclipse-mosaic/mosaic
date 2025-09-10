/*
 * Copyright (c) 2022 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.lib.perception;


import org.eclipse.mosaic.lib.perception.objects.SpatialObject;

import java.util.List;

/**
 * A {@link PerceptionModifier} works on the perception result to model
 * occlusion and perception errors. A modifier can filter the provided
 * list of perceived objects, and/or alter their positions/dimensions.
 */
public interface PerceptionModifier {

    /**
     * Applies the implemented filter/modifier.
     *
     * @return the filtered/modified list
     */
    <T extends SpatialObject<?>> List<T> apply(PerceptionEgo ego, List<T> spatialObjects);
}
