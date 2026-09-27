package io.github.joecoder12.shuttle.routing;

import io.github.joecoder12.shuttle.domain.GeoPoint;

public final class GeoDistance {

    static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistance() {
    }

    /** Great-circle distance between two points, using the haversine formula. */
    public static double haversineKm(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude()))
                * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
