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

package org.eclipse.mosaic.fed.sumo.ambassador;

import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.rti.TIME;

/**
 * This class is used to hold and query data of externally simulated vehicles
 * it is used by the {@link AbstractSumoAmbassador} to synchronize vehicles between
 * simulations.
 */
class ExternalVehicleState {

    /**
     * Sets the longest time between two update commands. An external state
     * is updated in SUMO whenever it has changed. If the external state has
     * not changed, it is updated every TIME_DIFFERENCE_FOR_UPDATE seconds at most.
     */
    public static final long TIME_DIFFERENCE_FOR_UPDATE = 4 * TIME.SECOND;

    /**
     * Flag to indicate whether the {@link VehicleData} of the last update is added.
     * Every {@link ExternalVehicleState} is initialized as not added and will be
     * added during simulation.
     */
    private boolean added = false;

    /**
     * Stores the time at which this state was updated in SUMO.
     * This is used to prevent too frequent updates of vehicles whose state
     * did not change, e.g. for parking vehicles.
     */
    private long timeOfLastUpdateInSumo;

    private VehicleData lastMovementInfo = null;

    /**
     * Info about the last vehicle movement.
     *
     * @param lastMovementInfo The last vehicle info.
     */
    public void setLastMovementInfo(VehicleData lastMovementInfo) {
        this.timeOfLastUpdateInSumo = 0;
        this.lastMovementInfo = lastMovementInfo;
    }

    /**
     * Sets the boolean value which indicates whether the last vehicle movement is added.
     *
     * @param added Value to indicate the status of the update.
     */
    public void setAdded(boolean added) {
        this.added = added;
    }

    /**
     * Gets the info about last vehicle movement.
     *
     * @return Last vehicle movement.
     */
    public VehicleData getLastMovementInfo() {
        return lastMovementInfo;
    }

    /**
     * Checks whether the last vehicle movement is added.
     *
     * @return True if the last vehicle movement is added.
     */
    public boolean isAdded() {
        return added;
    }

    /**
     * Returns {@code true} if this external vehicle state requires an update in SUMO.
     *
     * @param time the current simulation time
     */
    public boolean isRequireUpdate(long time) {
        return (time - timeOfLastUpdateInSumo) > TIME_DIFFERENCE_FOR_UPDATE;
    }

    /**
     * Stores the time (ns) at which this external vehicle state was last updated in SUMO.
     */
    public void updatedInSumo(long time) {
        this.timeOfLastUpdateInSumo = time;
    }
}
