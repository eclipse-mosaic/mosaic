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

import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.transform.GeoProjection;

import java.util.Locale;

public class MutableGeoPoint implements GeoPoint {

    private static final long serialVersionUID = 1L;

    private static final double LONGITUDE_MAX = 180;
    private static final double LONGITUDE_MIN = -LONGITUDE_MAX;

    private static final double LATITUDE_MAX = 90;
    private static final double LATITUDE_MIN = -LATITUDE_MAX;

    /**
     * The latitude coordinate of this {@link MutableGeoPoint}. Unit: [degree (angle)].
     */
    public double latitude;

    /**
     * The longitude coordinate of this {@link MutableGeoPoint}.Unit: [degree (angle)].
     */
    public double longitude;

    /**
     * The altitude coordinate of this {@link MutableGeoPoint}. Unit: meter.
     */
    public double altitude;

    /**
     * Creates a GeoLocation object with (0, 0) coordinates.
     */
    public MutableGeoPoint() {
        this(0, 0);
    }

    public MutableGeoPoint(GeoPoint other) {
        this(other.getLatitude(), other.getLongitude(), other.getAltitude());
    }

    /**
     * Creates a GeoLocation object with the specified latitude and longitude.
     *
     * @param lat Latitude in degrees
     * @param lon Longitude in degrees
     */
    public MutableGeoPoint(double lat, double lon) {
        this(lat, lon, 0.0);
    }

    /**
     * Creates a GeoLocation object with the specified latitude and longitude.
     *
     * @param lat Latitude in degrees
     * @param lon Longitude in degrees
     * @param alt Altitude in meters above sea level
     */
    public MutableGeoPoint(double lat, double lon, double alt) {
        set(lat, lon, alt);
    }

    public MutableGeoPoint set(double latitude, double longitude, double altitude) {
        this.latitude = validateLatitude(latitude);
        this.longitude = validateLongitude(longitude);
        this.altitude = altitude;
        return this;
    }

    public MutableGeoPoint set(GeoPoint other) {
        set(other.getLatitude(), other.getLongitude(), other.getAltitude());
        return this;
    }

    private static double validateLatitude(double latitude) {
        if (Double.isNaN(latitude) || latitude < LATITUDE_MIN || latitude > LATITUDE_MAX) {
            throw new IllegalArgumentException("Invalid latitude: " + latitude);
        }
        return latitude;
    }

    private static double validateLongitude(double longitude) {
        if (Double.isNaN(longitude) || longitude < LONGITUDE_MIN || longitude > LONGITUDE_MAX) {
            throw new IllegalArgumentException("Invalid longitude: " + longitude);
        }
        return longitude;
    }

    /**
     * Returns the longitude coordinate of this {@link MutableGeoPoint}. Unit: [degree (angle)].
     *
     * @return the longitude coordinate of this {@link MutableGeoPoint}. Unit: [degree (angle)].
     */
    @Override
    public double getLongitude() {
        return longitude;
    }

    /**
     * Returns the latitude coordinate of this {@link MutableGeoPoint}. Unit: [degree (angle)].
     *
     * @return the latitude coordinate of this {@link MutableGeoPoint}. Unit: [degree (angle)].
     */
    @Override
    public double getLatitude() {
        return latitude;
    }

    /**
     * Returns the altitude coordinate of this {@link MutableGeoPoint}. Unit: meter.
     *
     * @return the altitude coordinate of this {@link MutableGeoPoint}. Unit: meter.
     */
    @Override
    public double getAltitude() {
        return altitude;
    }

    @Override
    public double distanceTo(GeoPoint pointOnLine) {
        return GeoUtils.distanceBetween(this, pointOnLine);
    }

    @Override
    public Vector3d toVector3d(Vector3d result) {
        return GeoProjection.getInstance().geographicToVector(this, result);
    }

    @Override
    public CartesianPoint toCartesian() {
        return GeoProjection.getInstance().geographicToCartesian(this);
    }

    public MutableCartesianPoint toCartesian(MutableCartesianPoint result) {
        return GeoProjection.getInstance().geographicToCartesian(this, result);
    }

    @Override
    public UtmPoint toUtm() {
        return GeoProjection.getInstance().geographicToUtm(this);
    }

    public MutableUtmPoint toUtm(MutableUtmPoint result) {
        return GeoProjection.getInstance().geographicToUtm(this, result);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MutableGeoPoint that = (MutableGeoPoint) o;
        return Double.compare(that.latitude, latitude) == 0
                && Double.compare(that.longitude, longitude) == 0
                && Double.compare(that.altitude, altitude) == 0;
    }

    @Override
    public int hashCode() {
        long longHash = Double.doubleToLongBits(latitude);
        longHash = 31 * longHash + Double.doubleToLongBits(longitude);
        longHash = 31 * longHash + Double.doubleToLongBits(altitude);
        return (int) (longHash ^ (longHash >>> 32));
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "GeoPoint{lat=%.6f,lon=%.6f,alt=%.2f}", this.latitude, this.longitude, this.altitude);
    }
}
