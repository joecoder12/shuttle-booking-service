# Shuttle Booking Service

[![CI](https://github.com/joecoder12/shuttle-booking-service/actions/workflows/ci.yml/badge.svg)](https://github.com/joecoder12/shuttle-booking-service/actions/workflows/ci.yml)

A Spring Boot REST API for employee transport: employees book seats on scheduled office shuttle
trips, and each trip gets an ordered pickup route for the driver.

The two parts that make it more than CRUD:

- **Seats can't be overbooked, even under concurrent load.** Booking a seat takes a row-level
  write lock on the trip, so simultaneous requests for the last seats queue up instead of racing.
  A concurrency test fires 20 simultaneous bookings at a 5-seat trip and asserts exactly 5 succeed.
- **Pickup route planning.** For each trip the service orders the pickups into a short route from
  the depot to the office, using a nearest-neighbour heuristic refined by 2-opt local search.

## Tech stack

Java 21 · Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator) · Hibernate · H2 ·
springdoc-openapi (Swagger UI) · JUnit 5 · Mockito · MockMvc · JaCoCo · Maven · GitHub Actions

## Running it

Requires JDK 21 or newer. The Maven wrapper downloads Maven for you.

```bash
./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs
- Health check: http://localhost:8080/actuator/health

The database is in-memory H2, so there is nothing to set up (and data resets on restart).

### Try it

```bash
# A 2-seat shuttle, three employees, and a trip departing tomorrow
curl -s -X POST localhost:8080/api/shuttles -H 'Content-Type: application/json' \
  -d '{"registrationNumber": "KA01AB1234", "capacity": 2}'
for name in Asha Ravi Meera; do
  curl -s -X POST localhost:8080/api/employees -H 'Content-Type: application/json' \
    -d "{\"name\": \"$name\", \"email\": \"$name@example.com\"}"
done
curl -s -X POST localhost:8080/api/trips -H 'Content-Type: application/json' -d '{
  "shuttleId": 1, "departureTime": "2030-01-15T03:00:00Z",
  "origin":      {"latitude": 12.9121, "longitude": 77.6446},
  "destination": {"latitude": 12.9352, "longitude": 77.6245}}'

# Two bookings fill the trip; the third gets 409 TRIP_FULL
curl -s -X POST localhost:8080/api/trips/1/bookings -H 'Content-Type: application/json' \
  -d '{"employeeId": 1, "pickup": {"latitude": 12.9279, "longitude": 77.6271}}'
curl -s -X POST localhost:8080/api/trips/1/bookings -H 'Content-Type: application/json' \
  -d '{"employeeId": 2, "pickup": {"latitude": 12.9166, "longitude": 77.6101}}'
curl -s -X POST localhost:8080/api/trips/1/bookings -H 'Content-Type: application/json' \
  -d '{"employeeId": 3, "pickup": {"latitude": 12.9200, "longitude": 77.6300}}'

# Ordered pickup route for the driver
curl -s localhost:8080/api/trips/1/pickup-plan
```

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/employees` | Register an employee (email must be unique) |
| GET | `/api/employees/{employeeId}` | Get an employee |
| POST | `/api/shuttles` | Register a shuttle and its seat capacity |
| GET | `/api/shuttles` | List shuttles |
| POST | `/api/trips` | Schedule a trip for a shuttle (departure must be in the future) |
| GET | `/api/trips?from=&to=` | Scheduled trips departing in a window (default: next 7 days) |
| GET | `/api/trips/{tripId}` | Trip details, including seats booked and available |
| POST | `/api/trips/{tripId}/cancel` | Cancel a trip and all of its bookings |
| POST | `/api/trips/{tripId}/bookings` | Book a seat, with a pickup location |
| GET | `/api/trips/{tripId}/bookings` | Confirmed bookings on a trip |
| GET | `/api/trips/{tripId}/pickup-plan` | Ordered pickup route with per-leg and total distance |
| GET | `/api/bookings/{bookingId}` | Get a booking |
| DELETE | `/api/bookings/{bookingId}` | Cancel a booking and free its seat |

### Errors

Errors use the RFC 9457 `application/problem+json` format, with a machine-readable `code` that
clients can branch on:

```json
{"status": 409, "title": "Conflict", "code": "TRIP_FULL",
 "detail": "Trip 1 has no seats left", "instance": "/api/trips/1/bookings"}
```

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean Validation failed; an `errors` map names each bad field |
| 400 | `INVALID_REQUEST` | A business rule failed, e.g. a departure time in the past |
| 404 | `NOT_FOUND` | The employee, shuttle, trip or booking doesn't exist |
| 409 | `TRIP_FULL` | No seats left |
| 409 | `ALREADY_BOOKED` | The employee already has a seat on this trip |
| 409 | `BOOKING_CLOSED` | Past the booking cutoff (30 minutes before departure by default) |
| 409 | `TRIP_NOT_SCHEDULED` | The trip was cancelled |
| 409 | `TRIP_DEPARTED` | Tried to cancel a booking after the trip left |
| 409 | `EMAIL_TAKEN`, `SHUTTLE_EXISTS` | Duplicate employee email or shuttle registration |

## Design notes

### Concurrency-safe seat allocation

Booking a seat is a check-then-act sequence: *is the trip scheduled, is there a free seat, does this
employee already have one?* Then write. If two requests run those checks at the same moment, both
can pass and the trip ends up overbooked.

`BookingService.book` starts by loading the trip with `SELECT ... FOR UPDATE`
(`TripRepository.findByIdForUpdate`, a `PESSIMISTIC_WRITE` lock). Any other transaction that wants
the same trip waits until this one commits, so the checks and the write are atomic per trip.
Bookings on different trips never block each other.

- **Why pessimistic locking and not optimistic (`@Version`):** a popular trip is exactly the place
  where many requests collide. With optimistic locking most of them would fail and have to retry;
  a row lock simply queues them.
- **No deadlocks:** every write that touches a trip's seats or bookings (book, cancel booking,
  cancel trip) takes that one trip lock *first*, so locks are always acquired in the same order.
  Cancelling a booking reads only its trip id before locking, then loads the booking under the lock,
  so two simultaneous cancellations can't both release a seat.
- **Proof:** `BookingConcurrencyTest` releases 20 threads at once against a 5-seat trip and asserts
  exactly 5 bookings and 15 `TRIP_FULL` rejections. It also sends 10 simultaneous requests from one
  employee and asserts exactly one seat. With the `@Lock` annotation removed, the first test fails:
  all 20 requests get a seat.

### Pickup route planning

`RoutePlanner` must visit every pickup once, starting at the trip's origin and ending at its
destination. That's a fixed-endpoint travelling salesman problem, so finding the exact best route
takes exponential time. Instead it uses two classic heuristics:

1. **Nearest neighbour**, O(n²): from the current stop, drive to the closest unvisited pickup.
2. **2-opt**, O(n²) per pass: if reversing a stretch of the route makes it shorter, reverse it, and
   repeat until no such stretch remains. This removes the crossing, backtracking legs that the
   greedy step tends to leave behind.

Distances are great-circle (haversine) distances, precomputed into an (n+2)×(n+2) matrix. The tests
check that:
- every pickup is visited exactly once;
- 2-opt never makes a route longer, and leaves no improving move;
- on an instance where the greedy route is about 24.4 km, 2-opt finds the brute-force optimum of
  about 18.8 km.

### Other decisions

- **Layered design:** controller → service → repository. Request and response bodies are Java
  records, separate from the JPA entities, so API changes and schema changes don't leak into each
  other.
- **Testable time:** a `java.time.Clock` bean is injected wherever "now" matters, so the booking
  cutoff and departure rules are unit-tested with a fixed clock. The cutoff is configurable
  (`shuttle.booking.cutoff`).
- **Seat count on the trip row:** `seatsBooked` is stored on the trip and only changes under the
  trip lock, so availability is a single-row read instead of a count query.
- **No N+1 queries:** listing trips and bookings fetches the related shuttle or employee in the same
  query via `@EntityGraph`, and `spring.jpa.open-in-view` is disabled.
- **Capacity snapshot:** a trip copies its shuttle's capacity when it is created, so later fleet
  changes can't invalidate seats that are already booked.

## Tests

```bash
./mvnw verify
```

29 tests:

- **Unit tests** (JUnit 5, Mockito): booking rules with a fixed clock, the trip seat invariants,
  the haversine distance and the route planner.
- **Integration tests** (`@SpringBootTest` + MockMvc): the full HTTP lifecycle. They cover booking
  until full, cancelling to free a seat, pickup plans, trip cancellation, validation errors and
  error status codes.
- **Concurrency tests:** real threads against the real database, described above.

JaCoCo writes a coverage report to `target/site/jacoco/index.html`. GitHub Actions runs the full
build on every push and pull request.

## Project layout

```
src/main/java/io/github/joecoder12/shuttle
├── api/            REST controllers, the global exception handler, and request/response DTO records
├── config/         Clock bean, booking properties, OpenAPI metadata
├── domain/         JPA entities: Employee, Shuttle, Trip, Booking, plus the GeoPoint embeddable
├── error/          Exceptions that map to 400 / 404 / 409 responses
├── repository/     Spring Data JPA repositories (including the locking query)
├── routing/        Haversine distance and the nearest-neighbour + 2-opt route planner
└── service/        Business logic and transaction boundaries
```

## Limitations and next steps

- H2 in-memory storage keeps setup to zero. The next step is a PostgreSQL profile, with integration
  tests running against real PostgreSQL via Testcontainers.
- There is no authentication yet; Spring Security with role-based access would separate employees
  from transport admins.
- 2-opt gives a good route quickly but not a guaranteed-optimal one. Real road distances would come
  from a routing engine instead of straight-line distance.

## License

MIT
