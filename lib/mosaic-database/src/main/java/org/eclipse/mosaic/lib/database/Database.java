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

package org.eclipse.mosaic.lib.database;

import org.eclipse.mosaic.lib.database.building.Building;
import org.eclipse.mosaic.lib.database.building.Corner;
import org.eclipse.mosaic.lib.database.building.Wall;
import org.eclipse.mosaic.lib.database.persistence.OutdatedDatabaseException;
import org.eclipse.mosaic.lib.database.persistence.SQLiteReader;
import org.eclipse.mosaic.lib.database.persistence.SQLiteTypeDetector;
import org.eclipse.mosaic.lib.database.persistence.SQLiteWriter;
import org.eclipse.mosaic.lib.database.road.Connection;
import org.eclipse.mosaic.lib.database.road.Node;
import org.eclipse.mosaic.lib.database.road.Restriction;
import org.eclipse.mosaic.lib.database.road.Roundabout;
import org.eclipse.mosaic.lib.database.road.TrafficLightNode;
import org.eclipse.mosaic.lib.database.road.Way;
import org.eclipse.mosaic.lib.database.route.Route;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoRectangle;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * This contains all information about the traffic network, as well as possible obstacles affecting
 * communications and predefined routing information. The information can be persisted to and read
 * from a file.
 */
public class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    /**
     * Default name if no version name was defined. If a database has this value set it usually means,
     * that the database was one of the following:<br />
     * <ul>
     * <li>just created and is not persisted yet</li>
     * <li>not loaded yet</li>
     * <li>could not be loaded from persistence</li>
     * </ul>
     */
    public static final String VERSION_UNKNOWN = "UNKNOWN";

    /**
     * Key for the import origin property.
     */
    public static final String PROPERTY_VERSION = "version";
    /**
     * Key for the import origin property.
     */
    public static final String PROPERTY_IMPORT_ORIGIN = "importOrigin";

    /**
     * Value for key "importOrigin" which states, that the import origin of the database was a SUMO network file.
     */
    public static final String IMPORT_ORIGIN_SUMO = "sumo";

    /**
     * Value for key "importOrigin" which states, that the import origin of the database was a OpenStreetMap file.
     */
    public static final String IMPORT_ORIGIN_OSM = "osm";

    /**
     * Contains additional properties.
     */
    private final Map<String, String> properties = new HashMap<>();

    /**
     * Contains all {@link Node}s that are part of Ways. There should be no {@link Node} that has no
     * referenced {@link Way}s.
     */
    private final Map<String, Node> nodes = new HashMap<>();

    /**
     * Contains all {@link Way}s that represent roads that can be used for routing. There should
     * be no {@link Way}s without referenced {@link Node}s.
     */
    private final Map<String, Way> ways = new HashMap<>();

    /**
     * Contains all {@link Connection}s that represent an abstract view of the network.
     * Each connection is part of a way and should represent a part that starts and ends at an
     * intersection {@link Node}.
     */
    private final Map<String, Connection> connections = new HashMap<>();

    /**
     * Contains all roundabouts that are part of the map.
     */
    private final List<Roundabout> roundabouts = new ArrayList<>();

    /**
     * This list contains all turn restrictions present in the road network.
     * This should <b>not</b> be needed for routing purposes as this information is incorporated
     * into the network when loading from persistence. The <b>only</b> intended use for this
     * is to retain the information for persisting to a database system or file.
     */
    private final Map<String, Restriction> restrictions = new HashMap<>();

    /**
     * Contains all {@link Building}s that may have an impact on communication.
     */
    private final Map<String, Building> buildings = new HashMap<>();


    /**
     * Contains all predefined {@link Route}s vehicles can drive on. Each route consists of
     * {@link Connection}s
     */
    private final Map<String, Route> routes = new HashMap<>();

    private final MutableGeoPoint minBounds = new MutableGeoPoint(90, 180);
    private final MutableGeoPoint maxBounds = new MutableGeoPoint(-90, -180);

    private transient List<String> borderNodes = null;

    /**
     * This creates a new database along with a version.
     * This is intended for a completely new database which is not persisted as of yet.
     *
     * @param version database version
     */
    private Database(String version) {
        properties.put(PROPERTY_VERSION, version);
    }

    /**
     * This method loads tries to load a database object from
     * the given {@link File}, which should refer to a database-file.
     * A {@link SQLiteReader} is used for the translation of the database
     * to the Java-Object
     *
     * @param file the database-file
     * @return the loaded database
     */
    public static Database loadFromFile(File file) {
        return Database.Builder.loadFromFile(file).build();
    }

    /**
     * This method loads tries to load a database object from
     * the given filename, which should refer to a database-file.
     *
     * @param filename a {@link String} representation of the file to be loaded
     * @return the loaded database
     */
    public static Database loadFromFile(String filename) {
        return Database.loadFromFile(new File(filename));
    }

    /**
     * Saves the current database to a SQLite file.
     *
     * @param filename database filename.
     */
    public void saveToFile(String filename) {
        new SQLiteWriter().saveToFile(this, filename);
    }

    /**
     * Returns the version of the given Database.
     *
     * @return version as string or {@link #VERSION_UNKNOWN}
     */
    public String getVersion() {
        return properties.getOrDefault(PROPERTY_VERSION, VERSION_UNKNOWN);
    }

    /**
     * Returns the import origin of the given Database.
     *
     * @return import origin as string
     */
    public String getImportOrigin() {
        return properties.getOrDefault(PROPERTY_IMPORT_ORIGIN, "");
    }

    /**
     * Returns the value of the requested property with the given key.
     *
     * @param key Key of the requested property.
     * @return Requested property.
     */
    public String getProperty(String key) {
        return this.properties.get(key);
    }

    /**
     * Returns all property keys.
     *
     * @return Set of the property keys.
     */
    public Set<String> getPropertyKeys() {
        return this.properties.keySet();
    }

    /**
     * Returns the {@link Node} with the given id.
     *
     * @param id Id of the Node.
     * @return Requested node.
     */
    public Node getNode(String id) {
        return nodes.get(id);
    }

    /**
     * Returns the {@link Way} with the given id.
     *
     * @param id Id of the way.
     * @return Way with the given Id.
     */
    public Way getWay(String id) {
        return ways.get(id);
    }

    /**
     * Returns the {@link Connection} with the given id.
     *
     * @param id Id of the connection.
     * @return Connection with the given Id.
     */
    public Connection getConnection(String id) {
        return connections.get(id);
    }

    /**
     * Returns the {@link Building} with the given id.
     *
     * @param id id of the building.
     * @return The building identified by the given id.
     */
    public Building getBuilding(String id) {
        return buildings.get(id);
    }

    /**
     * Returns the {@link Route} with the given id.
     *
     * @param id Id of the route.
     * @return Route with the given Id.
     */
    public Route getRoute(String id) {
        return routes.get(id);
    }

    /**
     * Returns the {@link Restriction} with the given id.
     *
     * @param id Id of the restriction.
     * @return Restriction with the given Id.
     */
    public Restriction getRestriction(String id) {
        return restrictions.get(id);
    }

    /**
     * Returns the bounding box containing all nodes in the database.
     *
     * @return the bounding box as {@link GeoRectangle}
     */
    public GeoRectangle getBoundingBox() {
        return new GeoRectangle(minBounds, maxBounds);
    }

    /**
     * Returns an unmodifiable view of the known {@link Node}s.
     *
     * @return Nodes as {@link Collection}.
     */
    public Collection<Node> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * Returns a list of all nodes, which are tagged as traffic lights.
     *
     * @return Traffic light nodes.
     */
    public Collection<TrafficLightNode> getTrafficLightNodes() {
        Collection<TrafficLightNode> trafficLightNodes = new ArrayList<>();
        for (Node node : getNodes()) {
            if (node instanceof TrafficLightNode trafficLightNode) {
                trafficLightNodes.add(trafficLightNode);
            }
        }
        return trafficLightNodes;
    }

    /**
     * Returns an unmodifiable view of the known {@link Way}s.
     *
     * @return All ways as {@link Collection}.
     */
    public Collection<Way> getWays() {
        return Collections.unmodifiableCollection(ways.values());
    }

    /**
     * Returns an unmodifiable view of the known {@link Connection}s.
     *
     * @return All connections as {@link Collection}.
     */
    public Collection<Connection> getConnections() {
        return Collections.unmodifiableCollection(connections.values());
    }

    /**
     * Returns all roundabouts from the database.
     *
     * @return All roundabouts in a {@link Collection}.
     */
    public Collection<Roundabout> getRoundabouts() {
        return Collections.unmodifiableCollection(roundabouts);
    }

    /**
     * Returns all buildings from the database.
     *
     * @return All buildings in a {@link Collection}.
     */
    public Collection<Building> getBuildings() {
        return Collections.unmodifiableCollection(buildings.values());
    }

    /**
     * Returns an unmodifiable view of the known {@link Route}s.
     *
     * @return All routes as {@link Collection}.
     */
    public Collection<Route> getRoutes() {
        return Collections.unmodifiableCollection(routes.values());
    }

    /**
     * Returns an unmodifiable view of the known {@link Restriction}s.
     *
     * @return All restrictions as {@link Collection}.
     */
    public Collection<Restriction> getRestrictions() {
        return Collections.unmodifiableCollection(restrictions.values());
    }

    /**
     * Builder class for simple generation for a new {@link Database}.
     */
    public static class Builder {
        /**
         * The {@link Database} to be created.
         */
        private Database database;

        /**
         * Default constructor for the {@link Database.Builder}, creating
         * the {@link Database}-object using {@link Database#VERSION_UNKNOWN}
         * as version for the {@link Database}.
         */
        public Builder() {
            database = new Database(Database.VERSION_UNKNOWN);
        }

        /**
         * Constructor for the {@link Database.Builder}, creating
         * the {@link Database}-object using the given version
         * parameter as version for the {@link Database}.
         *
         * @param version the version to be used for the database
         */
        public Builder(String version) {
            database = new Database(version);
        }

        /**
         * This method loads tries to load a database object from
         * the given {@link File}, which should refer to a database-file.
         * A {@link SQLiteReader} is used for the translation of the database
         * to the Java-Object
         *
         * @param file the database-file
         * @return the builder for easy cascading of methods
         */
        public static Builder loadFromFile(File file) {
            SQLiteReader reader;
            try {
                String contentType = Files.probeContentType(file.toPath());
                if (SQLiteTypeDetector.MIME_TYPE.equals(contentType)) {
                    reader = new SQLiteReader();
                    log.debug("recognized database format is SQLite");
                } else {
                    reader = null;
                    log.error("database format unknown or unsupported: " + contentType);
                }
            } catch (NullPointerException | IOException e) {
                String line = "could not guess database type (%s) make sure it is SQLite";
                throw new RuntimeException(String.format(line, e.getMessage()), e);
            }

            // type was already determined, start loading
            if (reader != null) {
                try {
                    return reader.loadFromFile(file.getCanonicalPath());
                } catch (OutdatedDatabaseException | IOException ode) {
                    throw new RuntimeException(ode);
                }
            } else {
                throw new RuntimeException("database not in a known format");
            }
        }

        /**
         * Overrides the database to be created with the
         * input {@link Database}.
         *
         * @param database the {@link Database}-object, that shall be used as the database
         * @return the builder for easy cascading
         */
        public Builder loadFromDatabase(Database database) {
            this.database = database;
            return this;
        }

        /**
         * Copies all elements of the given {@link Database} into
         * the current one.
         *
         * @param other the {@link Database} to copy into the existing one
         * @return the builder for easy cascading
         */
        @SuppressWarnings("UnusedReturnValue")
        public Builder copyFromDatabase(Database other) {
            other.getNodes().forEach(this::addNode);
            other.getWays().forEach(this::addWay);
            other.getConnections().forEach(this::addConnection);
            other.getRestrictions().forEach(this::addRestriction);
            other.getRoundabouts().forEach(this::addRoundabout);
            other.getBuildings().forEach(this::addBuilding);
            other.getRoutes().forEach(this::addRoute);
            other.properties.forEach(this::addProperty);
            return this;
        }

        /**
         * Returns the current state of the database, it is used to access by different
         * classes, that add fields to this object, but need information already contained in the object.
         *
         * @return the current database
         * @deprecated will be removed in future versions. It should not be required to have access to a Database which is being currently built.
         */
        public Database getIntermediateDatabase() {
            return database;
        }

        /**
         * Resets network components of database.
         *
         * @return the builder for easy cascading
         */
        public Builder clearNetwork() {
            database.connections.clear();
            database.nodes.clear();
            database.restrictions.clear();
            database.ways.clear();
            database.roundabouts.clear();
            database.borderNodes = null;
            database.minBounds.set(90, 180, 0);
            database.maxBounds.set(-90, -180, 0);
            return this;
        }

        /**
         * Adds a new property to the database.
         * Adding a property whose key is already in the list will replace the previous value.
         *
         * @param key   the key of the property
         * @param value the value of the property
         * @return the builder for easy cascading
         */
        public Builder addProperty(String key, String value) {
            database.properties.put(key, value);
            return this;
        }

        /**
         * Adds a new {@link Node} to the database. Be aware, that node IDs have to be distinct!
         * Adding a {@link Node} whose ID is already in the list will replace the previous {@link Node}.
         *
         * @param node Node to add.
         * @return this builder for easy cascading
         * @deprecated use {@link #addNode(String, GeoPoint)} instead
         */
        public Builder addNode(Node node) {
            if (node != null) {
                database.nodes.put(node.getId(), node);
                GeoPoint pos = node.getPosition();
                database.minBounds.set(
                        Math.min(pos.getLatitude(), database.minBounds.getLatitude()),
                        Math.min(pos.getLongitude(), database.minBounds.getLongitude()),
                        Math.min(pos.getAltitude(), database.minBounds.getAltitude()) // this is probably unnecessary
                );
                database.maxBounds.set(
                        Math.max(pos.getLatitude(), database.maxBounds.getLatitude()),
                        Math.max(pos.getLongitude(), database.maxBounds.getLongitude()),
                        Math.max(pos.getAltitude(), database.maxBounds.getAltitude()) // this is probably unnecessary
                );
            } else {
                log.warn("tried to add null node, ignoring!");
            }
            return this;
        }

        /**
         * Adds a new {@link Node} to the database using an id and a position. This method
         * will create a new {@link Node}, add it to the {@link #database} and will return
         * the created {@link Node} for further manipulation of it.
         *
         * @param nodeId   a {@link String} representing a unique id for the {@link Node}
         * @param position the position of the {@link Node}
         * @return the created {@link Node} for further manipulation
         */
        public Node addNode(String nodeId, GeoPoint position) {
            Node node = new Node(nodeId, position);
            addNode(node);
            return node;
        }

        /**
         * Adds a new {@link Node} to the database using an id and a position. This method
         * will create a new {@link Node}, add it to the {@link #database} and will return
         * the created {@link Node} for further manipulation of it. This overload of the
         * method {@link #addNode} is used to add {@link TrafficLightNode}-{@link Node}s
         * to the database.
         *
         * @param nodeId         a {@link String} representing a unique id for the {@link Node}
         * @param position       the position of the {@link Node}
         * @param isTrafficLight boolean to determine if the node to be added is a {@link TrafficLightNode}
         * @return the created {@link Node} for further manipulation
         */
        public Node addNode(String nodeId, GeoPoint position, boolean isTrafficLight) {
            Node node = isTrafficLight ? new TrafficLightNode(nodeId, position) : new Node(nodeId, position);
            addNode(node);
            return node;
        }

        public boolean nodeExists(String nodeId) {
            return database.getNode(nodeId) != null;
        }

        public Node getNode(String nodeId) {
            return database.getNode(nodeId);
        }

        public Collection<Node> getNodes() {
            return database.getNodes();
        }

        /**
         * Adds a new {@link Way} to the database. Be aware that adding a {@link Way} whose ID is
         * already in the list will replace the previous {@link Way}. This method also makes sure, that
         * the {@link Node}s of the way know the way.
         *
         * @param way Way to add.
         * @deprecated use {@link #addWay(String, String, String)} instead
         */
        public Builder addWay(Way way) {
            database.ways.put(way.getId(), way);

            // additionally, make sure the dependent nodes know the way
            // ATTENTION: when loading from database this will most probably be empty anyway at this point!
            for (Node node : way.getNodes()) {
                node.addWay(way);
            }
            return this;
        }

        public Way addWay(String id, String name, String type) {
            Way way = new Way(id, name, type);
            addWay(way);
            return way;
        }

        public Way getWay(String wayId) {
            return database.getWay(wayId);
        }

        public Collection<Way> getWays() {
            return database.getWays();
        }

        /**
         * Adds a new {@link Connection} to the database. Be aware that adding a {@link Connection}
         * whose ID is already in the list will replace the previous {@link Connection}. This method
         * also makes sure, that dependent {@link Node}s and {@link Way}s know the connection.
         *
         * @param connection Connection to add.
         * @return the builder for easy cascading
         * @deprecated use {@link #addConnection(String, String)} instead
         */
        public Builder addConnection(Connection connection) {
            database.connections.put(connection.getId(), connection);

            // additionally, make sure dependent nodes are added
            for (Node node : connection.getNodes()) {
                node.addConnection(connection);
            }

            connection.getWay().addConnection(connection);
            return this;
        }

        /**
         * Adds a new {@link Connection} to the database. Be aware that adding a {@link Connection}
         * whose ID is already in the list will replace the previous {@link Connection}. This method
         * also makes sure, that dependent {@link Node}s and {@link Way}s know the connection.
         *
         * @param connectionId the id of the connection to be added
         * @param wayId        the way id abstracted by the {@link Connection}
         * @return the added {@link Connection} for further manipulation
         */
        public Connection addConnection(String connectionId, String wayId) {
            Way way = Validate.notNull(database.getWay(wayId), "No such way with id " + wayId);
            Connection connection = new Connection(connectionId, way);
            addConnection(connection);
            return connection;
        }

        public Collection<Connection> getConnections() {
            return database.getConnections();
        }

        public Connection getConnection(String connectionId) {
            return database.getConnection(connectionId);
        }


        /**
         * This will add a {@link Node} to a {@link Way}, validating the
         * input parameters against null.
         *
         * @param wayId  the id of the {@link Way} to add the node to
         * @param nodeId the id of the {@link Node} to add to the way
         * @return the builder for easy cascading
         */
        public Builder addNodeToWay(String wayId, String nodeId) {
            Way way = Validate.notNull(database.getWay(wayId), "No such way with id " + wayId);
            Node node = Validate.notNull(database.getNode(nodeId), "No such node with id " + nodeId);
            way.addNode(node);
            node.addWay(way);
            return this;
        }

        /**
         * This will add a {@link Node} to a {@link Connection}, validating the
         * input parameters against null.
         *
         * @param connectionId the id of the {@link Connection} to add the node to
         * @param nodeId       the id of the {@link Node} to be added to the connection
         * @return the builder for easy cascading
         */
        public Builder addNodeToConnection(String connectionId, String nodeId) {
            Connection connection = Validate.notNull(database.getConnection(connectionId), "No such connection with id " + connectionId);
            Node node = Validate.notNull(database.getNode(nodeId), "No such node with id " + nodeId);
            connection.addNode(node);
            return this;
        }

        /**
         * Adds a new {@link Roundabout} to the database.
         *
         * @param roundaboutId the id of the roundabout
         * @param nodes        the nodes belonging to the roundabout
         * @return the builder for easy cascading
         */
        public Roundabout addRoundabout(String roundaboutId, List<Node> nodes) {
            Roundabout roundabout = new Roundabout(roundaboutId, nodes);
            addRoundabout(roundabout);
            return roundabout;
        }

        private Builder addRoundabout(Roundabout roundabout) {
            database.roundabouts.add(roundabout);
            return this;
        }

        /**
         * Adds a new {@link Restriction} to the database. This method will create a new {@link Restriction} object
         * using the given parameters.
         *
         * @param restrictionId the id for the restriction
         * @param type          the {@link Restriction.Type} of the restriction
         * @param sourceWayId   start of the restriction
         * @param viaNodeId     restriction will be via this node
         * @param targetWayId   end of the restriction
         * @return the {@link Restriction} object for further manipulation
         */
        public Restriction addRestriction(String restrictionId, Restriction.Type type, String sourceWayId, String viaNodeId, String targetWayId) {

            Way source = Validate.notNull(database.getWay(sourceWayId), "No such way with id " + sourceWayId);
            Node via = Validate.notNull(database.getNode(viaNodeId), "No such node with id " + viaNodeId);
            Way target = Validate.notNull(database.getWay(targetWayId), "No such way with id " + targetWayId);

            Restriction restriction = new Restriction(restrictionId, type, source, via, target);
            addRestriction(restriction);
            return restriction;
        }

        private void addRestriction(Restriction restriction) {
            database.restrictions.put(restriction.getId(), restriction);
        }

        /**
         * Adds a new building by a given list of corners defining the corners of the building.
         *
         * @param id      the id of the building
         * @param name    the name of the building
         * @param height  the height of the building
         * @param corners the list of geographical points describing the contour of the building
         */
        public Building addBuilding(@Nonnull String id, String name, double height, GeoPoint[] corners) {
            Validate.isTrue(corners.length > 2, "Building with id " + id + " needs at least three corner points");

            final List<Wall> walls = new ArrayList<>();
            Corner from = null;
            for (int i = 1; i < corners.length; i++) {
                if (from == null) {
                    from = new Corner(corners[i - 1]);
                }
                Corner to = new Corner(corners[i]);
                walls.add(new Wall(from, to));
                from = to;
            }
            walls.add(new Wall(from, walls.get(0).getFromCorner())); // close loop

            final Building building = new Building(id, name, height, walls);
            addBuilding(building);
            return building;
        }

        private void addBuilding(Building building) {
            database.buildings.put(building.getId(), building);
        }

        /**
         * Adds a new {@link Route} to the database. Be aware that adding a {@link Route} whose ID
         * is already in the list will replace the previous {@link Route}. This method also returns
         * the {@link RouteBuilder} for further manipulation.
         *
         * @param routeId the id of the route to be added
         */
        public RouteBuilder addRoute(String routeId) {
            return new RouteBuilder(this, routeId);
        }

        /**
         * Adds a new {@link Route} to the database. Be aware that adding a {@link Route} whose ID
         * is already in the list will replace the previous {@link Route}.
         *
         * @param route Route to add.
         * @deprecated use addRoute instead
         */
        public void addRoute(Route route) {
            database.routes.put(route.getId(), route);
        }

        public Route getRoute(String routeId) {
            return database.getRoute(routeId);
        }

        /**
         * Sets the import origin as a new property in the database.
         * Adding a property whose key is already in the list will replace the previous value.
         *
         * @param value the value the import origin should be set to
         */
        public void setImportOrigin(String value) {
            if (database.nodes.isEmpty()) {
                database.properties.put(PROPERTY_IMPORT_ORIGIN, value);
            }
        }

        /**
         * This method cleans the database of unnecessary network
         * elements and returns it.
         *
         * @return the build {@link Database}
         */
        public Database build() {
            return build(true);
        }

        /**
         * This method allows to disable the cleaning of the database.
         * This is mostly of importance for testing purposes.
         *
         * @return the build {@link Database}
         */
        public Database build(boolean cleanupUnusedFields) {
            completeConnections();
            if (cleanupUnusedFields) {
                cleanUnusedWays();
                cleanUnusedRelations();
                cleanUnusedNodes();
            }
            return database;
        }

        /**
         * Cleans the database of detected graphs, used for importing purposes.
         */
        public void cleanGraphs() {
            //prior detected graphs
            ArrayList<Set<Node>> graphs = DatabaseUtils.detectGraphs(database.getNodes());

            // init biggest graph search
            Set<Node> biggestGraph = null;

            // determine biggest graph
            for (Set<Node> graph : graphs) {
                if (biggestGraph == null || graph.size() > biggestGraph.size()) {
                    biggestGraph = graph;
                }
            }

            // remove the graph to keep from the list
            graphs.remove(biggestGraph);

            // now go through all (small) graphs and remove references!
            for (Set<Node> graph : graphs) {
                for (Node node : graph) {
                    database.nodes.remove(node.getId());
                    List<Connection> currentConnections = new ArrayList<>();
                    currentConnections.addAll(node.getIncomingConnections());
                    currentConnections.addAll(node.getPartOfConnections());
                    currentConnections.addAll(node.getOutgoingConnections());
                    for (Connection currentConnection : currentConnections) {
                        database.connections.remove(currentConnection.getId());
                    }
                    for (Way way : node.getWays()) {
                        database.ways.remove(way.getId());
                    }
                }
            }
        }

        private void cleanUnusedWays() {
            // we HAVE to use an iterator here even though it is complex to read,
            // as it's the only way to change the collection while iterating!
            Iterator<Map.Entry<String, Way>> wayIterator = database.ways.entrySet().iterator();
            while (wayIterator.hasNext()) {
                Way way = wayIterator.next().getValue();
                if (way.getNodes().isEmpty()) {
                    log.debug("way '{}' is not used in the traffic network, removing from database", way.getId());
                    wayIterator.remove();
                }
            }
        }

        /**
         * This checks all contained {@link Restriction}s and removes all which reference {@link Node}s
         * or {@link Way}s that apparently have been removed due to being unused.
         */
        private void cleanUnusedRelations() {
            // we HAVE to use an iterator here even though it is complex to read,
            // as it's the only way to change the collection while iterating!
            Iterator<Map.Entry<String, Restriction>> restrictionIter = database.restrictions.entrySet().iterator();
            while (restrictionIter.hasNext()) {
                Restriction restriction = restrictionIter.next().getValue();
                if (!database.nodes.containsKey(restriction.getVia().getId())
                        || !database.ways.containsKey(restriction.getSource().getId())
                        || !database.ways.containsKey(restriction.getTarget().getId())) {
                    log.debug(
                            "restriction '{}' references ways or nodes that are not used in the traffic network, removing from database",
                            restriction.getId()
                    );
                    restrictionIter.remove();
                }
            }
        }

        /**
         * This checks all contained {@link Node}s and removes all which have no reference to
         * {@link Way}s. Be aware that a consistent database cannot have {@link Node}s with references
         * to {@link Connection}s but not {@link Way}s! Therefore it is only checked against {@link Way}s.
         */
        private void cleanUnusedNodes() {
            // we HAVE to use an iterator here even though it is complex to read,
            // as it's the only way to change the collection while iterating!
            Iterator<Map.Entry<String, Node>> nodeIter = database.nodes.entrySet().iterator();
            while (nodeIter.hasNext()) {
                Node node = nodeIter.next().getValue();
                if (node.getWays().isEmpty()) {
                    log.debug("node '{}' is not used in the traffic network, removing from database", node.getId());
                    nodeIter.remove();
                }
            }
        }

        /**
         * This clears the list of routes.
         * Should <b>only</b> be called when importing routes!
         */
        public void clearRoutes() {
            database.routes.clear();
        }

        /**
         * Sets the "intersection" flag for all nodes, which have more than 2 neighboring nodes.
         */
        public void calculateIntersections() {
            for (Node node : database.getNodes()) {
                node.setIntersection(DatabaseUtils.isIntersection(node));
            }
        }

        public void completeConnections() {
            for (Connection connection : database.getConnections()) {
                for (Node node : connection.getNodes()) {
                    node.addConnection(connection);
                }
            }

            // now first simply add all from/to connections
            for (Connection fromConnection : database.connections.values()) {
                for (Connection toConnection : fromConnection.getTo().getOutgoingConnections()) {
                    fromConnection.addOutgoingConnection(toConnection);
                }
            }

            // now the to/from connections
            for (Connection toConnection : database.connections.values()) {
                for (Connection fromConnection : toConnection.getFrom().getIncomingConnections()) {
                    toConnection.addIncomingConnection(fromConnection);
                }
            }

            // now apply all restrictions
            for (Restriction restriction : database.restrictions.values()) {
                restriction.applyRestriction();
            }
        }

        public boolean connectionExists(String connectionId) {
            return database.getConnection(connectionId) != null;
        }
    }

    public static class RouteBuilder {

        private final Route route;
        private final Database.Builder builder;

        private RouteBuilder(Builder builder, String routeId) {
            this.builder = builder;
            this.route = new Route(routeId);
        }

        public RouteBuilder addConnection(String connectionId) {
            Connection connection = builder.database.getConnection(connectionId);
            route.addConnection(connection);
            return this;
        }

        public Database.Builder create() {
            builder.addRoute(route);
            return builder;
        }
    }

}
