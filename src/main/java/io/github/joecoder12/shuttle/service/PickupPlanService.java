package io.github.joecoder12.shuttle.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.joecoder12.shuttle.api.dto.LocationDto;
import io.github.joecoder12.shuttle.api.dto.PickupPlanResponse;
import io.github.joecoder12.shuttle.api.dto.PickupPlanResponse.Stop;
import io.github.joecoder12.shuttle.api.dto.PickupPlanResponse.StopType;
import io.github.joecoder12.shuttle.domain.Booking;
import io.github.joecoder12.shuttle.domain.BookingStatus;
import io.github.joecoder12.shuttle.domain.GeoPoint;
import io.github.joecoder12.shuttle.domain.Trip;
import io.github.joecoder12.shuttle.error.ResourceNotFoundException;
import io.github.joecoder12.shuttle.repository.BookingRepository;
import io.github.joecoder12.shuttle.repository.TripRepository;
import io.github.joecoder12.shuttle.routing.GeoDistance;
import io.github.joecoder12.shuttle.routing.RoutePlanner;

@Service
public class PickupPlanService {

    private final TripRepository trips;
    private final BookingRepository bookings;

    public PickupPlanService(TripRepository trips, BookingRepository bookings) {
        this.trips = trips;
        this.bookings = bookings;
    }

    /** Orders the trip's confirmed pickups into a short route from origin to destination. */
    @Transactional(readOnly = true)
    public PickupPlanResponse plan(Long tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        List<Booking> confirmed = bookings.findByTripIdAndStatusOrderByCreatedAtAsc(tripId, BookingStatus.CONFIRMED);
        List<GeoPoint> pickups = confirmed.stream().map(Booking::getPickup).toList();

        int[] order = RoutePlanner.plan(trip.getOrigin(), pickups, trip.getDestination());

        List<Stop> stops = new ArrayList<>(order.length + 2);
        stops.add(new Stop(0, StopType.ORIGIN, null, null, LocationDto.from(trip.getOrigin()), 0));
        GeoPoint previous = trip.getOrigin();
        double total = 0;
        for (int index : order) {
            Booking booking = confirmed.get(index);
            double leg = GeoDistance.haversineKm(previous, booking.getPickup());
            total += leg;
            stops.add(new Stop(stops.size(), StopType.PICKUP, booking.getId(), booking.getEmployee().getName(),
                    LocationDto.from(booking.getPickup()), round(leg)));
            previous = booking.getPickup();
        }
        double lastLeg = GeoDistance.haversineKm(previous, trip.getDestination());
        total += lastLeg;
        stops.add(new Stop(stops.size(), StopType.DESTINATION, null, null,
                LocationDto.from(trip.getDestination()), round(lastLeg)));

        return new PickupPlanResponse(tripId, order.length, round(total), stops);
    }

    private static double round(double km) {
        return Math.round(km * 100) / 100.0;
    }
}
