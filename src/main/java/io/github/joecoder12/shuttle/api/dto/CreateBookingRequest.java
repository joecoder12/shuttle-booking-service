package io.github.joecoder12.shuttle.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull Long employeeId,
        @NotNull @Valid LocationDto pickup) {
}
