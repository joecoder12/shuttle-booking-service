package io.github.joecoder12.shuttle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.joecoder12.shuttle.api.dto.BookingResponse;
import io.github.joecoder12.shuttle.api.dto.CreateBookingRequest;
import io.github.joecoder12.shuttle.api.dto.LocationDto;
import io.github.joecoder12.shuttle.config.BookingProperties;
import io.github.joecoder12.shuttle.domain.Booking;
import io.github.joecoder12.shuttle.domain.BookingStatus;
import io.github.joecoder12.shuttle.domain.Employee;
import io.github.joecoder12.shuttle.domain.GeoPoint;
import io.github.joecoder12.shuttle.domain.Shuttle;
import io.github.joecoder12.shuttle.domain.Trip;
import io.github.joecoder12.shuttle.error.ConflictException;
import io.github.joecoder12.shuttle.error.ResourceNotFoundException;
import io.github.joecoder12.shuttle.repository.BookingRepository;
import io.github.joecoder12.shuttle.repository.EmployeeRepository;
import io.github.joecoder12.shuttle.repository.TripRepository;

class BookingServiceTest {

    private static final long TRIP_ID = 1L;
    private static final long EMPLOYEE_ID = 7L;
    private static final long BOOKING_ID = 42L;
    private static final Instant DEPARTURE = Instant.parse("2026-10-01T03:00:00Z");
    private static final Duration CUTOFF = Duration.ofMinutes(30);
    private static final LocationDto PICKUP = new LocationDto(12.97, 77.59);

    private final TripRepository trips = mock(TripRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);

    private final Employee employee = withId(new Employee("Asha Rao", "asha@example.com"), EMPLOYEE_ID);

    @BeforeEach
    void stubSaveToReturnItsArgument() {
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
    }

    @Test
    void booksASeatWhenOneIsAvailable() {
        Trip trip = givenTrip(2);

        BookingResponse booking = serviceAt(DEPARTURE.minus(Duration.ofHours(2))).book(TRIP_ID, request());

        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.employeeName()).isEqualTo("Asha Rao");
        assertThat(trip.getSeatsBooked()).isEqualTo(1);
    }

    @Test
    void acceptsBookingsUntilTheCutoff() {
        givenTrip(2);

        BookingResponse booking = serviceAt(DEPARTURE.minus(CUTOFF).minusSeconds(1)).book(TRIP_ID, request());

        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void rejectsBookingsOnceTheCutoffHasPassed() {
        givenTrip(2);

        assertConflict(() -> serviceAt(DEPARTURE.minus(CUTOFF)).book(TRIP_ID, request()), "BOOKING_CLOSED");
    }

    @Test
    void rejectsBookingsOnACancelledTrip() {
        givenTrip(2).cancel();

        assertConflict(() -> serviceAt(DEPARTURE.minus(Duration.ofHours(2))).book(TRIP_ID, request()),
                "TRIP_NOT_SCHEDULED");
    }

    @Test
    void rejectsASecondSeatForTheSameEmployee() {
        givenTrip(2);
        when(bookings.existsByTripIdAndEmployeeIdAndStatus(TRIP_ID, EMPLOYEE_ID, BookingStatus.CONFIRMED))
                .thenReturn(true);

        assertConflict(() -> serviceAt(DEPARTURE.minus(Duration.ofHours(2))).book(TRIP_ID, request()),
                "ALREADY_BOOKED");
    }

    @Test
    void rejectsBookingsOnAFullTrip() {
        givenTrip(1).reserveSeat();

        assertConflict(() -> serviceAt(DEPARTURE.minus(Duration.ofHours(2))).book(TRIP_ID, request()), "TRIP_FULL");
    }

    @Test
    void rejectsUnknownTripsAndEmployees() {
        BookingService service = serviceAt(DEPARTURE.minus(Duration.ofHours(2)));
        when(trips.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.book(TRIP_ID, request())).isInstanceOf(ResourceNotFoundException.class);

        givenTrip(2);
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.book(TRIP_ID, request())).isInstanceOf(ResourceNotFoundException.class);
        verify(bookings, never()).save(any());
    }

    @Test
    void cancellingABookingReleasesItsSeat() {
        Trip trip = givenTrip(2);
        Booking booking = givenBooking(trip);
        trip.reserveSeat();

        serviceAt(DEPARTURE.minus(Duration.ofHours(2))).cancel(BOOKING_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(trip.getSeatsBooked()).isZero();
    }

    @Test
    void cancellingTwiceOnlyReleasesTheSeatOnce() {
        Trip trip = givenTrip(2);
        givenBooking(trip);
        trip.reserveSeat();
        trip.reserveSeat();
        BookingService service = serviceAt(DEPARTURE.minus(Duration.ofHours(2)));

        service.cancel(BOOKING_ID);
        service.cancel(BOOKING_ID);

        assertThat(trip.getSeatsBooked()).isEqualTo(1);
    }

    @Test
    void cannotCancelAfterTheTripHasDeparted() {
        Trip trip = givenTrip(2);
        givenBooking(trip);
        trip.reserveSeat();

        assertConflict(() -> serviceAt(DEPARTURE.plusSeconds(60)).cancel(BOOKING_ID), "TRIP_DEPARTED");
        assertThat(trip.getSeatsBooked()).isEqualTo(1);
    }

    private BookingService serviceAt(Instant now) {
        return new BookingService(trips, employees, bookings, Clock.fixed(now, ZoneOffset.UTC),
                new BookingProperties(CUTOFF));
    }

    private Trip givenTrip(int capacity) {
        Trip trip = withId(new Trip(new Shuttle("KA01AB1234", capacity), DEPARTURE,
                new GeoPoint(12.90, 77.60), new GeoPoint(12.99, 77.60)), TRIP_ID);
        when(trips.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        return trip;
    }

    private Booking givenBooking(Trip trip) {
        Booking booking = withId(new Booking(trip, employee, PICKUP.toGeoPoint(), DEPARTURE.minus(Duration.ofDays(1))),
                BOOKING_ID);
        when(bookings.findTripIdByBookingId(BOOKING_ID)).thenReturn(Optional.of(TRIP_ID));
        when(bookings.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        return booking;
    }

    private static CreateBookingRequest request() {
        return new CreateBookingRequest(EMPLOYEE_ID, PICKUP);
    }

    private static void assertConflict(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(ConflictException.class).extracting("code").isEqualTo(code);
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
