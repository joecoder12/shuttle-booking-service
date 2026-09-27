package io.github.joecoder12.shuttle.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shuttleBookingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Shuttle Booking Service API")
                .version("0.1.0")
                .description("Book seats on employee shuttle trips and get an ordered pickup route for each trip."));
    }
}
