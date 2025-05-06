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

import static java.util.Objects.requireNonNull;

import org.eclipse.mosaic.lib.database.Database;
import org.eclipse.mosaic.lib.database.DatabaseUtils;
import org.eclipse.mosaic.lib.database.road.Connection;
import org.eclipse.mosaic.lib.database.road.Node;
import org.eclipse.mosaic.lib.enums.VehicleClass;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoUtils;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.routing.RoutingCostFunction;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.routing.RoutingRequest;
import org.eclipse.mosaic.lib.routing.graphhopper.algorithm.RoutingAlgorithmFactory;
import org.eclipse.mosaic.lib.routing.graphhopper.profile.BikeProfile;
import org.eclipse.mosaic.lib.routing.graphhopper.profile.CarProfile;
import org.eclipse.mosaic.lib.routing.graphhopper.profile.RoutingProfile;
import org.eclipse.mosaic.lib.routing.graphhopper.util.DatabaseGraphLoader;
import org.eclipse.mosaic.lib.routing.graphhopper.util.GraphhopperToDatabaseMapper;
import org.eclipse.mosaic.lib.routing.graphhopper.util.OptionalTurnCostProvider;
import org.eclipse.mosaic.lib.routing.graphhopper.util.RoutingProfileManager;
import org.eclipse.mosaic.lib.routing.graphhopper.util.VehicleEncoding;
import org.eclipse.mosaic.lib.routing.graphhopper.util.WayTypeEncoder;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@SuppressWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Static fields kept public and adjustable for user customization")
public class GraphHopperRouting {

    private static final Logger LOG = LoggerFactory.getLogger(GraphHopperRouting.class);

    public static final Collection<Supplier<RoutingProfile>> PROFILES = List.of(
            CarProfile::new,
            BikeProfile::new
    );

    /**
     * The minimum number of alternatives to calculate when alternative routes have been requested.
     * GraphHopper often returns equal routes, and by calculating more than required we can
     * reduce the result to match the requested number of alternatives.
     */
    private static final int NUM_ALTERNATIVE_PATHS = 5;

    /**
     * Alternative routes may share a maximum of 70% of roads of the best route.
     */
    public static double ALTERNATIVE_ROUTES_MAX_SHARE = 0.7;

    /**
     * Alternative routes may cost a maximum of 40% more than the best route.
     */
    public static double ALTERNATIVE_ROUTES_MAX_WEIGHT = 1.4;

    /**
     * Increases the changes to find more alternatives.
     */
    public static double ALTERNATIVE_ROUTES_EXPLORATION_FACTOR = 1.3;

    /**
     * Specifies the minimum plateau portion of every alternative path that is required.
     */
    public static double ALTERNATIVE_ROUTES_PLATEAU_FACTOR = 0.1;

    /**
     * If the distance of the query position to the closest node is lower than this
     * value, then the closest node is used definitely as the source or target of the route.
     * If the distance is larger, the route may start or end on the connection the query is matched on.
     */
    public static final double TARGET_NODE_QUERY_DISTANCE = 1d;

    /**
     * If the requested target point is this X meters away from the last node of
     * the found route, another connection is added on which the target
     * point is matched on.
     */
    public static final double TARGET_REQUEST_CONNECTION_THRESHOLD = 5d;

    /**
     * Sometimes edges are dead ends. In these cases routing fails and invalid
     * routes are returned. To omit this, we check if the distance between the route end
     * and the original query target lies within the given threshold.
     */
    private static final double MAX_DISTANCE_TO_TARGET = 500d;

    private final DistanceCalc distanceCalculation = new DistancePlaneProjection();

    private final GraphhopperToDatabaseMapper graphMapper;
    private final Database db;
    private final RoutingProfileManager profileManager;
    private final BaseGraph graph;
    private final LocationIndex locationIndex;

    public GraphHopperRouting(Database db) {
        this.db = db;

        graphMapper = new GraphhopperToDatabaseMapper();
        profileManager = new RoutingProfileManager(PROFILES);

        graph = createGraphFromDatabase(db);
        locationIndex = createLocationIndex();
        cleanUpGraph();

        graph.flush();
    }

    private BaseGraph createGraphFromDatabase(Database db) {
        final BaseGraph graph = new BaseGraph
                .Builder(profileManager.getEncodingManager())
                .setDir(new RAMDirectory())
                .set3D(true)
                .withTurnCosts(profileManager.getEncodingManager().needsTurnCostsSupport())
                .setSegmentSize(-1)
                .build();

        final DatabaseGraphLoader reader = new DatabaseGraphLoader(db);
        reader.initialize(graph, profileManager, graphMapper);
        reader.loadGraph();
        LOG.info("nodes: {}, edges: {}", graph.getNodes(), graph.getEdges());
        return graph;
    }

    private LocationIndex createLocationIndex() {
        return new LocationIndexTree(graph, graph.getDirectory())
                .setMinResolutionInMeter(300)
                .setMaxRegionSearch(4)
                .prepareIndex();
    }

    protected void cleanUpGraph() {
        new PrepareRoutingSubnetworks(graph, buildSubnetworkRemovalJobs())
                .setMinNetworkSize(200)
                .setThreads(1)
                .doWork();
    }

    private List<PrepareRoutingSubnetworks.PrepareJob> buildSubnetworkRemovalJobs() {
        List<PrepareRoutingSubnetworks.PrepareJob> jobs = new ArrayList<>();
        for (RoutingProfile profile : profileManager.getAllProfiles()) {
            Weighting weighting = createWeighting(profile, RoutingCostFunction.Fastest, false);
            jobs.add(new PrepareRoutingSubnetworks.PrepareJob(profile.getVehicleEncoding().subnetwork(), weighting));
        }
        return jobs;
    }

    public List<CandidateRoute> findRoutes(RoutingRequest routingRequest) {
        if (graph == null) {
            throw new IllegalStateException("Load database at first");
        }

        final RoutingProfile profile;
        if (routingRequest.getRoutingParameters().getVehicleClass() == VehicleClass.Bicycle) {
            profile = profileManager.getRoutingProfile(BikeProfile.NAME);
        } else {
            profile = profileManager.getRoutingProfile(CarProfile.NAME);
        }
        final VehicleEncoding vehicleEncoding = profile.getVehicleEncoding();

        final RoutingPosition source = routingRequest.getSource();
        final RoutingPosition target = routingRequest.getTarget();

        final Snap snapSource = createQueryForSource(source, vehicleEncoding.access());
        final Snap snapTarget = createQueryForTarget(target, vehicleEncoding.access());

        if (snapSource.getClosestEdge() == null || snapTarget.getClosestEdge() == null) {
            LOG.warn("Could not find a route from {} to {}", routingRequest.getSource(), routingRequest.getTarget());
            return Lists.newArrayList();
        }

        final QueryGraph queryGraph = QueryGraph.create(graph, snapSource, snapTarget);

        final int numberOfAlternatives = routingRequest.getRoutingParameters().getNumAlternativeRoutes();
        final PMap algoHints = new PMap();
        if (numberOfAlternatives > 0) {
            // We calculate more alternative routes than required, since GraphHopper often seem to return equal alternatives
            algoHints.putObject(Parameters.Algorithms.AltRoute.MAX_PATHS, Math.max(numberOfAlternatives, NUM_ALTERNATIVE_PATHS) + 1);
        }

        final Weighting weighting = createWeighting(profile,
                routingRequest.getRoutingParameters().getRoutingCostFunction(),
                routingRequest.getRoutingParameters().isConsiderTurnCosts()
        );

        final RoutingAlgorithm algo = RoutingAlgorithmFactory.DEFAULT.createAlgorithm(
                queryGraph, queryGraph.wrapWeighting(weighting), algoHints
        );

        final List<Path> paths = algo.calcPaths(snapSource.getClosestNode(), snapTarget.getClosestNode());

        final Set<String> duplicateSet = new HashSet<>();
        final List<CandidateRoute> result = new ArrayList<>();

        // convert paths to routes
        for (final Path path : paths) {
            if (result.size() > numberOfAlternatives) {
                break;
            }
            final CandidateRoute route = convertPath(queryGraph, path, source, target);
            if (route != null
                    && !route.getConnectionIds().isEmpty()
                    && checkRouteOnRequiredSourceConnection(route, source)
                    && checkForDuplicate(route, duplicateSet)
            ) {
                result.add(route);
            } else if (route != null && LOG.isDebugEnabled()) {
                LOG.debug("Path is invalid and will be ignored [" + StringUtils.join(route.getConnectionIds(), ",") + "]");
            }
        }
        return result;
    }

    private Weighting createWeighting(RoutingProfile profile, RoutingCostFunction costFunction, boolean withTurnCosts) {
        final VehicleEncoding vehicleEncoding = profile.getVehicleEncoding();
        final OptionalTurnCostProvider turnCostProvider = new OptionalTurnCostProvider(vehicleEncoding, graph.getTurnCostStorage());
        if (!withTurnCosts) {
            turnCostProvider.disableTurnCosts();
        }
        WayTypeEncoder wayTypeEncoder = profileManager.getEncodingManager().getEncodedValue(WayTypeEncoder.KEY, WayTypeEncoder.class);
        return new GraphHopperWeighting(vehicleEncoding, wayTypeEncoder, turnCostProvider, graphMapper)
                .setRoutingCostFunction(ObjectUtils.defaultIfNull(costFunction, RoutingCostFunction.Default));
    }

    private Snap createQueryForTarget(RoutingPosition target, BooleanEncodedValue accessEnc) {
        final EdgeFilter toEdgeFilter = createEdgeFilterForRoutingPosition(target, accessEnc);
        Snap queryTarget = locationIndex.findClosest(target.getPosition().getLatitude(), target.getPosition().getLongitude(), toEdgeFilter);
        if (target.getConnectionId() != null) {
            return fixQueryResultIfNoClosestEdgeFound(queryTarget, target, accessEnc);
        } else {
            return fixQueryResultIfSnappedPointIsCloseToTowerNode(queryTarget, target);
        }
    }

    private Snap createQueryForSource(RoutingPosition source, BooleanEncodedValue accessEnc) {
        final EdgeFilter fromEdgeFilter = createEdgeFilterForRoutingPosition(source, accessEnc);
        Snap querySource = locationIndex.findClosest(source.getPosition().getLatitude(), source.getPosition().getLongitude(), fromEdgeFilter);
        if (source.getConnectionId() != null) {
            querySource = fixQueryResultIfSnappedPointIsTowerNode(querySource, source, fromEdgeFilter);
            return fixQueryResultIfNoClosestEdgeFound(querySource, source, accessEnc);
        } else {
            return fixQueryResultIfSnappedPointIsCloseToTowerNode(querySource, source);
        }
    }

    private Snap fixQueryResultIfSnappedPointIsCloseToTowerNode(Snap queryResult, RoutingPosition target) {
        if (queryResult.getSnappedPosition() == Snap.Position.TOWER || target.getConnectionId() != null) {
            return queryResult;
        }
        Node closestNode = graphMapper.toNode(queryResult.getClosestNode());
        /* If the query result is snapped to an edge, but the matched node is very close to the query, than we use the actual closest node
         * as the target of the route.*/
        if (closestNode != null && target.getPosition().distanceTo(closestNode.getPosition()) < TARGET_NODE_QUERY_DISTANCE) {
            queryResult.setSnappedPosition(Snap.Position.TOWER);
        }
        return queryResult;
    }

    private Snap fixQueryResultIfNoClosestEdgeFound(Snap queryResult, RoutingPosition routingPosition, BooleanEncodedValue accessEnc) {
        if (queryResult.getClosestEdge() == null) {
            LOG.warn("Wrong routing request: The from-connection {} does not fit with the given position {}", routingPosition.getConnectionId(), routingPosition.getPosition());

            return locationIndex.findClosest(
                    routingPosition.getPosition().getLatitude(), routingPosition.getPosition().getLongitude(), AccessFilter.allEdges(accessEnc)
            );
        }
        return queryResult;
    }

    private Snap fixQueryResultIfSnappedPointIsTowerNode(Snap queryResult, RoutingPosition routingPosition, EdgeFilter fromEdgeFilter) {
        if (queryResult.getSnappedPosition() != Snap.Position.TOWER) {
            return queryResult;
        }
        /* If the requested position is in front or behind the edge it is mapped either on the start or end of the edge (one of the tower nodes).
         * As a result, the resulting route can bypass turn restrictions in very rare cases. To avoid this, we choose an alternative
         * position which is located somewhere _on_ the queried connection.*/
        final Connection queryConnection = db.getConnection(routingPosition.getConnectionId());
        final GeoPoint alternativeQueryPosition;
        if (queryConnection.getNodes().size() > 2) {
            alternativeQueryPosition = requireNonNull(DatabaseUtils.getNodeByIndex(queryConnection, -2)).getPosition();
        } else {
            alternativeQueryPosition = GeoUtils.getPointBetween(
                    queryConnection.getFrom().getPosition(), queryConnection.getTo().getPosition()
            );
        }
        return locationIndex.findClosest(
                alternativeQueryPosition.getLatitude(), alternativeQueryPosition.getLongitude(), fromEdgeFilter
        );
    }

    /**
     * Checks the {@param duplicateSet} whether it contains the {@param route}'s nodeIdList.
     *
     * @param route        Route to check for duplicate.
     * @param duplicateSet Set of node Ids.
     * @return True, if not duplicate in the set.
     */
    private boolean checkForDuplicate(CandidateRoute route, Set<String> duplicateSet) {
        String nodeIdList = StringUtils.join(route.getConnectionIds(), ",");
        return duplicateSet.add(nodeIdList);
    }

    private boolean checkRouteOnRequiredSourceConnection(CandidateRoute route, RoutingPosition source) {
        if (source.getConnectionId() != null) {
            return source.getConnectionId().equals(route.getConnectionIds().get(0));
        }
        return true;
    }

    private EdgeFilter createEdgeFilterForRoutingPosition(final RoutingPosition position, final BooleanEncodedValue accessEnc) {
        if (position.getConnectionId() == null) {
            return AccessFilter.allEdges(accessEnc);
        }
        final int forcedEdge = graphMapper.fromConnection(db.getConnection(position.getConnectionId()));
        if (forcedEdge < 0) {
            return AccessFilter.allEdges(accessEnc);
        }
        return edgeState -> edgeState.getEdge() == forcedEdge;
    }

    private CandidateRoute convertPath(Graph graph, Path newPath, RoutingPosition sourcePosition, RoutingPosition targetPosition) {
        PointList pointList = newPath.calcPoints();
        if (pointList.isEmpty()) {
            return null;
        }
        GHPoint pathTarget = Iterables.getLast(pointList);
        GHPoint origTarget = new GHPoint(targetPosition.getPosition().getLatitude(), targetPosition.getPosition().getLongitude());
        double distanceToOriginalTarget = distanceCalculation.calcDist(pathTarget.lat, pathTarget.lon, origTarget.lat, origTarget.lon);
        if (distanceToOriginalTarget > MAX_DISTANCE_TO_TARGET) {
            return null;
        }
        Iterator<EdgeIteratorState> edgesIt = newPath.calcEdges().iterator();
        if (!edgesIt.hasNext()) {
            return null;
        }

        List<String> pathConnections = new ArrayList<>();

        double offsetFromSource = 0;
        int lastNode = -1;
        Connection lastConnection = null;
        NodeAccess nodes = graph.getNodeAccess();

        while (edgesIt.hasNext()) {
            EdgeIteratorState origEdge = edgesIt.next();
            EdgeIteratorState currEdge = origEdge;

            /*
             * If the requested source or target point is in the middle of the road, an artificial node
             * (and artificial edges) is created in the QueryGraph. As a consequence, the first
             * and/or last edge of the route might be such virtual edge, which must be converted to its original edge.
             */
            if (currEdge instanceof VirtualEdgeIteratorState virtualEdge) {
                currEdge = graph.getEdgeIteratorStateForKey(virtualEdge.getOriginalEdgeKey());
            }

            Connection con = graphMapper.toConnection(currEdge.getEdge());
            if (con != null) {
                /*
                 * In some cases, virtual edges are created at the target even though they are only some
                 * centimeters away from the requested node. In that case, we would have an unwanted
                 * last connection at the end of the route, which is eliminated here.
                 */
                boolean lastConnectionStartsAtTarget = !edgesIt.hasNext()
                        && targetPosition.getConnectionId() == null
                        && targetPosition.getPosition().distanceTo(con.getFrom().getPosition()) < TARGET_REQUEST_CONNECTION_THRESHOLD;
                if (lastConnectionStartsAtTarget) {
                    continue;
                }

                if (pathConnections.isEmpty()) {
                    offsetFromSource = calcOffset(con, origEdge.getBaseNode(), nodes);
                }

                pathConnections.add(con.getId());
                lastNode = origEdge.getAdjNode();
                lastConnection = con;

                if (Double.isInfinite(newPath.getWeight())) {
                    LOG.warn(
                            "Something went wrong during path search: The found route has infinite weight. Maybe there's a turn restriction or unconnected "
                                    + "sub-graphs in the network. Route will be ignored.");
                    return null;
                }
            } else {
                LOG.debug(String.format("A connection could be resolved by internal ID %d.", origEdge.getEdge()));
            }
        }

        double offsetToTarget = 0;
        if (lastConnection != null) {
            offsetToTarget = lastConnection.getLength() - calcOffset(lastConnection, lastNode, nodes);
        }

        fixFirstConnectionOfPathIfNotAsQueried(sourcePosition, pathConnections);
        return new CandidateRoute(
                pathConnections,
                newPath.getDistance(),
                newPath.getTime() / 1000.0,
                offsetFromSource,
                offsetToTarget
        );
    }

    private double calcOffset(Connection con, int node, NodeAccess nodes) {
        GeoPoint onConnection = GeoPoint.latLon(nodes.getLat(node), nodes.getLon(node));
        double offset = 0;
        Node prev = null;
        for (Node curr : con.getNodes()) {
            if (prev != null) {
                double distancePrevToCurr = prev.getPosition().distanceTo(curr.getPosition());
                double distancePrevToNode = prev.getPosition().distanceTo(onConnection);
                if (distancePrevToNode < distancePrevToCurr) {
                    return Math.max(con.getLength(), offset + distancePrevToNode);
                }
                offset += distancePrevToCurr;
            }
            prev = curr;
        }
        return con.getLength();
    }

    /**
     * In some very rare cases, if a source connection is given, the path returned by GraphHopper omits this first connection
     * and continues on the subsequent one. As a workaround, this code checks if the outgoing connections of the queried source connection
     * contains the first connection of the calculated path, and then adds the source connection to the beginning of the new path.
     */
    private void fixFirstConnectionOfPathIfNotAsQueried(RoutingPosition sourcePosition, List<String> pathConnections) {
        String firstConnectionId = Iterables.getFirst(pathConnections, null);
        if (sourcePosition.getConnectionId() != null && firstConnectionId != null
                && !sourcePosition.getConnectionId().equals(firstConnectionId)
        ) {
            Connection sourceConnection = db.getConnection(sourcePosition.getConnectionId());
            Connection firstConnection = db.getConnection(firstConnectionId);
            if (sourceConnection.getOutgoingConnections().contains(firstConnection)) {
                pathConnections.add(0, sourceConnection.getId());
            }
        }
    }
}
