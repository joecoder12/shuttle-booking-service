package io.github.joecoder12.shuttle.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.joecoder12.shuttle.domain.Booking;
import io.github.joecoder12.shuttle.domain.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByTripIdAndEmployeeIdAndStatus(Long tripId, Long employeeId, BookingStatus status);

    long countByTripIdAndStatus(Long tripId, BookingStatus status);

    @EntityGraph(attributePaths = "employee")
    List<Booking> findByTripIdAndStatusOrderByCreatedAtAsc(Long tripId, BookingStatus status);

    /** Reads only the trip id, so the booking itself is first loaded after the trip lock is held. */
    @Query("select b.trip.id from Booking b where b.id = :bookingId")
    Optional<Long> findTripIdByBookingId(@Param("bookingId") Long bookingId);
}
