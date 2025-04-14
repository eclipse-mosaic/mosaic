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

public class GeoPolygon implements Polygon<GeoPoint>, GeoArea {

    private static final long serialVersionUID = 1L;

    private final List<GeoPoint> vertices;
    private transient GeoRectangle boundingBox;

    public GeoPolygon(GeoPoint... coordinates) {
        this(Arrays.asList(coordinates));
    }

    /**
     * Construct a new {@link GeoPolygon}.
     *
     * @param coordinates A list of lists of {@link GeoPoint}s
     */
    public GeoPolygon(List<GeoPoint> coordinates) {
        vertices = new ArrayList<>(coordinates);
        if (!vertices.get(0).equals(vertices.get(vertices.size() - 1))) {
            vertices.add(vertices.get(0));
        }

        if (vertices.size() < 3) {
            throw new IllegalArgumentException("The polygon must consists of two different vertices at least.");
        }
    }

    private GeoRectangle calcBoundingBox(List<GeoPoint> vertices) {
        double maxLat = -90;
        double minLat = 90;
        double maxLon = -180;
        double minLon = 180;
        for (GeoPoint point : vertices) {
            maxLat = Math.max(maxLat, point.getLatitude());
            minLat = Math.min(minLat, point.getLatitude());
            maxLon = Math.max(maxLon, point.getLongitude());
            minLon = Math.min(minLon, point.getLongitude());
        }
        return new GeoRectangle(GeoPoint.latLon(maxLat, minLon), GeoPoint.latLon(minLat, maxLon));
    }

    public List<GeoPoint> getVertices() {
        return Collections.unmodifiableList(vertices);
    }

    @Override
    public Bounds<GeoPoint> getBounds() {
        if (boundingBox == null) {
            boundingBox = calcBoundingBox(vertices);
        }
        return boundingBox;
    }

    @Override
    public boolean contains(GeoPoint point) {
        float[] verticesXValues = new float[vertices.size()];
        float[] verticesYValues = new float[vertices.size()];

        for (int i = 0; i < vertices.size(); i++) {
            GeoPoint geoPoint = vertices.get(i);
            CartesianPoint cartesianPoint = geoPoint.toCartesian();
            verticesXValues[i] = (float) cartesianPoint.getX();
            verticesYValues[i] = (float) cartesianPoint.getY();
        }

        return MathUtils.pnpoly(
                vertices.size(), verticesXValues, verticesYValues,
                (float) point.toCartesian().getX(), (float) point.toCartesian().getY()
        );
    }

    public CartesianPolygon toCartesian() {
        return new CartesianPolygon(
                getVertices().stream().map(GeoPoint::toCartesian).collect(Collectors.toList())
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GeoPolygon other = (GeoPolygon) o;
        return this.getVertices().equals(other.getVertices());
    }

    @Override
    public int hashCode() {
        long longHash = 898912L;
        for (GeoPoint corner : getVertices()) {
            longHash = 31 * longHash + corner.hashCode();
        }
        return (int) (longHash ^ (longHash >>> 32));
    }

}
