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

package org.eclipse.mosaic.lib.routing.graphhopper;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;

import org.eclipse.mosaic.lib.routing.RoutingCostFunction;
import org.eclipse.mosaic.lib.routing.graphhopper.util.GraphhopperToDatabaseMapper;
import org.eclipse.mosaic.lib.routing.graphhopper.util.VehicleEncoding;
import org.eclipse.mosaic.lib.routing.graphhopper.util.WayTypeEncoder;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;

/**
 * A dynamic weight calculation. If an alternative travel time
 * on an edge is known, then this travel time will be used to weight
 * during routing. Otherwise, the minimum travel time will be used
 * to weight an edge.
 */
public class GraphHopperWeighting implements Weighting {

    private final BooleanEncodedValue accessEnc;
    private final DecimalEncodedValue speedEnc;
    private final TurnCostProvider turnCostProvider;
    private final GraphHopperEdgeProperties edgePropertiesState;
    private final double maxSpeed;

    private RoutingCostFunction routingCostFunction;

    public GraphHopperWeighting(VehicleEncoding vehicleEncoding, WayTypeEncoder wayTypeEncoder, TurnCostProvider turnCostProvider, GraphhopperToDatabaseMapper graphMapper) {
        this.accessEnc = vehicleEncoding.access();
        this.speedEnc = vehicleEncoding.speed();
        this.turnCostProvider = turnCostProvider;

        this.edgePropertiesState = new GraphHopperEdgeProperties(vehicleEncoding, wayTypeEncoder, graphMapper);
        this.maxSpeed = speedEnc.getMaxOrMaxStorableDecimal() / 3.6; // getMaxOrMaxStorableDecimal returns the speed in km/h
    }

    public GraphHopperWeighting setRoutingCostFunction(RoutingCostFunction routingCostFunction) {
        this.routingCostFunction = routingCostFunction;
        return this;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edge, boolean reverse) {
        if (reverse ? !edge.getReverse(accessEnc) : !edge.get(accessEnc)) {
            return Double.POSITIVE_INFINITY;
        }
        synchronized (edgePropertiesState) {
            edgePropertiesState.setCurrentEdgeIterator(edge, reverse);
            if (routingCostFunction == null) {
                return edge.getDistance() / edgePropertiesState.getSpeed();
            } else {
                return routingCostFunction.calculateCosts(edgePropertiesState);
            }
        }
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        if (reverse && !edgeState.getReverse(accessEnc) || !reverse && !edgeState.get(accessEnc))
            throw new IllegalStateException("Calculating time should not require to read speed from edge in wrong direction. " +
                    "(" + edgeState.getBaseNode() + " - " + edgeState.getAdjNode() + ") "
                    + edgeState.fetchWayGeometry(FetchMode.ALL) + ", dist: " + edgeState.getDistance() + " "
                    + "Reverse:" + reverse + ", fwd:" + edgeState.get(accessEnc) + ", bwd:" + edgeState.getReverse(accessEnc) + ", fwd-speed: " + edgeState.get(speedEnc) + ", bwd-speed: " + edgeState.getReverse(speedEnc));

        double speed = reverse ? edgeState.getReverse(speedEnc) : edgeState.get(speedEnc);
        if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
            throw new IllegalStateException("Invalid speed stored in edge! " + speed);
        if (speed == 0)
            throw new IllegalStateException("Speed cannot be 0 for unblocked edge, use access properties to mark edge blocked! Should only occur for shortest path calculation. See #242.");

        return Math.round(edgeState.getDistance() / speed * 3.6 * 1000);
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return turnCostProvider.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return turnCostProvider.calcTurnMillis(inEdge, viaNode, outEdge);
    }

    @Override
    public boolean hasTurnCosts() {
        return turnCostProvider != NO_TURN_COST_PROVIDER;
    }

    public TurnCostProvider getTurnCostProvider() {
        return turnCostProvider;
    }

    @Override
    public double calcMinWeightPerDistance() {
        return 1 / maxSpeed;
    }

    @Override
    public String getName() {
        if (routingCostFunction == null) {
            return "fastest";
        } else {
            return routingCostFunction.getCostFunctionName().toLowerCase();
        }
    }

}
