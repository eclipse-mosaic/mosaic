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

import org.eclipse.mosaic.fed.application.app.api.perception.PerceptionModule;
import org.eclipse.mosaic.lib.database.Database;
import org.eclipse.mosaic.lib.perception.PerceptionConfiguration;
import org.eclipse.mosaic.lib.perception.PerceptionEgo;
import org.eclipse.mosaic.lib.perception.PerceptionModifier;
import org.eclipse.mosaic.lib.perception.objects.SpatialObject;
import org.eclipse.mosaic.lib.perception.objects.TrafficLightObject;
import org.eclipse.mosaic.lib.perception.objects.VehicleObject;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPerceptionModule implements PerceptionModule {

    private static final double DEFAULT_VIEWING_ANGLE = 40;
    private static final double DEFAULT_VIEWING_RANGE = 200;

    protected final PerceptionEgo ego;
    protected final Logger log;
    protected final Database database;

    protected PerceptionConfiguration configuration;

    AbstractPerceptionModule(PerceptionEgo ego, Database database, Logger log) {
        this.ego = ego;
        this.log = log;
        this.database = database;
    }

    @Override
    public void enable(PerceptionConfiguration configuration) {
        if (configuration == null) {
            log.warn("Provided perception configuration is null. Using default configuration with viewingAngle={}°, viewingRange={}m.",
                    DEFAULT_VIEWING_ANGLE, DEFAULT_VIEWING_RANGE);
            this.configuration = new PerceptionConfiguration.Builder(DEFAULT_VIEWING_ANGLE, DEFAULT_VIEWING_ANGLE).build();
        } else {
            this.configuration = configuration;
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfiguration() != null;
    }

    @Override
    public PerceptionConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    public List<VehicleObject> getPerceivedVehicles() {
        return applyPerceptionModifiers(getVehiclesInRange());
    }

    /**
     * As an intermediate step, this method returns all vehicles in range without applying
     * any perception modifiers.
     *
     * @return the raw list of vehicles in range of the ego vehicle
     */
    abstract List<VehicleObject> getVehiclesInRange();

    @Override
    public List<TrafficLightObject> getPerceivedTrafficLights() {
        // TODO: we could add an additional filter here to check the traffic lights' orientation
        return applyPerceptionModifiers(getTrafficLightsInRange());
    }

    /**
     * As an intermediate step, this method returns all traffic lights in range without applying
     * any perception modifiers.
     *
     * @return the raw list of traffic lights in range of the ego vehicle
     */
    abstract List<TrafficLightObject> getTrafficLightsInRange();

    @Override
    public List<SpatialObject<?>> getPerceivedObjects() {
        List<SpatialObject<?>> objectsInRange = getObjectsInRange();
        objectsInRange = applyPerceptionModifiers(objectsInRange);
        return objectsInRange;
    }

    /**
     * As an intermediate step, this method returns all spatial objects in range without applying
     * any perception modifiers.
     *
     * @return the raw list of objects in range of the ego vehicle
     */
    abstract List<SpatialObject<?>> getObjectsInRange();

    private <T extends SpatialObject<?>> List<T> applyPerceptionModifiers(List<T> objectsInRange) {
        List<T> filteredList = new ArrayList<>(objectsInRange);
        // create a copy of all perceived objects to avoid interference with modifiers of other perception modules.
        //noinspection unchecked
        filteredList.replaceAll(object -> (T) object.copy());
        for (PerceptionModifier perceptionModifier : configuration.getPerceptionModifiers()) {
            filteredList = perceptionModifier.apply(ego, filteredList); // apply filters in sequence
        }
        return filteredList;
    }
}
