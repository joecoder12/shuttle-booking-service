package io.github.joecoder12.shuttle.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.joecoder12.shuttle.domain.Shuttle;

public interface ShuttleRepository extends JpaRepository<Shuttle, Long> {

    boolean existsByRegistrationNumber(String registrationNumber);
}
