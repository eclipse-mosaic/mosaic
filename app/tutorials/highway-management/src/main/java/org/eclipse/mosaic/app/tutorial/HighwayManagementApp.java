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

package org.eclipse.mosaic.app.tutorial;

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.tmc.InductionLoop;
import org.eclipse.mosaic.fed.application.ambassador.simulation.tmc.LaneAreaDetector;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.TrafficManagementCenterApplication;
import org.eclipse.mosaic.fed.application.app.api.os.TrafficManagementCenterOperatingSystem;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleRoute;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import java.util.Collection;

public class HighwayManagementApp extends AbstractApplication<TrafficManagementCenterOperatingSystem> implements TrafficManagementCenterApplication {

    private final String routeIdToClose;
    private final int numberOfLanesToClose;

    public HighwayManagementApp(String routeToClose, int numberOfLanesToClose) {
        this.routeIdToClose = routeToClose;
        this.numberOfLanesToClose = numberOfLanesToClose;
    }

    @Override
    public void onStartup() {
        VehicleRoute routeToClose = SimulationKernel.SimulationKernel.getRoutes().get(routeIdToClose);
        if (routeToClose == null) {
            throw new IllegalArgumentException("Could not find route with id=" + routeIdToClose + " in route cache.");
        }

        // On tenth second of simulation, close the lanes along the given route
        getOs().getEventManager().addEvent(10 * TIME.SECOND, (e) ->
                closeEdges(routeToClose, numberOfLanesToClose)
        );

        // On 300th second of simulation, open the lanes along the given route
        getOs().getEventManager().addEvent(300 * TIME.SECOND, (e) ->
                openEdges(routeToClose, numberOfLanesToClose)
        );
    }

    private void closeEdges(VehicleRoute routeToClose, int numberOfLanesToClose) {
        getLog().info("Closing {} lanes along route {}", numberOfLanesToClose, routeToClose.getId());

        for (String edge : routeToClose.getConnectionIds()) {
            for (int lane = 0; lane < numberOfLanesToClose; lane++) {
                getOs().changeLaneState(edge, lane).closeForAll();
            }
        }
    }

    private void openEdges(VehicleRoute routeToClose, int numberOfLanesToClose) {
        getLog().info("Opening {} lanes along route {}", numberOfLanesToClose, routeToClose.getId());

        for (String edge : routeToClose.getConnectionIds()) {
            for (int lane = 0; lane < numberOfLanesToClose; lane++) {
                getOs().changeLaneState(edge, lane).openForAll();
            }
        }
    }

    @Override
    public void onInductionLoopUpdated(Collection<InductionLoop> updatedInductionLoops) {
        for (InductionLoop item : getOs().getInductionLoops()) {
            getLog().infoSimTime(this, "Detector '{}': average speed: {} m/s, traffic flow: {} veh/h", item.getId(), item.getAverageSpeedMs(), item.getTrafficFlowVehPerHour());
        }
    }

    @Override
    public void onLaneAreaDetectorUpdated(Collection<LaneAreaDetector> updatedLaneAreaDetectors) {
        for (LaneAreaDetector item : getOs().getLaneAreaDetectors()) {
            getLog().infoSimTime(this, "Segment '{}': average speed: {} m/s, traffic density: {} veh/km", item.getId(), item.getMeanSpeed(), item.getTrafficDensity());
        }
    }

    @Override
    public void onShutdown() {
    }

    @Override
    public void processEvent(Event event) {
    }
}
