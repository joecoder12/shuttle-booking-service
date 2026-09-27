package io.github.joecoder12.shuttle.api.dto;

import java.time.Instant;

import io.github.joecoder12.shuttle.domain.Trip;
import io.github.joecoder12.shuttle.domain.TripStatus;

public record TripResponse(
        Long id,
        Long shuttleId,
        String shuttleRegistration,
        Instant departureTime,
        LocationDto origin,
        LocationDto destination,
        int capacity,
        int seatsBooked,
        int seatsAvailable,
        TripStatus status) {

    public static TripResponse from(Trip trip) {
        return new TripResponse(
                trip.getId(),
                trip.getShuttle().getId(),
                trip.getShuttle().getRegistrationNumber(),
                trip.getDepartureTime(),
                LocationDto.from(trip.getOrigin()),
                LocationDto.from(trip.getDestination()),
                trip.getCapacity(),
                trip.getSeatsBooked(),
                trip.seatsAvailable(),
                trip.getStatus());
    }
}
