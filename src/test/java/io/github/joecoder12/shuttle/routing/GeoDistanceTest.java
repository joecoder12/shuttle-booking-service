package io.github.joecoder12.shuttle.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import io.github.joecoder12.shuttle.domain.GeoPoint;

class GeoDistanceTest {

    @Test
    void oneDegreeOfLongitudeAtTheEquatorIsAbout111Km() {
        double km = GeoDistance.haversineKm(new GeoPoint(0, 0), new GeoPoint(0, 1));

        assertThat(km).isCloseTo(2 * Math.PI * GeoDistance.EARTH_RADIUS_KM / 360, within(1e-6));
    }

    @Test
    void distanceIsZeroForTheSamePointAndSymmetric() {
        GeoPoint office = new GeoPoint(12.9352, 77.6245);
        GeoPoint home = new GeoPoint(12.9716, 77.5946);

        assertThat(GeoDistance.haversineKm(office, office)).isZero();
        assertThat(GeoDistance.haversineKm(office, home)).isEqualTo(GeoDistance.haversineKm(home, office));
    }
}
