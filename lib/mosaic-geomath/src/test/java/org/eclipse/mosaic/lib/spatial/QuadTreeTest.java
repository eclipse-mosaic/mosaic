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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.mosaic.lib.math.Vector3d;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class QuadTreeTest {


    private QuadTree<Vector3d> tree;

    @Before
    public void setup() {
        // setup new tree for each test
        QuadTree.configure(2, 1, 3);
        BoundingBox treeBounds = new BoundingBox();
        treeBounds.add(new Vector3d(0, 0, 0));
        treeBounds.add(new Vector3d(100, 0, 100));
        tree = new QuadTree<>(new SpatialItemAdapter.PointAdapter<>(), treeBounds);
    }

    @Test
    public void addObject_Success() {
        assertTrue(tree.addItem(new Vector3d(5, 0, 5)));
        assertEquals(1, tree.getSize());
        assertEquals(1, tree.getAllObjects().size());
    }

    @Test
    public void removeObject_Success() {
        Vector3d element = new Vector3d(5, 0, 5);
        assertTrue(tree.addItem(element));
        assertEquals(1, tree.getSize());
        assertEquals(1, tree.getAllObjects().size());
        tree.removeObject(element);
        assertEquals(0, tree.getSize());
        assertEquals(0, tree.getAllObjects().size());
    }

    @Test
    public void addObject_Failure() {
        assertFalse(tree.addItem(new Vector3d(-10, 0, -10)));
        assertEquals(0, tree.getSize());
        assertEquals(0, tree.getAllObjects().size());
    }

    @Test
    public void queryRange_Success() {
        // SETUP
        tree.addItem(new Vector3d(5, 0, 5));
        BoundingBox queryRange = new BoundingBox();
        queryRange.add(new Vector3d(0, 0, 0));
        queryRange.add(new Vector3d(10, 0, 10));
        List<Vector3d> result = new ArrayList<>();
        QuadTreeTraversal.getObjectsInBoundingArea(tree, queryRange, result);
        assertEquals(1, result.size());
    }

    @Test
    public void queryRange_Failure_NoObjectsInArea() {
        // SETUP
        tree.addItem(new Vector3d(5, 0, 5));
        BoundingBox queryRange = new BoundingBox();
        queryRange.add(new Vector3d(6, 0, 6));
        queryRange.add(new Vector3d(10, 0, 10));
        List<Vector3d> result = new ArrayList<>();
        QuadTreeTraversal.getObjectsInBoundingArea(tree, queryRange, result);
        assertEquals(0, result.size());
    }

    @Test
    public void queryRange_Success_TestIntersectMethod() {
        // SETUP
        tree.addItem(new Vector3d(5, 0, 5));
        tree.addItem(new Vector3d(10, 0, 10));
        tree.addItem(new Vector3d(15, 0, 15));
        BoundingBox queryRange = new BoundingBox();
        queryRange.add(new Vector3d(13, 0, 12));
        queryRange.add(new Vector3d(24, 0, 26));
        List<Vector3d> result = new ArrayList<>();
        QuadTreeTraversal.getObjectsInBoundingArea(tree, queryRange, result);
        assertEquals(3, tree.getSize());
        assertEquals(1, result.size());
    }

    @Test
    public void joinFunctionality() {
        // SETUP
        Vector3d element1 = new Vector3d(5, 0, 5);
        Vector3d element2 = new Vector3d(75, 0, 75);
        tree.addItem(element1);
        tree.addItem(element2);
        tree.addItem(new Vector3d(75, 0, 0));
        assertEquals(4, tree.getRoot().childNodes.length);
        for (int i = 0; i < 4; i++) {
            assertTrue(tree.getRoot().childNodes[i].isLeaf());
        }
        tree.removeObject(element1);
        tree.removeObject(element2);
        assertTrue(tree.getRoot().isLeaf());

    }
}
