package io.github.joecoder12.shuttle.api.dto;

import java.time.Instant;

import io.github.joecoder12.shuttle.domain.Booking;
import io.github.joecoder12.shuttle.domain.BookingStatus;

public record BookingResponse(
        Long id,
        Long tripId,
        Long employeeId,
        String employeeName,
        LocationDto pickup,
        BookingStatus status,
        Instant createdAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getTrip().getId(),
                booking.getEmployee().getId(),
                booking.getEmployee().getName(),
                LocationDto.from(booking.getPickup()),
                booking.getStatus(),
                booking.getCreatedAt());
    }
}
