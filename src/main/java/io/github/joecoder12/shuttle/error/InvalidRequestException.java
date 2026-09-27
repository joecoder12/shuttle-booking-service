package io.github.joecoder12.shuttle.error;

/** Mapped to 400 Bad Request, for business rules that bean validation can't express. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
