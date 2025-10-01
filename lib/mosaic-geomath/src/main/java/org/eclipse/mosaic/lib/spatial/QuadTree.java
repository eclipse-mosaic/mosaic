/*
 * Copyright (c) 2022 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.lib.spatial;

import org.eclipse.mosaic.lib.math.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Implements a spatial index for 2-dimensional objects (on the X,Z plane) based on Quad-Tree implementation. Each leaf can store multiple objects, allowing
 * them to be moved inside the bounds of its quad tile without removing and adding them to the tree again.
 * <p/>
 * Note: Currently works only for point-based spatial items.
 *
 * @param <T> the item type to store inside the tree
 */
public class QuadTree<T> {

    private static int SPLIT_SIZE = 20;
    private static int JOIN_SIZE = 10;
    private static int MAX_DEPTH = 12;

    private final TreeNode root;

    private final Map<Integer, ObjectAndNode> objects = new HashMap<>();
    private final SpatialItemAdapter<T> adapter;

    /**
     * Creates a Quad-Tree for indexing objects of type T covering the given area.
     *
     * @param adapter    the adapter used to determine center coordinates of any object of type T
     * @param treeBounds the bound of the area this tree should cover (on the X,Z plane)
     */
    public QuadTree(final SpatialItemAdapter<T> adapter, final BoundingBox treeBounds) {
        this(adapter, treeBounds.min.x, treeBounds.max.x, treeBounds.min.z, treeBounds.max.z);
    }


    /**
     * Creates a Quad-Tree for indexing objects of type T covering the given area.
     *
     * @param adapter the adapter used to determine center coordinates of any object of type T
     * @param minX    the bounds of the area this tree should cover (on the X,Z plane)
     * @param maxX    the bounds of the area this tree should cover (on the X,Z plane)
     * @param minZ    the bounds of the area this tree should cover (on the X,Z plane)
     * @param maxZ    the bounds of the area this tree should cover (on the X,Z plane)
     */
    public QuadTree(final SpatialItemAdapter<T> adapter, double minX, double maxX, double minZ, double maxZ) {
        root = new TreeNode(0, minX, maxX, minZ, maxZ);
        this.adapter = adapter;
    }

    /**
     * Collects and returns all objects stored in this spatial index.
     *
     * @return all objects stored in this spatial index.
     */
    public Collection<T> getAllObjects() {
        return objects.values().stream().map(o -> o.object).collect(Collectors.toList());
    }

    /**
     * Searches all objects within the given radius around a center point.
     *
     * @param center the center point for the range search
     * @param radius the radius for range search
     * @return the list of results
     */
    public List<T> getObjectsInRadius(Vector3d center, double radius) {
        return getObjectsInRadius(center, radius, new ArrayList<>());
    }

    /**
     * Searches all objects within the given radius around a center point.
     *
     * @param center the center point for the range search
     * @param radius the radius for range search
     * @param result the list of results
     * @return the list of results
     */
    public List<T> getObjectsInRadius(Vector3d center, double radius, List<T> result) {
        return getObjectsInRadius(center, radius, null, result);
    }

    /**
     * Searches all objects within the given radius around a center point.
     *
     * @param center the center point for the range search
     * @param radius the radius for range search
     * @param filter a predicate to exclude certain objects from the result list
     * @param result the list of results
     * @return the list of results
     */
    public List<T> getObjectsInRadius(Vector3d center, double radius, Predicate<T> filter, List<T> result) {
        QuadTreeTraversal.getObjectsInRadius(this, center, radius, filter, result);
        return result;
    }

    /**
     * Searches all objects within the given bounding area.
     *
     * @param area the rectangle area for range search
     * @return the list of results
     */
    public List<T> getObjectsInBoundingArea(BoundingBox area) {
        return getObjectsInBoundingArea(area, new ArrayList<>());
    }

    /**
     * Searches all objects within the given bounding area.
     *
     * @param area   the rectangle area for range search
     * @param result the list of results
     * @return the list of results
     */
    public List<T> getObjectsInBoundingArea(BoundingBox area, List<T> result) {
        return getObjectsInBoundingArea(area, null, result);
    }

    /**
     * Searches all objects within the given bounding area.
     *
     * @param area   the rectangle area for range search
     * @param filter a predicate to exclude certain objects from the result list
     * @param result the list of results
     * @return the list of results
     */
    public List<T> getObjectsInBoundingArea(BoundingBox area, Predicate<T> filter, List<T> result) {
        QuadTreeTraversal.getObjectsInBoundingArea(this, area, filter, result);
        return result;
    }

    /**
     * Search for the nearest object for the given point.
     *
     * @param center the point to find the nearest object
     * @return the nearest object, or <code>null</code> if not found
     */
    public T getNearestObject(Vector3d center) {
        return getNearestObject(center, null);
    }

    /**
     * Search for the nearest object for the given point.
     *
     * @param center the point to find the nearest object
     * @param filter a predicate to exclude certain objects from the result list
     * @return the nearest object, or <code>null</code> if not found
     */
    public T getNearestObject(Vector3d center, Predicate<T> filter) {
        return QuadTreeTraversal.getNearestObject(this, center, filter);
    }

    public int getSize() {
        return root.objectsCount;
    }

    public boolean addItem(T item) {
        ObjectAndNode oan = new ObjectAndNode(item);
        if (root.isInBounds(oan.objectPos)) {
            objects.put(adapter.getItemHash(item), oan);
            root.addObjectNode(oan);
            return true;
        }
        return false;
    }

    public void removeObject(T object) {
        ObjectAndNode oan = objects.remove(adapter.getItemHash(object));
        if (oan != null) {
            root.removeObjectNode(oan);
        }
    }

    public void updateTree() {
        for (ObjectAndNode oan : objects.values()) {
            oan.update();
        }
    }

    public void clear() {
        objects.clear();
        root.clear();
    }

    TreeNode getRoot() {
        return root;
    }

    public static void configure(int splitSize, int joinSize, int maxDepth) {
        if (splitSize < 1) {
            throw new IllegalArgumentException("Split size must be greater than 0");
        }

        if (splitSize < joinSize) {
            throw new IllegalArgumentException("Join size must be lower than split size");
        }

        if (maxDepth < 1) {
            throw new IllegalArgumentException("Max depth must be greater than 0");
        }

        SPLIT_SIZE = splitSize;
        JOIN_SIZE = joinSize;
        MAX_DEPTH = maxDepth;
    }


    class ObjectAndNode {
        TreeNode node = null;
        final T object;

        final Vector3d objectPos = new Vector3d();
        private final Vector3d newPos = new Vector3d();

        private ObjectAndNode(T object) {
            this.object = object;
            objectPos.set(
                    adapter.getCenterX(object),
                    adapter.getCenterY(object),
                    adapter.getCenterZ(object)
            );
        }

        private void update() {
            newPos.set(
                    adapter.getCenterX(object),
                    adapter.getCenterY(object),
                    adapter.getCenterZ(object)
            );
            if (node != null) {
                if (!node.isInBounds(newPos)) {
                    root.removeObjectNode(this);
                    objectPos.set(newPos);
                    root.addObjectNode(this);
                }
            }
            objectPos.set(newPos);
        }
    }

    static class TreeNode {
        final double minX;
        final double maxX;
        final double minZ;
        final double maxZ;
        final int depth;

        final ArrayList<QuadTree<?>.ObjectAndNode> objects = new ArrayList<>();
        int objectsCount = 0;
        TreeNode[] childNodes = null;

        private TreeNode(int depth, double minX, double maxX, double minZ, double maxZ) {
            this.depth = depth;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        boolean isLeaf() {
            return childNodes == null;
        }

        int getChildIndex(Vector3d p) {
            double cx = (minX + maxX) / 2;
            double cz = (minZ + maxZ) / 2;
            return (p.x > cx ? 2 : 0) | (p.z > cz ? 1 : 0);
        }

        boolean isInBounds(Vector3d p) {
            return isInBounds(p.x, p.y, p.z);
        }

        boolean isInBounds(double x, double y, double z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        double distanceSqrToPoint(Vector3d p) {
            if (isInBounds(p)) {
                return 0;
            }

            double x = 0.0f;
            double z = 0.0f;

            double tmp = p.x - minX;
            if (tmp < 0) {
                // px < minX
                x = tmp;
            } else {
                tmp = maxX - p.x;
                if (tmp < 0) {
                    // px > maxX
                    x = tmp;
                }
            }

            tmp = p.z - minZ;
            if (tmp < 0) {
                // pz < minZ
                z = tmp;
            } else {
                tmp = maxZ - p.z;
                if (tmp < 0) {
                    // pz > maxZ
                    z = tmp;
                }
            }

            return x * x + z * z;
        }

        boolean intersects(BoundingBox area) {
            return area.min.x <= maxX
                    && area.max.x >= minX
                    && area.min.z <= maxZ
                    && area.max.z >= minZ;
        }

        private void addObjectNode(QuadTree<?>.ObjectAndNode item) {
            objectsCount++;

            if (isLeaf()) {
                objects.add(item);
                item.node = this;
                if (objectsCount > SPLIT_SIZE && depth < MAX_DEPTH) {
                    split();
                }

            } else {
                childNodes[getChildIndex(item.objectPos)].addObjectNode(item);
            }
        }

        private void removeObjectNode(QuadTree<?>.ObjectAndNode item) {
            objectsCount--;

            if (childNodes == null) {
                objects.remove(item);
                item.node = null;

            } else {
                childNodes[getChildIndex(item.objectPos)].removeObjectNode(item);
                if (objectsCount <= JOIN_SIZE) {
                    join();
                }
            }
        }

        private void split() {
            double cx = (minX + maxX) / 2;
            double cz = (minZ + maxZ) / 2;

            childNodes = new TreeNode[]{
                    new TreeNode(depth + 1, minX, cx, minZ, cz),
                    new TreeNode(depth + 1, minX, cx, cz, maxZ),
                    new TreeNode(depth + 1, cx, maxX, minZ, cz),
                    new TreeNode(depth + 1, cx, maxX, cz, maxZ)
            };
            for (int i = 0; i < objects.size(); i++) {
                QuadTree<?>.ObjectAndNode object = objects.get(i);
                childNodes[getChildIndex(object.objectPos)].addObjectNode(object);
            }
            objects.clear();
        }

        private void join() {
            for (int i = 0; i <= 3; i++) {
                objects.addAll(childNodes[i].objects);
            }
            childNodes = null;
        }

        private void clear() {
            if (childNodes != null) {
                for (int i = 0; i < 4; i++) {
                    childNodes[i].clear();
                }
                childNodes = null;
            }
            objectsCount = 0;
        }
    }

}