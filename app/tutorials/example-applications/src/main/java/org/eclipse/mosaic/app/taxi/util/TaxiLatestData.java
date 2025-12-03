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

package org.eclipse.mosaic.app.taxi.util;

import org.eclipse.mosaic.lib.objects.taxi.TaxiVehicleData;

import java.util.ArrayList;
import java.util.List;

public class TaxiLatestData {

	private TaxiVehicleData.TaxiState lastStatus;
    private final List<String> edgesToVisit = new ArrayList<>();
    private Integer currentLegId;
    private final List<Integer> nextLegIds = new ArrayList<>();

    public TaxiLatestData() {
        this.lastStatus = TaxiVehicleData.TaxiState.EMPTY;
        this.currentLegId = null;
    }

    TaxiLatestData(TaxiVehicleData.TaxiState lastStatus, List<String> edgesToVisit, Integer currentLegId, List<Integer> nextLegIds) {
        this.lastStatus = lastStatus;
        this.edgesToVisit.addAll(edgesToVisit);
        this.currentLegId = currentLegId;
        this.nextLegIds.addAll(nextLegIds);
    }

    public TaxiVehicleData.TaxiState getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(TaxiVehicleData.TaxiState lastStatus) {
        this.lastStatus = lastStatus;
    }

    public Integer getCurrentLegId() {
        return currentLegId;
    }

    public void setCurrentLegId(Integer currentLegId) {
        this.currentLegId = currentLegId;
    }

    public List<String> getEdgesToVisit() {
        return edgesToVisit;
    }

    public List<Integer> getNextLegIds() {
        return nextLegIds;
    }
}
