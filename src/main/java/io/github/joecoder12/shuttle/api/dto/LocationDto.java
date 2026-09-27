package io.github.joecoder12.shuttle.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import io.github.joecoder12.shuttle.domain.GeoPoint;

public record LocationDto(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {

    public static LocationDto from(GeoPoint point) {
        return new LocationDto(point.latitude(), point.longitude());
    }

    public GeoPoint toGeoPoint() {
        return new GeoPoint(latitude, longitude);
    }
}
