/*
 * Copyright (c) 2024 Fraunhofer FOKUS and others. All rights reserved.
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

import org.eclipse.mosaic.lib.math.Matrix3d;
import org.eclipse.mosaic.lib.math.Vector3d;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class represents a point cloud based on the {@link Vector3d} coordinate system.
 * <ul> <li>It consists of a reference point which represents the origin/center of this point cloud
 * in absolute world coordinates.</li>
 * <li>A rotation matrix used to translate relative end points of the point cloud to absolute coordinates.</li>
 * <li>A list of points (x,y,z, hit type, distance to origin) forming this point cloud.</li>
 * <li>Use one of the given {@link PointReference} formats to declare the reference frame of the given point lists. If
 *     they are already in absolute world coordinates, use {@link PointReference#ABSOLUTE}. If the reference frame of the
 *     points are {@link #origin} and  {@link #orientation}, then use {@link PointReference#RELATIVE}.
 * </ul>
 * When calling the methods {@link #getRelativeEndPoints()} or {@link #getAbsoluteEndPoints()}, a transformation of the stored points
 * may be executed (and cached for re-use). E.g., if the point cloud is constructed using absolute coordinates
 * (i.e. {@link PointReference#ABSOLUTE}), a call of {@link #getAbsoluteEndPoints()} will return the stored point list as-is, whereas
 * a call of {@link #getRelativeEndPoints()} will transform the stored points to relative coordinates (relatively to  {@link #origin} and
 * {@link #orientation}).
 */
public final class PointCloud implements Serializable {

    private static final Predicate<Point> POINTS_ALL = p -> true;
    private static final Predicate<Point> POINTS_WITH_HITS = Point::hasHit;

    public enum PointReference {
        ABSOLUTE(Transformation::absoluteToRelative, Transformation::noTransformation),
        RELATIVE(Transformation::noTransformation, Transformation::relativeToAbsolute);

        PointReference(Transformation toRelative, Transformation toAbsolute) {
            this.toRelative = toRelative;
            this.toAbsolute = toAbsolute;
        }

        private final Transformation toRelative;
        private final Transformation toAbsolute;
    }

    private static final long serialVersionUID = 1L;

    private final Vector3d origin = new Vector3d();
    private final RotationMatrix orientation = new RotationMatrix();
    private final long creationTime;

    private final PointReference pointsReference;
    private final List<Point> points;

    private transient List<Point> absoluteEndPoints = null;
    private transient List<Point> absoluteEndPointsWithHit = null;
    private transient List<Point> relativeEndPoints = null;
    private transient List<Point> relativeEndPointsWithHit = null;

    /**
     * Creates a new PointCloud based on a list of {@link Point}s. The points may be given (and then stored) either in absolute
     * world coordinates, or relative coordinates compared to the given origin and orientation field. The reference frame of the points
     * in the given {@link Point} list must be declared by using {@link PointReference#ABSOLUTE} or {@link PointReference#RELATIVE}.
     *
     * @param creationTime    the creation time of the point cloud in nanoseconds
     * @param origin          the origin point of the point cloud in world coordinates
     * @param orientation     the orientation of the point cloud
     * @param points          a list of points, containing point coordinates in either absolute or relative format
     * @param pointsReference the reference format of the coordinates of the points (absolute world coordinates, relative coordinates)
     */
    public PointCloud(long creationTime, Vector3d origin, RotationMatrix orientation, List<Point> points, PointReference pointsReference) {
        this.creationTime = creationTime;
        this.origin.set(origin);
        this.orientation.set(orientation);
        this.points = points;
        this.pointsReference = pointsReference;
    }

    /**
     * Returns the simulation time when the {@link PointCloud} was created.
     *
     * @return time in nanoseconds
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Returns the origin point of this point cloud in absolute world coordinates.
     * Points returned by {@link #getRelativeEndPoints} are relative to this origin.
     *
     * @return origin of the rays forming this point cloud in absolute coordinates
     * @see #getOrientation()
     */
    public Vector3d getOrigin() {
        return origin;
    }

    /**
     * Returns the orientation of the point cloud as {@link RotationMatrix}. Points returned by {@link #getRelativeEndPoints} are relative
     * to {@link #getOrigin()} and this orientation matrix. Points returned by {@link #getAbsoluteEndPoints()} are translated and rotated
     * using {@link #getOrigin()} and this orientation matrix
     * <p/>
     * Let o be the ray origin in absolute coordinates, let R be the rotation matrix. Thus, the transformation
     * of relative coordinates r into absolute coordinates a is
     * <code>a = R * r + o</code>
     *
     * @return rotation matrix R
     * @see #getOrigin() returns <code>o</code>
     */
    public RotationMatrix getOrientation() {
        return orientation;
    }

    /**
     * Returns all end points of all rays of this {@link PointCloud} that hit something in relative coordinates.
     * Relative coordinates mean the use of a cartesian coordinate system with its origin at
     * the {@link PointCloud}'s origin, returned by {@link #getOrigin()}. In addition, the
     * coordinate system is rotated. See {@link #getOrientation()} for details.
     *
     * @return end points of all rays of this {@link PointCloud} in relative coordinates
     * @see #getOrigin() origin of ray / translation of coordinates
     * @see #getOrientation() rotation of coordinates
     */
    public List<Point> getRelativeEndPoints() {
        if (relativeEndPoints == null) {
            relativeEndPoints = pointsReference.toRelative.transform(this, POINTS_ALL);
        }
        return relativeEndPoints;
    }


    /**
     * Returns all end points of all rays of this {@link PointCloud} that hit something in relative coordinates.
     * Relative coordinates mean the use of a cartesian coordinate system with its origin (0,0,0) at
     * the {@link PointCloud}'s origin, returned by {@link #getOrigin()}. In addition, the
     * coordinate system is rotated. See {@link #getOrientation()} for details.
     *
     * @return end points of all rays of this {@link PointCloud} in relative coordinates
     * @see #getOrigin() origin of ray / translation of coordinates
     * @see #getOrientation() rotation of coordinates
     */
    public List<Point> getRelativeEndPointsWithHit() {
        if (relativeEndPointsWithHit == null) {
            relativeEndPointsWithHit = pointsReference.toRelative.transform(this, POINTS_WITH_HITS);
        }
        return relativeEndPointsWithHit;
    }

    /**
     * Returns all end points of all rays of this {@link PointCloud} in absolute coordinates.
     * Absolute coordinates represent world coordinates, and have {@link #getOrigin()} and {@link #getOrientation()}
     * already applied.
     *
     * @return a list of points in absolute coordinates
     */
    public List<Point> getAbsoluteEndPoints() {
        if (absoluteEndPoints == null) {
            absoluteEndPoints = pointsReference.toAbsolute.transform(this, POINTS_ALL);
        }
        return absoluteEndPoints;
    }

    /**
     * Returns all end points of all rays of this {@link PointCloud} that hit something in absolute coordinates.
     * Absolute coordinates represent world coordinates, and have {@link #getOrigin()} and {@link #getOrientation()}
     * already applied.
     *
     * @return a list of points in absolute coordinates
     */
    public List<Point> getAbsoluteEndPointsWithHit() {
        if (absoluteEndPointsWithHit == null) {
            absoluteEndPointsWithHit = pointsReference.toAbsolute.transform(this, POINTS_WITH_HITS);
        }
        return absoluteEndPointsWithHit;
    }


    /**
     * Returns the type of reference frame the points are stored in this point cloud internally. The usage of
     * {@link #getAbsoluteEndPoints()} and {@link #getRelativeEndPoints()} is independent of the value returned
     * by this method, as transformation is already done internally if necessary. Therefore, it should
     * usually not be required to use this method, except for (de)serialization use-cases.
     *
     * @return the {@link PointReference} type of the points stored in the point cloud.
     */
    public PointReference getReferenceFormat() {
        return pointsReference;
    }

    /**
     * A {@link Point} of the point cloud consists of its coordinates, an identifier
     * of the type of object the point has hit, and the distance to the point cloud origin.
     */
    public static class Point extends Vector3d {

        private static final long serialVersionUID = 1L;

        private final byte hitType;
        private final float distance;

        /**
         * Creates a new point to add to a {@link PointCloud} by specifying the distance
         * to origin and the type of hit object (encoded in byte) next to the location of the point.
         *
         * @param endPoint the coordinates of the point cloud
         * @param distance the distance to the origin of the point cloud
         * @param hitType  the type of hit object represented by this point. 0 = no hit
         */
        public Point(Vector3d endPoint, float distance, byte hitType) {
            x = endPoint.x;
            y = endPoint.y;
            z = endPoint.z;
            this.distance = distance;
            this.hitType = hitType;
        }

        /**
         * Returns true if the ray generating this {@link Point} has hit an object.
         */
        public boolean hasHit() {
            return hitType != 0;
        }

        /**
         * Returns the type of the object the ray generating this point has hit. (0 = no hit)
         */
        public byte getHitType() {
            return hitType;
        }

        /**
         * Returns the distance to the origin of point cloud this point belongs to.
         */
        public float getDistance() {
            return distance;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Point other = (Point) o;
            return super.equals(other)
                    && this.hitType == other.hitType
                    && Float.compare(this.distance, other.distance) == 0;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + Byte.hashCode(hitType);
            result = 31 * result + Float.hashCode(distance);
            return result;
        }
    }

    private interface Transformation {

        List<Point> transform(PointCloud pointCloud, Predicate<Point> filter);

        private static List<Point> relativeToAbsolute(PointCloud pointCloud, Predicate<Point> filter) {
            return pointCloud.points.stream()
                    .filter(filter)
                    .map(point -> (Point) pointCloud.orientation.multiply(new Point(point, point.getDistance(), point.getHitType())).add(pointCloud.origin))
                    .collect(Collectors.toList());
        }

        private static List<Point> absoluteToRelative(PointCloud pointCloud, Predicate<Point> filter) {
            Matrix3d inv = pointCloud.orientation.transpose(new Matrix3d());
            return pointCloud.points.stream()
                    .filter(filter)
                    .map(point -> (Point) inv.multiply(point.subtract(pointCloud.origin, new Point(point, point.getDistance(), point.getHitType()))))
                    .collect(Collectors.toList());
        }

        private static List<Point> noTransformation(PointCloud pointCloud, Predicate<Point> filter) {
            return filter == POINTS_ALL
                    ? Collections.unmodifiableList(pointCloud.points)
                    : pointCloud.points.stream()
                    .filter(filter)
                    .collect(Collectors.toList());
        }
    }
}
