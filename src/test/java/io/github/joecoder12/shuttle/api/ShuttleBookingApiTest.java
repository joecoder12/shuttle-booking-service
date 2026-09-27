package io.github.joecoder12.shuttle.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** End-to-end tests through the HTTP layer: routing, validation, JSON mapping, persistence and errors. */
@SpringBootTest
@AutoConfigureMockMvc
class ShuttleBookingApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void bookingLifecycleFromCreationToPickupPlan() throws Exception {
        long shuttle = createShuttle(2);
        long trip = createTrip(shuttle);
        long asha = createEmployee("Asha Rao");
        long ravi = createEmployee("Ravi Kumar");
        long meera = createEmployee("Meera Iyer");

        long ashaBooking = book(trip, asha, 12.95, 77.60).andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.employeeName").value("Asha Rao"))
                .andReturn().getResponse().getContentAsString().transform(ShuttleBookingApiTest::id);
        book(trip, ravi, 12.92, 77.60).andExpect(status().isCreated());

        book(trip, meera, 12.93, 77.60).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_FULL"));
        mvc.perform(get("/api/trips/{id}", trip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatsBooked").value(2))
                .andExpect(jsonPath("$.seatsAvailable").value(0));

        mvc.perform(delete("/api/bookings/{id}", ashaBooking)).andExpect(status().isNoContent());
        mvc.perform(get("/api/bookings/{id}", ashaBooking)).andExpect(jsonPath("$.status").value("CANCELLED"));
        book(trip, meera, 12.93, 77.60).andExpect(status().isCreated());

        mvc.perform(get("/api/trips/{id}/bookings", trip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        // Depot at 12.90 and office at 12.99 on the same meridian: Ravi (12.92) must come before Meera (12.93).
        mvc.perform(get("/api/trips/{id}/pickup-plan", trip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickups").value(2))
                .andExpect(jsonPath("$.stops", hasSize(4)))
                .andExpect(jsonPath("$.stops[0].type").value("ORIGIN"))
                .andExpect(jsonPath("$.stops[1].employeeName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.stops[2].employeeName").value("Meera Iyer"))
                .andExpect(jsonPath("$.stops[3].type").value("DESTINATION"))
                .andExpect(jsonPath("$.totalDistanceKm").value(10.01));
    }

    @Test
    void cancellingATripCancelsItsBookingsAndBlocksNewOnes() throws Exception {
        long trip = createTrip(createShuttle(4));
        long employee = createEmployee("Kiran Shah");
        book(trip, employee, 12.95, 77.60).andExpect(status().isCreated());

        mvc.perform(post("/api/trips/{id}/cancel", trip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.seatsBooked").value(0));

        mvc.perform(get("/api/trips/{id}/bookings", trip)).andExpect(jsonPath("$", hasSize(0)));
        book(trip, employee, 12.95, 77.60).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_SCHEDULED"));
    }

    @Test
    void listsOnlyScheduledTripsInTheRequestedWindow() throws Exception {
        long shuttle = createShuttle(4);
        long scheduled = createTrip(shuttle);
        long cancelled = createTrip(shuttle);
        mvc.perform(post("/api/trips/{id}/cancel", cancelled)).andExpect(status().isOk());

        mvc.perform(get("/api/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(scheduled)).exists())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(cancelled)).doesNotExist());
        mvc.perform(get("/api/trips").param("from", "2030-01-02T00:00:00Z").param("to", "2030-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidRequestBodiesReturnFieldLevelErrors() throws Exception {
        mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON).content("""
                        {"departureTime": "2030-01-01T03:00:00Z",
                         "origin": {"latitude": 123.0, "longitude": 77.6}}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.shuttleId").exists())
                .andExpect(jsonPath("$.errors['origin.latitude']").exists())
                .andExpect(jsonPath("$.errors.destination").exists());

        mvc.perform(post("/api/shuttles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationNumber\": \"KA01\", \"capacity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.capacity").exists());

        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void businessRuleViolationsMapToTheRightStatusCodes() throws Exception {
        mvc.perform(get("/api/trips/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Trip 999999 not found"));

        mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON)
                        .content(tripJson(createShuttle(4), Instant.now().minus(Duration.ofHours(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("departureTime must be in the future"));

        String email = unique("dup") + "@example.com";
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(employeeJson("A", email)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("B", email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void readEndpointsReturnWhatWasCreated() throws Exception {
        long employee = createEmployee("Neha Singh");
        mvc.perform(get("/api/employees/{id}", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Neha Singh"));

        mvc.perform(post("/api/shuttles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationNumber\": \"ka 05 mn 4321\", \"capacity\": 12}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationNumber").value("KA05MN4321"));
        mvc.perform(get("/api/shuttles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.registrationNumber == 'KA05MN4321')].capacity").value(12));
        mvc.perform(post("/api/shuttles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationNumber\": \"KA05MN4321\", \"capacity\": 12}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHUTTLE_EXISTS"));
    }

    private long createShuttle(int capacity) throws Exception {
        String json = """
                {"registrationNumber": "%s", "capacity": %d}""".formatted(unique("KA"), capacity);
        return created(post("/api/shuttles"), json);
    }

    private long createEmployee(String name) throws Exception {
        return created(post("/api/employees"), employeeJson(name, unique("emp") + "@example.com"));
    }

    private long createTrip(long shuttleId) throws Exception {
        return created(post("/api/trips"), tripJson(shuttleId, Instant.now().plus(Duration.ofDays(1))));
    }

    private ResultActions book(long tripId, long employeeId, double lat, double lon) throws Exception {
        return mvc.perform(post("/api/trips/{id}/bookings", tripId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"employeeId": %d, "pickup": {"latitude": %s, "longitude": %s}}"""
                        .formatted(employeeId, lat, lon)));
    }

    private long created(MockHttpServletRequestBuilder request, String json) throws Exception {
        return id(mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private static String tripJson(long shuttleId, Instant departure) {
        return """
                {"shuttleId": %d, "departureTime": "%s",
                 "origin": {"latitude": 12.90, "longitude": 77.60},
                 "destination": {"latitude": 12.99, "longitude": 77.60}}""".formatted(shuttleId, departure);
    }

    private static String employeeJson(String name, String email) {
        return """
                {"name": "%s", "email": "%s"}""".formatted(name, email);
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
