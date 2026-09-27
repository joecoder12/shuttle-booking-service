package io.github.joecoder12.shuttle.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.joecoder12.shuttle.api.dto.BookingResponse;
import io.github.joecoder12.shuttle.api.dto.CreateBookingRequest;
import io.github.joecoder12.shuttle.config.BookingProperties;
import io.github.joecoder12.shuttle.domain.Booking;
import io.github.joecoder12.shuttle.domain.BookingStatus;
import io.github.joecoder12.shuttle.domain.Employee;
import io.github.joecoder12.shuttle.domain.Trip;
import io.github.joecoder12.shuttle.error.ConflictException;
import io.github.joecoder12.shuttle.error.ResourceNotFoundException;
import io.github.joecoder12.shuttle.repository.BookingRepository;
import io.github.joecoder12.shuttle.repository.EmployeeRepository;
import io.github.joecoder12.shuttle.repository.TripRepository;

@Service
public class BookingService {

    private final TripRepository trips;
    private final EmployeeRepository employees;
    private final BookingRepository bookings;
    private final Clock clock;
    private final BookingProperties properties;

    public BookingService(TripRepository trips, EmployeeRepository employees, BookingRepository bookings,
                          Clock clock, BookingProperties properties) {
        this.trips = trips;
        this.employees = employees;
        this.bookings = bookings;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Books one seat. The trip row is locked for the whole transaction, so the "is there a seat?" and
     * "is this employee already booked?" checks can't be invalidated by a concurrent booking before
     * this one commits.
     */
    @Transactional
    public BookingResponse book(Long tripId, CreateBookingRequest request) {
        Trip trip = trips.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        if (!trip.isScheduled()) {
            throw new ConflictException("TRIP_NOT_SCHEDULED", "Trip " + tripId + " is " + trip.getStatus());
        }
        Instant closesAt = trip.getDepartureTime().minus(properties.cutoff());
        if (!clock.instant().isBefore(closesAt)) {
            throw new ConflictException("BOOKING_CLOSED", "Bookings for trip " + tripId + " closed at " + closesAt);
        }

        Employee employee = employees.findById(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", request.employeeId()));
        if (bookings.existsByTripIdAndEmployeeIdAndStatus(tripId, employee.getId(), BookingStatus.CONFIRMED)) {
            throw new ConflictException("ALREADY_BOOKED",
                    "Employee " + employee.getId() + " already has a seat on trip " + tripId);
        }
        if (trip.seatsAvailable() == 0) {
            throw new ConflictException("TRIP_FULL", "Trip " + tripId + " has no seats left");
        }

        trip.reserveSeat();
        Booking booking = bookings.save(new Booking(trip, employee, request.pickup().toGeoPoint(), clock.instant()));
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long bookingId) {
        return bookings.findById(bookingId)
                .map(BookingResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }

    /**
     * Cancels a booking and frees its seat. Takes the trip lock before reading the booking, so two
     * concurrent cancellations of the same booking can't both release a seat. Cancelling twice is a no-op.
     */
    @Transactional
    public void cancel(Long bookingId) {
        Long tripId = bookings.findTripIdByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        Trip trip = trips.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (!booking.isConfirmed()) {
            return;
        }
        if (!clock.instant().isBefore(trip.getDepartureTime())) {
            throw new ConflictException("TRIP_DEPARTED", "Trip " + tripId + " has already departed");
        }
        booking.cancel();
        trip.releaseSeat();
    }
}
