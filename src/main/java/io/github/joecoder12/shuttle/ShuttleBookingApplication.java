package io.github.joecoder12.shuttle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShuttleBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShuttleBookingApplication.class, args);
    }
}
