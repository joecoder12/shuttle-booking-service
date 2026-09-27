package io.github.joecoder12.shuttle.error;

/**
 * Mapped to 409 Conflict: the request is well-formed but clashes with the current state, e.g. the trip
 * is full. {@code code} is a stable, machine-readable reason clients can branch on.
 */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
