package io.github.joecoder12.shuttle.domain;

import java.time.Instant;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One scheduled run of a shuttle. Seat bookkeeping lives on this row, so every change to it is made
 * while holding the row's write lock (see {@code TripRepository#findByIdForUpdate}).
 */
@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shuttle_id")
    private Shuttle shuttle;

    @Column(nullable = false)
    private Instant departureTime;

    @Embedded
    @AttributeOverride(name = "latitude", column = @Column(name = "origin_lat"))
    @AttributeOverride(name = "longitude", column = @Column(name = "origin_lon"))
    private GeoPoint origin;

    @Embedded
    @AttributeOverride(name = "latitude", column = @Column(name = "destination_lat"))
    @AttributeOverride(name = "longitude", column = @Column(name = "destination_lon"))
    private GeoPoint destination;

    /** Copied from the shuttle when the trip is created, so later fleet changes don't affect existing bookings. */
    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int seatsBooked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    protected Trip() {
        // for JPA
    }

    public Trip(Shuttle shuttle, Instant departureTime, GeoPoint origin, GeoPoint destination) {
        this.shuttle = shuttle;
        this.departureTime = departureTime;
        this.origin = origin;
        this.destination = destination;
        this.capacity = shuttle.getCapacity();
        this.seatsBooked = 0;
        this.status = TripStatus.SCHEDULED;
    }

    public boolean isScheduled() {
        return status == TripStatus.SCHEDULED;
    }

    public int seatsAvailable() {
        return capacity - seatsBooked;
    }

    public void reserveSeat() {
        if (seatsAvailable() <= 0) {
            throw new IllegalStateException("Trip " + id + " has no seats left");
        }
        seatsBooked++;
    }

    public void releaseSeat() {
        if (seatsBooked <= 0) {
            throw new IllegalStateException("Trip " + id + " has no booked seats to release");
        }
        seatsBooked--;
    }

    public void cancel() {
        status = TripStatus.CANCELLED;
        seatsBooked = 0;
    }

    public Long getId() {
        return id;
    }

    public Shuttle getShuttle() {
        return shuttle;
    }

    public Instant getDepartureTime() {
        return departureTime;
    }

    public GeoPoint getOrigin() {
        return origin;
    }

    public GeoPoint getDestination() {
        return destination;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getSeatsBooked() {
        return seatsBooked;
    }

    public TripStatus getStatus() {
        return status;
    }
}
