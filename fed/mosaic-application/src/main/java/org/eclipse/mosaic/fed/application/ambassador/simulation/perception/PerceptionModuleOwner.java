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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception;

import org.eclipse.mosaic.fed.application.ambassador.simulation.VehicleUnit;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.perception.PerceptionEgo;
import org.eclipse.mosaic.lib.perception.objects.BuildingWall;
import org.eclipse.mosaic.rti.api.Interaction;

import java.util.Collection;

public interface PerceptionModuleOwner extends PerceptionEgo {

    VehicleData getVehicleData();

    long getSimulationTime();

    /**
     * Sends the given {@link Interaction} to the runtime infrastructure.
     *
     * @param interaction the {@link Interaction} to be sent
     */
    void sendInteractionToRti(Interaction interaction);

    /**
     * Adapter for Vehicle Units to provide access to necessary data and functions for the {@link SimplePerceptionModule}.
     */
    class VehicleUnitAdapter implements PerceptionModuleOwner {

        private final VehicleUnit unit;

        public VehicleUnitAdapter(VehicleUnit unit) {
            this.unit = unit;
        }

        @Override
        public VehicleData getVehicleData() {
            return unit.getVehicleData();
        }

        @Override
        public long getSimulationTime() {
            return unit.getSimulationTime();
        }

        @Override
        public void sendInteractionToRti(Interaction interaction) {
            unit.sendInteractionToRti(interaction);
        }

        @Override
        public String getId() {
            return unit.getId();
        }

        @Override
        public CartesianPoint getProjectedPosition() {
            return getVehicleData() != null ? getVehicleData().getProjectedPosition() : null;
        }

        @Override
        public double getHeading() {
            return getVehicleData() != null ? getVehicleData().getHeading() : 0d;
        }

        @Override
        public double getViewingRange() {
            return unit.getPerceptionModule().getConfiguration().getViewingRange();
        }

        @Override
        public Collection<BuildingWall> getSurroundingWalls() {
            return unit.getPerceptionModule().getSurroundingWalls();
        }
    }

}
