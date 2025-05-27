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

package org.eclipse.mosaic.lib.routing.graphhopper.util;

import org.eclipse.mosaic.lib.database.Database;
import org.eclipse.mosaic.lib.database.DatabaseUtils;
import org.eclipse.mosaic.lib.database.road.Connection;
import org.eclipse.mosaic.lib.database.road.Node;
import org.eclipse.mosaic.lib.database.road.Way;
import org.eclipse.mosaic.lib.routing.graphhopper.profile.BikeProfile;
import org.eclipse.mosaic.lib.routing.graphhopper.profile.CarProfile;
import org.eclipse.mosaic.lib.routing.graphhopper.profile.RoutingProfile;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Loads the content of a MOSAIC scenario database into a graphhopper graph.
 */
public class DatabaseGraphLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseGraphLoader.class);

    private final Database database;
    private final List<TagParser> wayParsers = new ArrayList<>();
    private BaseGraph graphStorage;
    private RoutingProfileManager profileManager;
    private GraphhopperToDatabaseMapper graphMapper;
    private TurnCostStorage turnCostStorage;
    private EdgeIntAccess edgeAccess;

    public DatabaseGraphLoader(Database database) {
        this.database = database;
    }

    public void initialize(BaseGraph graph,
                           RoutingProfileManager profileManager,
                           GraphhopperToDatabaseMapper mapper
    ) {
        this.graphStorage = graph;
        this.graphMapper = mapper;
        this.profileManager = profileManager;
        this.turnCostStorage = graph.getTurnCostStorage();

        wayParsers.clear();

        for (RoutingProfile profile : profileManager.getAllProfiles()) {
            wayParsers.addAll(profile.createTagParsers(profileManager.getEncodingManager()));
        }
    }

    public void loadGraph() {

        int nodeIndex = 0;

        // The import gets buggy if we do not filter for the main graph beforehand. 
        // GraphHopper is not able to route on divided graphs anyway.
        final Set<Node> mainGraph = searchForMainGraph();

        graphStorage.create(Math.max(mainGraph.size() / 30, 100));
        edgeAccess = graphStorage.getEdgeAccess();

        WayTypeEncoder wayTypeEncoder = profileManager.getEncodingManager().getEncodedValue(WayTypeEncoder.KEY, WayTypeEncoder.class);

        // add nodes and connections
        for (Connection con : database.getConnections()) {
            final Node from = con.getFrom();
            final Node to = con.getTo();

            if (!mainGraph.contains(from) || !mainGraph.contains(to)) {
                LOG.debug("Connection {} has not been added to the routing graph, "
                        + "since it is not within the main graph of the traffic network", con.getId());
                continue;
            }

            final Way way = con.getWay();

            if (graphMapper.fromNode(from) < 0) {
                addNode(nodeIndex, from);
                nodeIndex++;
            }

            if (graphMapper.fromNode(to) < 0) {
                addNode(nodeIndex, to);
                nodeIndex++;
            }

            EdgeIteratorState edgeIt = graphStorage.edge(graphMapper.fromNode(from), graphMapper.fromNode(to))
                    .setDistance(con.getLength())
                    .setWayGeometry(getWayGeometry(from, to, con.getNodes()));
            handleWayTags(edgeIt.getEdge(), way);

            wayTypeEncoder.setWay(way, con.getLanes(), edgeIt.getEdge(), edgeAccess);

            graphMapper.setConnection(con, edgeIt.getEdge());
        }

        VehicleEncoding car = profileManager.getRoutingProfile(CarProfile.NAME).getVehicleEncoding();
        VehicleEncoding bike = profileManager.getRoutingProfile(BikeProfile.NAME).getVehicleEncoding();

        // add turn restrictions
        for (Connection conFrom : database.getConnections()) {

            Node nodeVia = conFrom.getTo();

            int ghNodeVia = graphMapper.fromNode(nodeVia);
            int ghConFrom = graphMapper.fromConnection(conFrom);
            int ghConTo;

            if (ghNodeVia < 0 || ghConFrom < 0) {
                continue;
            }

            for (Connection conTo : nodeVia.getOutgoingConnections()) {
                Collection<Connection> outgoing = conFrom.getOutgoingConnections();

                ghConTo = graphMapper.fromConnection(conTo);

                if (ghConTo < 0) {
                    continue;
                }

                boolean isUTurn = conTo.getFrom() == conFrom.getTo() && conFrom.getFrom() == conTo.getTo();
                boolean isOnRoad = outgoing.size() == 2;
                boolean endsAtJunction = outgoing.size() > 2;

                if (!outgoing.contains(conTo) || (isUTurn && isOnRoad)) {
                    // if end node is connected with an connection, which is not
                    // accessible from incoming connection, then add turn
                    // restriction
                    turnCostStorage.set(
                            car.turnRestriction(), ghConFrom, ghNodeVia, ghConTo, true
                    );
                    turnCostStorage.set(
                            bike.turnRestriction(), ghConFrom, ghNodeVia, ghConTo, true
                    );
                } else if (isUTurn) {
                    // avoid (high costs) u-turns on same road, that is if from and to node
                    // of first connection are the same nodes as of second connection
                    // note, only for cars, bikes can easily turn
                    turnCostStorage.set(
                            car.turnCost(), ghConFrom, ghNodeVia, ghConTo, endsAtJunction ? 120 : 90
                    );
                }
            }
        }

        // analyze turn costs
        new TurnCostAnalyzer(graphStorage, wayTypeEncoder).createTurnCostsForVehicle(car);

        if (graphStorage.getNodes() == 0) {
            throw new IllegalStateException(
                    "No nodes or connections has been found in database.");
        }

    }

    /**
     * Searches the main graph in order to import correctly from database.
     *
     * @return Set of graphs describing the main graph.
     */
    private Set<Node> searchForMainGraph() {

        ArrayList<Set<Node>> subGraphs = DatabaseUtils.detectGraphs(database.getNodes());
        Set<Node> mainGraph = null;
        for (Set<Node> subGraph : subGraphs) {
            if (mainGraph == null) {
                mainGraph = subGraph;
            }
            if (subGraph.size() > mainGraph.size()) {
                mainGraph = subGraph;
            }
        }
        return mainGraph;
    }

    /**
     * Adds a node to graphhopper storage.
     *
     * @param nodeIndex Index of the node that will be added.
     * @param node      This node to add.
     */
    private void addNode(int nodeIndex, final Node node) {
        graphStorage.getNodeAccess().setNode(nodeIndex, node.getPosition().getLatitude(),
                node.getPosition().getLongitude(), node.getPosition().getAltitude());
        graphMapper.setNode(node, nodeIndex);
    }

    private void handleWayTags(int edgeId, Way way) {
        IntsRef relationFlags = IntsRef.EMPTY;

        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", way.getType());
        osmWay.setTag("maxspeed", String.valueOf((int) Math.round(way.getMaxSpeedInKmh())));
        osmWay.setTag("oneway", "yes");

        for (TagParser parser : wayParsers) {
            parser.handleWayTags(edgeId, edgeAccess, osmWay, relationFlags);
        }
    }

    /**
     * Getter for the way geometry as a list of points.
     *
     * @param from        Start node.
     * @param to          End node.
     * @param wayNodeList List containing way nodes from which to get the geometry.
     * @return List of points representing the geometry.
     */
    private PointList getWayGeometry(Node from, Node to, List<Node> wayNodeList) {
        PointList points = new PointList(1000, true);

        boolean between = false;
        boolean reverse = true;
        for (Node wayNode : wayNodeList) {
            if (between) {
                if (wayNode.getId().equals(from.getId())) {
                    between = false;
                    reverse = true;
                }

                if (wayNode.getId().equals(to.getId())) {
                    between = false;
                    reverse = false;
                }

                if (between) {
                    points.add(
                            wayNode.getPosition().getLatitude(),
                            wayNode.getPosition().getLongitude(),
                            wayNode.getPosition().getAltitude()
                    );
                }
            } else {
                if (wayNode.getId().equals(from.getId())) {
                    between = true;
                    reverse = false;
                }

                if (wayNode.getId().equals(to.getId())) {
                    between = true;
                    reverse = true;
                }
            }
        }

        if (reverse) {
            points.reverse();
        }

        return points;
    }

}
