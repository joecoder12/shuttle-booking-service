package io.github.joecoder12.shuttle.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.joecoder12.shuttle.api.dto.BookingResponse;
import io.github.joecoder12.shuttle.api.dto.CreateTripRequest;
import io.github.joecoder12.shuttle.api.dto.TripResponse;
import io.github.joecoder12.shuttle.domain.Booking;
import io.github.joecoder12.shuttle.domain.BookingStatus;
import io.github.joecoder12.shuttle.domain.Shuttle;
import io.github.joecoder12.shuttle.domain.Trip;
import io.github.joecoder12.shuttle.domain.TripStatus;
import io.github.joecoder12.shuttle.error.InvalidRequestException;
import io.github.joecoder12.shuttle.error.ResourceNotFoundException;
import io.github.joecoder12.shuttle.repository.BookingRepository;
import io.github.joecoder12.shuttle.repository.ShuttleRepository;
import io.github.joecoder12.shuttle.repository.TripRepository;

@Service
public class TripService {

    static final Duration DEFAULT_LISTING_WINDOW = Duration.ofDays(7);

    private final TripRepository trips;
    private final ShuttleRepository shuttles;
    private final BookingRepository bookings;
    private final Clock clock;

    public TripService(TripRepository trips, ShuttleRepository shuttles, BookingRepository bookings, Clock clock) {
        this.trips = trips;
        this.shuttles = shuttles;
        this.bookings = bookings;
        this.clock = clock;
    }

    @Transactional
    public TripResponse create(CreateTripRequest request) {
        Shuttle shuttle = shuttles.findById(request.shuttleId())
                .orElseThrow(() -> new ResourceNotFoundException("Shuttle", request.shuttleId()));
        if (!request.departureTime().isAfter(clock.instant())) {
            throw new InvalidRequestException("departureTime must be in the future");
        }
        Trip trip = new Trip(shuttle, request.departureTime(),
                request.origin().toGeoPoint(), request.destination().toGeoPoint());
        return TripResponse.from(trips.save(trip));
    }

    @Transactional(readOnly = true)
    public TripResponse get(Long tripId) {
        return TripResponse.from(findTrip(tripId));
    }

    /** Scheduled trips departing in [from, to]; defaults to the next seven days. */
    @Transactional(readOnly = true)
    public List<TripResponse> listScheduled(Instant from, Instant to) {
        Instant start = from != null ? from : clock.instant();
        Instant end = to != null ? to : start.plus(DEFAULT_LISTING_WINDOW);
        if (end.isBefore(start)) {
            throw new InvalidRequestException("'to' must not be before 'from'");
        }
        return trips.findByStatusAndDepartureTimeBetweenOrderByDepartureTimeAsc(TripStatus.SCHEDULED, start, end)
                .stream()
                .map(TripResponse::from)
                .toList();
    }

    /** Cancels the trip and every confirmed booking on it. Cancelling twice is a no-op. */
    @Transactional
    public TripResponse cancel(Long tripId) {
        Trip trip = trips.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        if (trip.isScheduled()) {
            bookings.findByTripIdAndStatusOrderByCreatedAtAsc(tripId, BookingStatus.CONFIRMED)
                    .forEach(Booking::cancel);
            trip.cancel();
        }
        return TripResponse.from(trip);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> confirmedBookings(Long tripId) {
        findTrip(tripId);
        return bookings.findByTripIdAndStatusOrderByCreatedAtAsc(tripId, BookingStatus.CONFIRMED)
                .stream()
                .map(BookingResponse::from)
                .toList();
    }

    private Trip findTrip(Long tripId) {
        return trips.findById(tripId).orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
    }
}
