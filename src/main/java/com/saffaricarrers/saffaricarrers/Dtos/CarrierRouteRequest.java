package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class CarrierRouteRequest {
    private String uid;

    @NotBlank(message = "From location is required")
    private String fromLocation;

    @NotBlank(message = "To location is required")
    private String toLocation;

    private Long longAddressId;

    @NotNull(message = "Available date is required")
    @FutureOrPresent(message = "Date must be today or in the future")
    private LocalDate availableDate;

    @NotNull(message = "Deadline date is required")
    @FutureOrPresent(message = "Deadline date must be today or in the future")
    private LocalDate deadlineDate;

    @NotNull(message = "Available time is required")
    private LocalTime availableTime;

    @NotNull(message = "Deadline time is required")
    private LocalTime deadlineTime;

    @NotNull(message = "Transport type is required")
    private CarrierRoute.TransportType transportType;

    @NotNull(message = "Max weight is required")
    @DecimalMin(value = "0.1", message = "Max weight must be greater than 0")
    private Double maxWeight;

    @NotNull(message = "Max quantity is required")
    @Min(value = 1, message = "Max quantity must be at least 1")
    private Integer maxQuantity;

    // Source/From coordinates
    private double latitude;
    private double longitude;

    // ✅ Destination/To coordinates
    private double toLatitude;
    private double toLongitude;

    @NotEmpty(message = "Pricing information is required")
    private List<RoutePricingRequest> pricing;

    private Boolean isDirectRoute = true;
    private String intermediateStops;
}