package io.github.joecoder12.shuttle.api.dto;

import io.github.joecoder12.shuttle.domain.Shuttle;

public record ShuttleResponse(Long id, String registrationNumber, int capacity) {

    public static ShuttleResponse from(Shuttle shuttle) {
        return new ShuttleResponse(shuttle.getId(), shuttle.getRegistrationNumber(), shuttle.getCapacity());
    }
}
