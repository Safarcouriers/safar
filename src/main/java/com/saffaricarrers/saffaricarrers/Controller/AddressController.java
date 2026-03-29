package com.saffaricarrers.saffaricarrers.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.saffaricarrers.saffaricarrers.Dtos.AddressRequest;
import com.saffaricarrers.saffaricarrers.Responses.AddressResponse;
import com.saffaricarrers.saffaricarrers.Responses.ApiResponse;
import com.saffaricarrers.saffaricarrers.Services.AddressService;
import com.saffaricarrers.saffaricarrers.Services.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/user/{userId}/address")
@RequiredArgsConstructor
@Slf4j
public class AddressController {

    private final AddressService addressService;
    private final UserService userService;

    @PostMapping()
    public ResponseEntity<?> createAddress(
            @PathVariable String userId,
            @RequestBody AddressRequest request) {

        try {
            log.info("Creating address for user: {}", userId);

            // Uncomment when user verification is needed
            // if (!userService.canUserAccessFeatures(userId)) {
            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
            //             .body(Map.of(
            //                 "success", false,
            //                 "message", "Address creation not allowed. Please complete document verification first.",
            //                 "error", "User verification required"
            //             ));
            // }

            AddressResponse response = addressService.createAddress(userId, request);

            // Create a success response wrapper
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("message", "Address created successfully");
            successResponse.put("data", response);

            return ResponseEntity.status(HttpStatus.CREATED).body(successResponse);

        } catch (IllegalArgumentException e) {
            log.error("Invalid address data for user {}: ", userId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Invalid address data provided",
                            "error", e.getMessage()
                    ));

        } catch (DataIntegrityViolationException e) {
            log.error("Address already exists or data constraint violation for user {}: ", userId, e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "success", false,
                            "message", "Address already exists or data constraint violation",
                            "error", "Duplicate address found"
                    ));

        } catch (EntityNotFoundException e) {
            log.error("User not found during address creation: {}", userId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "message", "User not found",
                            "error", "Invalid user ID provided"
                    ));

        } catch (Exception e) {
            log.error("Unexpected error during address creation for user {}: ", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Internal server error occurred",
                            "error", "Please try again later"
                    ));
        }
    }
    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getUserAddresses(
            @PathVariable String userId) {
        try {
            if (!userService.canUserAccessFeatures(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Address access not allowed. Please complete document verification first."));
            }
            List<AddressResponse> addresses = addressService.getUserAddresses(userId);
            return ResponseEntity.ok(ApiResponse.success(addresses, "Addresses retrieved successfully"));
        } catch (Exception e) {
            log.error("Failed to get user addresses for: {}", userId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable String userId,
            @PathVariable Long addressId,
             @RequestBody AddressRequest request) {
        try {
            if (!userService.canUserAccessFeatures(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Address updates not allowed. Please complete document verification first."));
            }

            AddressResponse response = addressService.updateAddress(userId, addressId, request);
            return ResponseEntity.ok(ApiResponse.success(response, "Address updated successfully"));
        } catch (Exception e) {
            log.error("Failed to update address for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable String userId,
            @PathVariable Long addressId) {
        try {
            if (!userService.canUserAccessFeatures(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Address deletion not allowed. Please complete document verification first."));
            }

            addressService.deleteAddress(userId, addressId);
            return ResponseEntity.ok(ApiResponse.success(null, "Address deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete address for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/default")
    public ResponseEntity<ApiResponse<AddressResponse>> getDefaultAddress(@PathVariable String userId) {
        try {
            if (!userService.canUserAccessFeatures(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Address access not allowed. Please complete document verification first."));
            }

            AddressResponse defaultAddress = addressService.getDefaultAddress(userId);
            return ResponseEntity.ok(ApiResponse.success(defaultAddress, "Default address retrieved successfully"));
        } catch (Exception e) {
            log.error("Failed to get default address for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{addressId}/set-default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            @PathVariable String userId,
            @PathVariable Long addressId) {
        try {
            if (!userService.canUserAccessFeatures(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Address updates not allowed. Please complete document verification first."));
            }

            AddressResponse response = addressService.setDefaultAddress(userId, addressId);
            return ResponseEntity.ok(ApiResponse.success(response, "Default address updated successfully"));
        } catch (Exception e) {
            log.error("Failed to set default address for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    @GetMapping("/istesting")
    public String testing()
    {
        return "TEsting on";
    }
}

