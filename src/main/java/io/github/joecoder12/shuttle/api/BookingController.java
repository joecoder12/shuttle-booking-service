package io.github.joecoder12.shuttle.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.joecoder12.shuttle.api.dto.BookingResponse;
import io.github.joecoder12.shuttle.service.BookingService;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/{bookingId}")
    public BookingResponse get(@PathVariable Long bookingId) {
        return bookingService.get(bookingId);
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Void> cancel(@PathVariable Long bookingId) {
        bookingService.cancel(bookingId);
        return ResponseEntity.noContent().build();
    }
}
