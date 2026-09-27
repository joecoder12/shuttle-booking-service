package io.github.joecoder12.shuttle.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.joecoder12.shuttle.api.dto.CreateBookingRequest;
import io.github.joecoder12.shuttle.api.dto.LocationDto;
import io.github.joecoder12.shuttle.domain.BookingStatus;
import io.github.joecoder12.shuttle.domain.Employee;
import io.github.joecoder12.shuttle.domain.GeoPoint;
import io.github.joecoder12.shuttle.domain.Shuttle;
import io.github.joecoder12.shuttle.domain.Trip;
import io.github.joecoder12.shuttle.error.ConflictException;
import io.github.joecoder12.shuttle.repository.BookingRepository;
import io.github.joecoder12.shuttle.repository.EmployeeRepository;
import io.github.joecoder12.shuttle.repository.ShuttleRepository;
import io.github.joecoder12.shuttle.repository.TripRepository;

/**
 * Fires many booking requests at the same trip at the same instant, against the real database and
 * transaction manager, to prove the trip row lock prevents overbooking and double booking.
 */
@SpringBootTest
class BookingConcurrencyTest {

    private static final LocationDto PICKUP = new LocationDto(12.97, 77.59);

    @Autowired
    private BookingService bookingService;
    @Autowired
    private ShuttleRepository shuttles;
    @Autowired
    private TripRepository trips;
    @Autowired
    private EmployeeRepository employees;
    @Autowired
    private BookingRepository bookings;

    @Test
    void twentyEmployeesRacingForFiveSeatsFillTheTripExactly() throws Exception {
        Trip trip = newTrip(5);
        List<Employee> staff = IntStream.range(0, 20).mapToObj(i -> newEmployee()).toList();

        RaceResult result = race(staff.stream()
                .map(employee -> (Callable<?>) () ->
                        bookingService.book(trip.getId(), new CreateBookingRequest(employee.getId(), PICKUP)))
                .toList());

        assertThat(result.successes()).isEqualTo(5);
        assertThat(result.conflictCodes()).hasSize(15).containsOnly("TRIP_FULL");
        assertThat(trips.findById(trip.getId()).orElseThrow().getSeatsBooked()).isEqualTo(5);
        assertThat(bookings.countByTripIdAndStatus(trip.getId(), BookingStatus.CONFIRMED)).isEqualTo(5);
    }

    @Test
    void oneEmployeeSubmittingTenTimesAtOnceGetsExactlyOneSeat() throws Exception {
        Trip trip = newTrip(10);
        Employee employee = newEmployee();
        CreateBookingRequest request = new CreateBookingRequest(employee.getId(), PICKUP);

        RaceResult result = race(IntStream.range(0, 10)
                .mapToObj(i -> (Callable<?>) () -> bookingService.book(trip.getId(), request))
                .toList());

        assertThat(result.successes()).isEqualTo(1);
        assertThat(result.conflictCodes()).hasSize(9).containsOnly("ALREADY_BOOKED");
        assertThat(trips.findById(trip.getId()).orElseThrow().getSeatsBooked()).isEqualTo(1);
    }

    private Trip newTrip(int capacity) {
        Shuttle shuttle = shuttles.save(new Shuttle("RACE-" + UUID.randomUUID().toString().substring(0, 8), capacity));
        return trips.save(new Trip(shuttle, Instant.now().plus(Duration.ofDays(1)),
                new GeoPoint(12.90, 77.60), new GeoPoint(12.99, 77.60)));
    }

    private Employee newEmployee() {
        return employees.save(new Employee("Racer", "racer-" + UUID.randomUUID() + "@example.com"));
    }

    /** Runs every task on its own thread, releasing them all at once through a start gate. */
    private static RaceResult race(List<? extends Callable<?>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();

            int successes = 0;
            List<String> conflictCodes = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get();
                    successes++;
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof ConflictException conflict)) {
                        throw new AssertionError("Unexpected failure", e.getCause());
                    }
                    conflictCodes.add(conflict.getCode());
                }
            }
            return new RaceResult(successes, conflictCodes);
        } finally {
            pool.shutdownNow();
        }
    }

    private record RaceResult(int successes, List<String> conflictCodes) {
    }
}
