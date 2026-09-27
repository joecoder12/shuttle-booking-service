package io.github.joecoder12.shuttle.domain;

import jakarta.persistence.Embeddable;

/** A latitude/longitude pair, embedded in trips (origin, destination) and bookings (pickup). */
@Embeddable
public record GeoPoint(double latitude, double longitude) {
}
