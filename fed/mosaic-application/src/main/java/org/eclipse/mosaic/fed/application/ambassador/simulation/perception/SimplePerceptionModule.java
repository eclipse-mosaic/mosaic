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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception;

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.lib.database.Database;
import org.eclipse.mosaic.lib.perception.PerceptionConfiguration;
import org.eclipse.mosaic.lib.perception.PerceptionEgo;
import org.eclipse.mosaic.lib.perception.SimplePerceptionModel;
import org.eclipse.mosaic.lib.perception.objects.BuildingWall;
import org.eclipse.mosaic.lib.perception.objects.SpatialObject;
import org.eclipse.mosaic.lib.perception.objects.TrafficLightObject;
import org.eclipse.mosaic.lib.perception.objects.VehicleObject;

import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A perception module which detects all vehicles within the defined field of view.
 */
public class SimplePerceptionModule extends AbstractPerceptionModule {

    private SimplePerceptionModel perceptionModel;

    public SimplePerceptionModule(PerceptionEgo ego, Database database, Logger log) {
        super(ego, database, log);
    }

    @Override
    public void enable(PerceptionConfiguration configuration) {
        super.enable(configuration);
        perceptionModel = new SimplePerceptionModel(ego.getId(), this.configuration);
    }

    @Override
    List<VehicleObject> getVehiclesInRange() {
        if (perceptionModel == null || ego.getProjectedPosition() == null) {
            log.warn("No perception model initialized.");
            return Lists.newArrayList();
        }
        perceptionModel.updateOrigin(ego);
        // note, the perception index is updated internally only if vehicles have moved since the last call
        SimulationKernel.SimulationKernel.getCentralPerceptionComponent().updateSpatialIndices();
        // request all vehicles within the area of the field of view
        return SimulationKernel.SimulationKernel.getCentralPerceptionComponent()
                .getPerceptionIndex()
                .getVehiclesInRange(perceptionModel);
    }

    @Override
    public List<TrafficLightObject> getTrafficLightsInRange() {
        if (perceptionModel == null || ego.getProjectedPosition() == null) {
            log.warn("No perception model initialized.");
            return Lists.newArrayList();
        }
        perceptionModel.updateOrigin(ego);
        // note, the perception index is updated internally only if vehicles have moved since the last call
        SimulationKernel.SimulationKernel.getCentralPerceptionComponent().updateSpatialIndices();
        // request all vehicles within the area of the field of view
        return SimulationKernel.SimulationKernel.getCentralPerceptionComponent()
                .getPerceptionIndex()
                .getTrafficLightsInRange(perceptionModel);

    }

    @Override
    public List<SpatialObject<?>> getObjectsInRange() {
        if (perceptionModel == null || ego.getProjectedPosition() == null) {
            log.warn("No perception model initialized.");
            return Lists.newArrayList();
        }
        perceptionModel.updateOrigin(ego);
        SimulationKernel.SimulationKernel.getCentralPerceptionComponent().updateSpatialIndices();
        List<SpatialObject<?>> objectsInRange = new ArrayList<>();
        objectsInRange.addAll(SimulationKernel.SimulationKernel.getCentralPerceptionComponent()
                .getPerceptionIndex()
                .getVehiclesInRange(perceptionModel));
        objectsInRange.addAll(SimulationKernel.SimulationKernel.getCentralPerceptionComponent()
                .getPerceptionIndex()
                .getTrafficLightsInRange(perceptionModel));
        return objectsInRange;
    }

    @Override
    public Collection<BuildingWall> getSurroundingWalls() {
        return SimulationKernel.SimulationKernel.getCentralPerceptionComponent()
                .getPerceptionIndex().getSurroundingWalls(perceptionModel);
    }
}
