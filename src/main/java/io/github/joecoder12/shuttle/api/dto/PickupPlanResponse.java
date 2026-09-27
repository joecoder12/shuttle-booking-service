package io.github.joecoder12.shuttle.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record PickupPlanResponse(Long tripId, int pickups, double totalDistanceKm, List<Stop> stops) {

    public enum StopType {
        ORIGIN,
        PICKUP,
        DESTINATION
    }

    /**
     * @param bookingId    set only for {@link StopType#PICKUP} stops
     * @param employeeName set only for {@link StopType#PICKUP} stops
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Stop(
            int sequence,
            StopType type,
            Long bookingId,
            String employeeName,
            LocationDto location,
            double distanceFromPreviousKm) {
    }
}
