package org.eclipse.mosaic.app.taxi.util;

public record DistanceBetweenStops(double distanceSeconds, double distanceMeters) {

	public int getDistanceInMinutes() {
		return (int) Math.ceil(distanceSeconds / 60);
	}
}
