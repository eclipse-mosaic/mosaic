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

package org.eclipse.mosaic.fed.application.app.api.os;

import org.eclipse.mosaic.fed.application.app.api.os.modules.CellCommunicative;
import org.eclipse.mosaic.fed.application.app.api.os.modules.Locatable;
import org.eclipse.mosaic.fed.application.app.api.os.modules.PtRoutable;
import org.eclipse.mosaic.fed.application.app.api.os.modules.Routable;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.agent.AgentData;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.routing.pt.PtRoute;

import javax.annotation.Nullable;

/**
 * An agent represents a person in the simulation which is able to use public transport
 * facilities, can walk, or use private or shared vehicles for transportation. An agent does
 * not move until its mapped application provides instructions, such as using public transport
 * or a private car.<br/>
 * <br/>
 * This interface extends the basic {@link OperatingSystem} and is implemented
 * by the {@link org.eclipse.mosaic.fed.application.ambassador.simulation.AgentUnit}.
 */
public interface AgentOperatingSystem
        extends OperatingSystem, Locatable, CellCommunicative, Routable, PtRoutable {

    /**
     * Changes the next leg of the agent to use a private vehicle. It will spawn a new vehicle and assigns this
     * agent to it. The {@link #getRoutingModule()} should be used to calculate {@link CandidateRoute}s for such vehicle trip.
     */
    void usePrivateVehicle(String vehicleType, CandidateRoute route);

    /**
     * Changes the next leg of the agent to use vehicle which already exists in the simulation, such as a shared vehicle or shuttle bus.
     */
    void useSharedVehicle(String vehicleId);

    /**
     * Changes the next leg(s) of the agent to use public transportation. The provided public transport route
     * would contain one or more legs of type public transport, or walking. The {@link #getPtRoutingModule()} should be used
     * to calculate such routes.
     */
    void usePublicTransport(PtRoute publicTransportRoute);

    /**
     * Removes this agent from the simulation proactively.
     */
    void finishAgent();

    /**
     * Provides the origin position of the agent as defined in the mapping configuration.
     */
    GeoPoint getOriginPosition();

    /**
     * Provides the destination position of the agent as defined in the mapping configuration.
     */
    GeoPoint getDestinationPosition();

    /**
     * Provides access to the current agent state.
     */
    @Nullable
    AgentData getAgentData();

}
