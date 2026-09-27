package io.github.joecoder12.shuttle.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.joecoder12.shuttle.api.dto.CreateShuttleRequest;
import io.github.joecoder12.shuttle.api.dto.ShuttleResponse;
import io.github.joecoder12.shuttle.domain.Shuttle;
import io.github.joecoder12.shuttle.error.ConflictException;
import io.github.joecoder12.shuttle.repository.ShuttleRepository;

@Service
public class ShuttleService {

    private final ShuttleRepository shuttles;

    public ShuttleService(ShuttleRepository shuttles) {
        this.shuttles = shuttles;
    }

    @Transactional
    public ShuttleResponse create(CreateShuttleRequest request) {
        String registration = request.registrationNumber().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (shuttles.existsByRegistrationNumber(registration)) {
            throw new ConflictException("SHUTTLE_EXISTS", "Shuttle " + registration + " is already registered");
        }
        return ShuttleResponse.from(shuttles.save(new Shuttle(registration, request.capacity())));
    }

    @Transactional(readOnly = true)
    public List<ShuttleResponse> list() {
        return shuttles.findAll(Sort.by("registrationNumber")).stream().map(ShuttleResponse::from).toList();
    }
}
