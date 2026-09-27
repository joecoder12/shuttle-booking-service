package io.github.joecoder12.shuttle.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param cutoff how long before departure a trip stops accepting new bookings
 */
@ConfigurationProperties(prefix = "shuttle.booking")
public record BookingProperties(@DefaultValue("30m") Duration cutoff) {
}
