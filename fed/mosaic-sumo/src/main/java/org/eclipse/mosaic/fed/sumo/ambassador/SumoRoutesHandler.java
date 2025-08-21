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

package org.eclipse.mosaic.fed.sumo.ambassador;

import org.eclipse.mosaic.interactions.traffic.VehicleRoutesInitialization;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteRegistration;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleRoute;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All the ambassador's logic which takes care of routes.
 */
public class SumoRoutesHandler extends AbstractHandler {


    /**
     * This contains references to all {@link VehicleRoute}s that are known to SUMO.
     */
    final HashMap<String, VehicleRoute> routes = new HashMap<>();

    /**
     * This processes a {@link VehicleRouteRegistration} that have been dynamically created.
     *
     * @param vehicleRouteRegistration Interaction containing information about an added route.
     */
    synchronized void handleRouteRegistration(VehicleRouteRegistration vehicleRouteRegistration) throws InternalFederateException {
        VehicleRoute newRoute = vehicleRouteRegistration.getRoute();
        propagateRouteIfAbsent(newRoute.getId(), newRoute);
    }

    /**
     * Read Routes priorly defined in Sumo route-file to later make them available to the rest of the
     * simulations using {@link VehicleRouteRegistration}.
     *
     * @throws InternalFederateException if Traci connection couldn't be established
     */
    void readInitialRoutesFromTraci(long nextTimeStep) throws InternalFederateException {
        for (String id : bridge.getRouteControl().getRouteIds()) {
            if (!routes.containsKey(id)) {
                VehicleRoute route = readRouteFromTraci(id);
                routes.put(route.getId(), route);
                // propagate new route
                final VehicleRouteRegistration vehicleRouteRegistration = new VehicleRouteRegistration(nextTimeStep, route);
                try {
                    rti.triggerInteraction(vehicleRouteRegistration);
                } catch (IllegalValueException e) {
                    throw new InternalFederateException(e);
                }
            }
        }
    }

    /**
     * Passes on initial routes (e.g., from scenario database) to SUMO.
     *
     * @throws InternalFederateException if there was a problem with traci
     */
    void addInitialRoutesFromRti(VehicleRoutesInitialization vehicleRoutesInitialization) throws InternalFederateException {
        for (Map.Entry<String, VehicleRoute> routeEntry : vehicleRoutesInitialization.getRoutes().entrySet()) {
            propagateRouteIfAbsent(routeEntry.getKey(), routeEntry.getValue());
        }
    }

    String cutAndAddRoute(String routeId, int departIndex) throws InternalFederateException {
        String newRouteId = routeId + "_cut" + departIndex;
        if (routes.containsKey(newRouteId)) {
            return newRouteId;
        }
        final VehicleRoute route = routes.get(routeId);
        final List<String> connections = route.getConnectionIds();
        if (departIndex >= connections.size()) {
            throw new IllegalArgumentException("The departIndex=" + departIndex + " is too large for route with id=" + routeId);
        }
        final VehicleRoute cutRoute =
                new VehicleRoute(newRouteId, connections.subList(departIndex, connections.size()), route.getNodeIds(), route.getLength());
        propagateRouteIfAbsent(newRouteId, cutRoute);
        return newRouteId;
    }

    /**
     * Propagates the route (e.g., from scenario database) to SUMO using the configured bridge.
     *
     * @param routeId ID of the route
     * @param route   route definition
     * @throws InternalFederateException thrown if connection to bridge failed
     */
    private void propagateRouteIfAbsent(String routeId, VehicleRoute route) throws InternalFederateException {
        // if the route is already known (because it is defined in a route-file) don't add route
        if (routes.containsKey(routeId)) {
            log.debug("Could not add route \"{}\", because it is already known to SUMO.", routeId);
        } else {
            routes.put(routeId, route);
            bridge.getRouteControl().addRoute(routeId, route.getConnectionIds());
        }
    }

    /**
     * This handles the case that sumo handles routing and creates new routes while doing so.
     *
     * @param vehicleUpdates Vehicle movement in the simulation.
     * @param time           Time at which the vehicle has moved.
     * @throws InternalFederateException Exception if an error occurred while propagating new routes.
     */
    void propagateNewRoutes(VehicleUpdates vehicleUpdates, long time) throws InternalFederateException {
        // cache all new routes
        ArrayList<VehicleRoute> newRoutes = new ArrayList<>();

        // check added vehicles for new routes
        for (VehicleData vehicleData : vehicleUpdates.getAdded()) {
            if (!routes.containsKey(vehicleData.getRouteId())) {
                newRoutes.add(readRouteFromTraci(vehicleData.getRouteId()));
            }
        }

        // check updated vehicles for new routes
        for (VehicleData vehicleData : vehicleUpdates.getUpdated()) {
            if (!routes.containsKey(vehicleData.getRouteId())) {
                newRoutes.add(readRouteFromTraci(vehicleData.getRouteId()));
            }
        }

        // now create VehicleRouteRegistration interactions for each and add route to cache
        for (VehicleRoute route : newRoutes) {
            // propagate new route
            final VehicleRouteRegistration vehicleRouteRegistration = new VehicleRouteRegistration(time, route);
            try {
                rti.triggerInteraction(vehicleRouteRegistration);
            } catch (IllegalValueException e) {
                throw new InternalFederateException(e);
            }

            // save in cache
            routes.put(route.getId(), route);
        }
    }

    /**
     * Reads the route from the SUMO Traci.
     *
     * @param routeId The Id of the route.
     * @return The route from the Traci.
     * @throws InternalFederateException Exception is thrown if an error is occurred by reading route from the Traci.
     */
    private VehicleRoute readRouteFromTraci(String routeId) throws InternalFederateException {
        // this route will always be generated with an empty list of nodes
        return new VehicleRoute(routeId, bridge.getRouteControl().getRouteEdges(routeId), new ArrayList<>(), 0d);
    }


}
