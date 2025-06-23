/*
 * Copyright (c) 2024 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.application.app.api.os.modules;

import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.SimplePerceptionConfiguration;
import org.eclipse.mosaic.fed.application.app.api.perception.BasicSensorModule;
import org.eclipse.mosaic.fed.application.app.api.perception.LidarSensorModule;
import org.eclipse.mosaic.fed.application.app.api.perception.PerceptionModule;
import org.eclipse.mosaic.lib.objects.environment.Sensor;

/**
 * Interface to mark an {@link org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem} as
 * an owner of a {@link PerceptionModule} to perceive surrounding vehicles, as well as an owner
 * of a {@link BasicSensorModule} and {@link LidarSensorModule} to sense data from the surrounding environment.
 */
public interface Perceptive {

    /**
     * Returns a perception module for this unit, which is able to return object information of its surrounding.
     */
    PerceptionModule<SimplePerceptionConfiguration> getPerceptionModule();

    /**
     * Returns a basic sensor module which provides single integer values for given {@link Sensor}s.
     * Can be used, e.g., to detect preconfigured icy roads, or rainy areas in conjunction with the mosaic-environment simulator.
     */
    BasicSensorModule getBasicSensorModule();

    /**
     * Returns a LiDAR sensor module which provides complex 3D {@link org.eclipse.mosaic.lib.spatial.PointCloud} data.
     * To be able to retrieve such data, a sensor model must be provided by a coupled simulator, such as PHABMACS or Carla.
     */
    LidarSensorModule getLidarSensorModule();

}
