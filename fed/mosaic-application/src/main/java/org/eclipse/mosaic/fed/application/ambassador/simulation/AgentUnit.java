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

package org.eclipse.mosaic.fed.application.ambassador.simulation;

import org.eclipse.mosaic.fed.application.ambassador.ErrorRegister;
import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.navigation.AgentPtRoutingModule;
import org.eclipse.mosaic.fed.application.ambassador.simulation.navigation.RoutingNavigationModule;
import org.eclipse.mosaic.fed.application.app.api.AgentApplication;
import org.eclipse.mosaic.fed.application.app.api.navigation.PtRoutingModule;
import org.eclipse.mosaic.fed.application.app.api.navigation.RoutingModule;
import org.eclipse.mosaic.fed.application.app.api.os.AgentOperatingSystem;
import org.eclipse.mosaic.interactions.agent.AgentRemoval;
import org.eclipse.mosaic.interactions.agent.AgentRouteChange;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoUtils;
import org.eclipse.mosaic.lib.objects.agent.AgentData;
import org.eclipse.mosaic.lib.objects.agent.AgentRoute;
import org.eclipse.mosaic.lib.objects.mapping.AgentMapping;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleDeparture;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleRoute;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.routing.IllegalRouteException;
import org.eclipse.mosaic.lib.routing.pt.PtRoute;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import com.google.common.collect.Lists;

import java.util.List;
import javax.annotation.Nullable;

public class AgentUnit extends AbstractSimulationUnit implements AgentOperatingSystem {

    private final RoutingModule vehicleRoutingModule;
    private final PtRoutingModule ptRoutingModule;
    private final GeoPoint originPosition;
    private final GeoPoint destinationPosition;

    private AgentData agentData;

    public AgentUnit(AgentMapping agentMapping, final GeoPoint originPosition, final GeoPoint destinationPosition) {
        super(agentMapping.getName(), originPosition);
        setRequiredOperatingSystem(AgentOperatingSystem.class);

        vehicleRoutingModule = new RoutingNavigationModule(this);
        ptRoutingModule = new AgentPtRoutingModule(agentMapping.getWalkingSpeed());

        this.originPosition = originPosition;
        this.destinationPosition = destinationPosition;
    }

    @Override
    public void usePrivateVehicle(String vehicleType, CandidateRoute route) {
        try {
            VehicleRoute rtiRoute = SimulationKernel.SimulationKernel.getCentralNavigationComponent().createAndPropagateRoute(
                    route, getSimulationTime()
            );

            if (rtiRoute == null) {
                throw new IllegalRouteException("Provided route could not be propagated to RTI.");
            }

            final List<AgentRoute.Leg> agentLegs = Lists.newArrayList(new AgentRoute.PrivateVehicleLeg(
                    getSimulationTime(),
                    vehicleType,
                    new VehicleDeparture.Builder(rtiRoute.getId())
                            .departureSpeed(VehicleDeparture.DepartureSpeedMode.PRECISE, 0)
                            .create()
            ));
            sendInteractionToRti(new AgentRouteChange(
                    getSimulationTime(), getId(), new AgentRoute(agentLegs)
            ));

        } catch (IllegalRouteException e) {
            throw new RuntimeException("Invalid route provided.", e);
        }
    }

    @Override
    public void useSharedVehicle(String vehicleId) {
        final List<AgentRoute.Leg> agentLegs = Lists.newArrayList(new AgentRoute.SharedVehicleLeg(
                getSimulationTime(),
                vehicleId
        ));

        sendInteractionToRti(new AgentRouteChange(
                getSimulationTime(), getId(), new AgentRoute(agentLegs)
        ));
    }

    @Override
    public void usePublicTransport(PtRoute publicTransportRoute) {
        final List<AgentRoute.Leg> agentLegs = publicTransportRoute.getLegs().stream().map(leg -> {
            if (leg instanceof PtRoute.PtLeg ptLeg) {
                return new AgentRoute.PtLeg(leg.getDepartureTime(), ptLeg.getPtTrip());
            }
            if (leg instanceof PtRoute.WalkLeg walkLeg) {
                return new AgentRoute.WalkLeg(leg.getDepartureTime(), walkLeg.getWaypoints(), estimateDesiredWalkingSpeed(walkLeg));
            }
            throw new IllegalArgumentException("Unsupported leg type found in public transport route.");
        }).toList();

        if (agentLegs.isEmpty()) {
            return;
        }
        sendInteractionToRti(new AgentRouteChange(getSimulationTime(), getId(), new AgentRoute(agentLegs)));
    }

    @Override
    public void finishAgent() {
        sendInteractionToRti(new AgentRemoval(getSimulationTime(), getId()));
    }

    /**
     * Estimates the walking speed in m/s of a Walk leg based on the departure and arrival time.
     */
    private static double estimateDesiredWalkingSpeed(PtRoute.WalkLeg walkLeg) {
        if (walkLeg.getWaypoints().size() < 2) {
            throw new IllegalArgumentException("Invalid walking path. Requires more than two waypoints.");
        }
        double length = 0;
        for (int i = 1; i < walkLeg.getWaypoints().size(); i++) {
            length += GeoUtils.distanceBetween(walkLeg.getWaypoints().get(i - 1), walkLeg.getWaypoints().get(i));
        }
        return length / ((double) (walkLeg.getArrivalTime() - walkLeg.getDepartureTime()) / TIME.SECOND);
    }

    @Override
    public GeoPoint getOriginPosition() {
        return originPosition;
    }

    @Override
    public GeoPoint getPosition() {
        return agentData != null && agentData.getPosition() != null
                ? agentData.getPosition()
                : getInitialPosition();
    }

    @Nullable
    @Override
    public AgentData getAgentData() {
        return agentData;
    }

    @Override
    public GeoPoint getDestinationPosition() {
        return destinationPosition;
    }

    @Override
    public PtRoutingModule getPtRoutingModule() {
        return ptRoutingModule;
    }

    @Override
    public RoutingModule getRoutingModule() {
        return vehicleRoutingModule;
    }

    @Override
    public void processEvent(Event event) throws Exception {
        // never remove the preProcessEvent call!
        final boolean preProcessed = super.preProcessEvent(event);

        // failsafe
        if (preProcessed) {
            return;
        }

        final Object resource = event.getResource();

        // failsafe
        if (resource == null) {
            getOsLog().error("Event has no resource: {}", event);
            throw new RuntimeException(ErrorRegister.AGENT_NoEventResource.toString());
        }

        if (resource instanceof AgentData agentData) {
            onAgentDataUpdate(agentData);
            return;
        }

        getOsLog().error("Unknown event resource: {}", event);
        throw new RuntimeException(ErrorRegister.AGENT_UnknownEvent.toString());
    }

    private void onAgentDataUpdate(AgentData agentData) {
        final AgentData previous = this.agentData;
        final AgentRoute.Leg previousLeg = previous != null ? previous.getCurrentLeg() : null;

        this.agentData = agentData;

        for (AgentApplication application : getApplicationsIterator(AgentApplication.class)) {
            application.onAgentUpdated(previous, agentData);
            if (previousLeg != agentData.getCurrentLeg()) {
                application.onLegChanged(previousLeg, agentData.getCurrentLeg());
            }
        }
    }

    @Override
    public CamBuilder assembleCamMessage(CamBuilder camBuilder) {
        throw new UnsupportedOperationException("Agents are not able to send CAMs");
    }
}
