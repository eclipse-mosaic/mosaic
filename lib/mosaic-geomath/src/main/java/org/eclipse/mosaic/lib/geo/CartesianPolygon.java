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

package org.eclipse.mosaic.lib.geo;

import org.eclipse.mosaic.lib.math.MathUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CartesianPolygon implements Polygon<CartesianPoint>, CartesianArea {

    private static final long serialVersionUID = 1L;

    private final List<CartesianPoint> vertices;
    private final CartesianRectangle boundingBox;

    public CartesianPolygon(CartesianPoint... coordinates) {
        this(Arrays.asList(coordinates));
    }

    /**
     * Construct a new {@link CartesianPolygon}.
     *
     * @param coordinates A list of lists of {@link CartesianPoint}s
     */
    public CartesianPolygon(List<CartesianPoint> coordinates) {
        final List<CartesianPoint> verticesTmp = new ArrayList<>(coordinates);
        if (!verticesTmp.get(0).equals(verticesTmp.get(verticesTmp.size() - 1))) {
            verticesTmp.add(verticesTmp.get(0));
        }

        if (verticesTmp.size() < 3) {
            throw new IllegalArgumentException("The polygon must consists of two different vertices at least.");
        }

        boundingBox = calcBoundingBox(verticesTmp);
        vertices = Collections.unmodifiableList(verticesTmp);
    }

    private CartesianRectangle calcBoundingBox(List<CartesianPoint> vertices) {
        double maxY = -Long.MAX_VALUE;
        double minY = Long.MAX_VALUE;
        double maxX = -Long.MAX_VALUE;
        double minX = Long.MAX_VALUE;
        for (CartesianPoint point : vertices) {
            maxY = Math.max(maxY, point.getY());
            minY = Math.min(minY, point.getY());
            maxX = Math.max(maxX, point.getX());
            minX = Math.min(minX, point.getX());
        }
        return new CartesianRectangle(CartesianPoint.xy(minX, maxY), CartesianPoint.xy(maxX, minY));
    }

    public List<CartesianPoint> getVertices() {
        return vertices;
    }

    @Override
    public Bounds<CartesianPoint> getBounds() {
        return boundingBox;
    }

    @Override
    public boolean contains(CartesianPoint point) {
        float[] verticesXValues = new float[vertices.size()];
        float[] verticesYValues = new float[vertices.size()];

        for (int i = 0; i < vertices.size(); i++) {
            CartesianPoint geoPoint = vertices.get(i);
            verticesXValues[i] = (float) geoPoint.getX();
            verticesYValues[i] = (float) geoPoint.getY();
        }

        return MathUtils.pnpoly(vertices.size(), verticesXValues, verticesYValues, (float) point.getX(), (float) point.getY());
    }

    public GeoPolygon toGeo() {
        return new GeoPolygon(
                getVertices().stream().map(CartesianPoint::toGeo).collect(Collectors.toList())
        );
    }

    /**
     * Returns true if there is an intersection between two line segments.
     *
     * @param edge1A Start point of the first line segment.
     * @param edge1B End point of the first line segment
     * @param edge2A Start point of the second line segment.
     * @param edge2B End point of the second line segment.
     * @return true if the line segments intersect.
     */
    private boolean isIntersectingEdge(CartesianPoint edge1A, CartesianPoint edge1B, CartesianPoint edge2A, CartesianPoint edge2B) {
        CartesianPoint r = new MutableCartesianPoint(edge1A.getX() - edge1B.getX(), edge1A.getY() - edge1B.getY(), 0);
        CartesianPoint s = new MutableCartesianPoint(edge2A.getX() - edge2B.getX(), edge2A.getY() - edge2B.getY(), 0);
        CartesianPoint diff = new MutableCartesianPoint(edge2A.getX() - edge1A.getX(), edge2A.getY() - edge1A.getY(), 0);
        double crossprod1 = r.getX() * s.getY() - r.getY() * s.getX();
        double crossprod2 = diff.getX() * r.getY() - diff.getY() * r.getX();

        if (crossprod1 == 0 && crossprod2 == 0) {
            // Vectors of edges are collinear, check for mutual line segment
            return edge1A.getX() >= edge2A.getX() && edge1A.getX() < edge2B.getX() ||
                    edge1B.getX() >= edge2A.getX() && edge1B.getX() < edge2B.getX() ||
                    edge2A.getX() >= edge1A.getX() && edge2A.getX() < edge1B.getX() ||
                    edge2B.getX() >= edge1A.getX() && edge2B.getX() < edge1B.getX();
        } else if (crossprod1 != 0) {
            // Vectors of edges are neither parallel nor collinear, check for intersection
            double u = crossprod2 / crossprod1;
            double t = (diff.getX() * s.getY() - diff.getY() * s.getX()) / crossprod1;
            return (u >= 0 && u <= 1 && t >= 0 && t <= 1) || (u <= 0 && u >= -1 && t <= 0 && t >= -1);
        }
        return false;
    }

    /**
     * Returns true if there is an intersection with another polygon.
     *
     * @param polygon The other polygon
     * @return true if the polygons intersect.
     */
    public boolean isIntersectingPolygon(CartesianPolygon polygon) {
        // Test if bounding boxes intersect
        if (!polygon.boundingBox.isIntersectingRectangle(this.boundingBox)){
            return false;
        }
        // Test if any polygon is completely contained within the other polygon
        if (contains(polygon.getVertices().get(0)) || polygon.contains(vertices.get(0))) {
            return true;
        }
        // Test if any edges of the polygons intersect
        for (int vIdx1 = 1; vIdx1 < vertices.size() ; vIdx1++) {
            CartesianPoint edgeP1A = vertices.get(vIdx1 - 1);
            CartesianPoint edgeP1B = vertices.get(vIdx1);
            for (int vIdx2 = 1; vIdx2 < polygon.getVertices().size() ; vIdx2++) {
                CartesianPoint edgeP2A = polygon.getVertices().get(vIdx2 - 1);
                CartesianPoint edgeP2B = polygon.getVertices().get(vIdx2);
                if (isIntersectingEdge(edgeP1A, edgeP1B, edgeP2A, edgeP2B)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CartesianPolygon other = (CartesianPolygon) o;
        return this.getVertices().equals(other.getVertices());
    }

    @Override
    public int hashCode() {
        long longHash = 898912L;
        for (CartesianPoint corner : getVertices()) {
            longHash = 31 * longHash + corner.hashCode();
        }
        return (int) (longHash ^ (longHash >>> 32));
    }

}
