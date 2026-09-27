package io.github.joecoder12.shuttle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TripTest {

    private final Trip trip = new Trip(new Shuttle("KA01AB1234", 2), Instant.parse("2026-10-01T03:00:00Z"),
            new GeoPoint(12.90, 77.60), new GeoPoint(12.99, 77.60));

    @Test
    void newTripIsScheduledWithTheShuttlesCapacity() {
        assertThat(trip.isScheduled()).isTrue();
        assertThat(trip.getCapacity()).isEqualTo(2);
        assertThat(trip.seatsAvailable()).isEqualTo(2);
    }

    @Test
    void reservingPastCapacityIsRejected() {
        trip.reserveSeat();
        trip.reserveSeat();

        assertThat(trip.seatsAvailable()).isZero();
        assertThatThrownBy(trip::reserveSeat).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releasingWithNothingBookedIsRejected() {
        assertThatThrownBy(trip::releaseSeat).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellingFreesEverySeat() {
        trip.reserveSeat();

        trip.cancel();

        assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
        assertThat(trip.getSeatsBooked()).isZero();
    }
}
