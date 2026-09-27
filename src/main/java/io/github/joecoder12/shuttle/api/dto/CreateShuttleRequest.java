package io.github.joecoder12.shuttle.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateShuttleRequest(
        @NotBlank @Size(max = 20) String registrationNumber,
        @NotNull @Min(1) @Max(60) Integer capacity) {
}
