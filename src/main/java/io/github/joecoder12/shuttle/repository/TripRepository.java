package io.github.joecoder12.shuttle.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.joecoder12.shuttle.domain.Trip;
import io.github.joecoder12.shuttle.domain.TripStatus;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /**
     * Loads a trip with {@code SELECT ... FOR UPDATE}. Every write that changes a trip's seats or its
     * bookings goes through this first, so concurrent requests for the same trip queue up one at a time
     * (no overbooking), while requests for different trips never block each other. Taking this single
     * lock first, in every code path, also rules out lock-ordering deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") Long id);

    /** Fetches each trip's shuttle in the same query, avoiding one extra query per trip (N+1). */
    @EntityGraph(attributePaths = "shuttle")
    List<Trip> findByStatusAndDepartureTimeBetweenOrderByDepartureTimeAsc(TripStatus status, Instant from, Instant to);
}
