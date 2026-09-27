package io.github.joecoder12.shuttle.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.joecoder12.shuttle.api.dto.BookingResponse;
import io.github.joecoder12.shuttle.api.dto.CreateBookingRequest;
import io.github.joecoder12.shuttle.api.dto.CreateTripRequest;
import io.github.joecoder12.shuttle.api.dto.PickupPlanResponse;
import io.github.joecoder12.shuttle.api.dto.TripResponse;
import io.github.joecoder12.shuttle.service.BookingService;
import io.github.joecoder12.shuttle.service.PickupPlanService;
import io.github.joecoder12.shuttle.service.TripService;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;
    private final BookingService bookingService;
    private final PickupPlanService pickupPlanService;

    public TripController(TripService tripService, BookingService bookingService,
                          PickupPlanService pickupPlanService) {
        this.tripService = tripService;
        this.bookingService = bookingService;
        this.pickupPlanService = pickupPlanService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> create(@Valid @RequestBody CreateTripRequest request) {
        TripResponse trip = tripService.create(request);
        return ResponseEntity.created(URI.create("/api/trips/" + trip.id())).body(trip);
    }

    @GetMapping
    public List<TripResponse> list(@RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to) {
        return tripService.listScheduled(from, to);
    }

    @GetMapping("/{tripId}")
    public TripResponse get(@PathVariable Long tripId) {
        return tripService.get(tripId);
    }

    @PostMapping("/{tripId}/cancel")
    public TripResponse cancel(@PathVariable Long tripId) {
        return tripService.cancel(tripId);
    }

    @PostMapping("/{tripId}/bookings")
    public ResponseEntity<BookingResponse> book(@PathVariable Long tripId,
                                                @Valid @RequestBody CreateBookingRequest request) {
        BookingResponse booking = bookingService.book(tripId, request);
        return ResponseEntity.created(URI.create("/api/bookings/" + booking.id())).body(booking);
    }

    @GetMapping("/{tripId}/bookings")
    public List<BookingResponse> bookings(@PathVariable Long tripId) {
        return tripService.confirmedBookings(tripId);
    }

    @GetMapping("/{tripId}/pickup-plan")
    public PickupPlanResponse pickupPlan(@PathVariable Long tripId) {
        return pickupPlanService.plan(tripId);
    }
}
