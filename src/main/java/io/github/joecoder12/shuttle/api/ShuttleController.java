package io.github.joecoder12.shuttle.api;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.joecoder12.shuttle.api.dto.CreateShuttleRequest;
import io.github.joecoder12.shuttle.api.dto.ShuttleResponse;
import io.github.joecoder12.shuttle.service.ShuttleService;

@RestController
@RequestMapping("/api/shuttles")
public class ShuttleController {

    private final ShuttleService shuttleService;

    public ShuttleController(ShuttleService shuttleService) {
        this.shuttleService = shuttleService;
    }

    @PostMapping
    public ResponseEntity<ShuttleResponse> create(@Valid @RequestBody CreateShuttleRequest request) {
        ShuttleResponse shuttle = shuttleService.create(request);
        return ResponseEntity.created(URI.create("/api/shuttles/" + shuttle.id())).body(shuttle);
    }

    @GetMapping
    public List<ShuttleResponse> list() {
        return shuttleService.list();
    }
}
