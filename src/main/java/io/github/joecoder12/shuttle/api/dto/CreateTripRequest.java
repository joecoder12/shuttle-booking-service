package io.github.joecoder12.shuttle.api.dto;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateTripRequest(
        @NotNull Long shuttleId,
        @NotNull Instant departureTime,
        @NotNull @Valid LocationDto origin,
        @NotNull @Valid LocationDto destination) {
}
