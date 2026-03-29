package com.saffaricarrers.saffaricarrers.Controller;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saffaricarrers.saffaricarrers.Dtos.DocumentUploadRequest;
import com.saffaricarrers.saffaricarrers.Dtos.UserRegistrationRequest;
import com.saffaricarrers.saffaricarrers.Entity.User;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Responses.PageResponse;
import com.saffaricarrers.saffaricarrers.Services.UserService;
import com.saffaricarrers.saffaricarrers.Services.CallBackService;
import com.saffaricarrers.saffaricarrers.Responses.UserProfileResponse;
import com.saffaricarrers.saffaricarrers.Responses.ProfileCompletionResponse;
import com.saffaricarrers.saffaricarrers.Responses.VerificationStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final CallBackService callBackService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @RequestParam("user") String request,
            @RequestParam(value = "profileImage", required = false) MultipartFile profile) {

        try {
            // Parse the user registration request
            UserRegistrationRequest userRequest = objectMapper.readValue(request, UserRegistrationRequest.class);

            // Register the user
            UserProfileResponse userProfile = userService.registerUser(userRequest, profile);

            // Create a success response wrapper
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("message", "User registered successfully");
            successResponse.put("data", userProfile);

            return ResponseEntity.status(HttpStatus.CREATED).body(successResponse);

        } catch (JsonProcessingException e) {
            log.error("Invalid JSON format in user registration request: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Invalid request format",
                            "error", e.getMessage()
                    ));

        } catch (IllegalArgumentException e) {
            log.error("Invalid user data: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Invalid user data provided",
                            "error", e.getMessage()
                    ));

        } catch (DataIntegrityViolationException e) {
            log.error("User already exists or data constraint violation: ", e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "success", false,
                            "message", "User already exists or data constraint violation",
                            "error", "Duplicate data found"
                    ));

        } catch (Exception e) {
            log.error("Unexpected error during user registration: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Internal server error occurred",
                            "error", "Please try again later"
                    ));
        }
    }

    @PostMapping("/{userId}/upload-documents")
    public ResponseEntity<UserProfileResponse> uploadDocumentImages(
            @PathVariable String userId,
            @RequestParam(value = "aadharFrontImage", required = false) MultipartFile aadharFrontImage,
            @RequestParam(value = "aadharBackImage", required = false) MultipartFile aadharBackImage,
            @RequestParam(value = "panCardImage", required = false) MultipartFile panCardImage         ) {

        try {
            DocumentUploadRequest request = new DocumentUploadRequest();
            request.setAadharFrontImage(aadharFrontImage);
            request.setAadharBackImage(aadharBackImage);
            request.setPanCardImage(panCardImage);


            UserProfileResponse response = userService.uploadDocumentImages(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error uploading document images for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/{userId}/upload-profile-image")
    public ResponseEntity<String> uploadProfileImage(
            @PathVariable String userId,
            @RequestParam("profileImage") MultipartFile profileImage) {

        try {
            if (profileImage.isEmpty()) {
                return ResponseEntity.badRequest().body("Profile image file is empty");
            }

            String profileUrl = callBackService.uploadProfileImageToS3(profileImage);

            // Update user profile with the new image URL
            // You can create a separate method in UserService to update just the profile image

            return ResponseEntity.ok(profileUrl);
        } catch (IOException e) {
            log.error("Error uploading profile image for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload profile image: " + e.getMessage());
        }
    }

//    @GetMapping("/{userId}/profile")
//    public ResponseEntity<String> getUserProfile(@PathVariable String userId) {
//        try {
//            UserProfileResponse response = userService.getUserProfile(userId);
//            return ResponseEntity.ok(response.toString());
//        } catch (ResourceNotFoundException e) {
//            return ResponseEntity.ok("User not found");
//        } catch (Exception e) {
//            return ResponseEntity.ok("An error occurred");
//        }
//    }


    @PutMapping("/{userId}/profile")
    public ResponseEntity<UserProfileResponse> updateUserProfile(
            @PathVariable String userId,
            @Valid @RequestBody UserRegistrationRequest request) {
        try {
            UserProfileResponse response = userService.updateUserProfile(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating user profile for: {}", userId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // Create a standardized error response DTO
     class ErrorResponse {
        private String error;
        private String message;
        private String userId;
        private int statusCode;
        private LocalDateTime timestamp;

        public ErrorResponse(String error, String message, String userId, int statusCode, LocalDateTime timestamp) {
            this.error = error;
            this.message = message;
            this.userId = userId;
            this.statusCode = statusCode;
            this.timestamp = timestamp;
        }
// Constructor, getters, and setters
    }

    // Controller Method

    @GetMapping("/{userId}/completion-status")
    public ResponseEntity<ProfileCompletionResponse> getProfileCompletionStatus(@PathVariable String userId) {
        try {
            ProfileCompletionResponse response = userService.getProfileCompletionStatus(userId);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            // Catch ResourceNotFoundException FIRST before generic Exception
            log.error("User not found: {}", userId);

            // Return a structured error response instead of empty 404
            ProfileCompletionResponse errorResponse = new ProfileCompletionResponse();
            errorResponse.setUserId(userId);
            errorResponse.setProfileCompleted(false);
            errorResponse.setCompletionPercentage(0);
            errorResponse.setNextStep("USER_NOT_FOUND");
            errorResponse.setNextStepMessage("User not found. Please register first.");

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            log.error("Error fetching profile completion status for: {}", userId, e);

            // Return a structured error response for other exceptions
            ProfileCompletionResponse errorResponse = new ProfileCompletionResponse();
            errorResponse.setUserId(userId);
            errorResponse.setProfileCompleted(false);
            errorResponse.setCompletionPercentage(0);
            errorResponse.setNextStep("ERROR");
            errorResponse.setNextStepMessage("An error occurred. Please try again later.");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{userId}/verification-status")
    public ResponseEntity<VerificationStatusResponse> getVerificationStatus(@PathVariable String userId) {
        try {
            VerificationStatusResponse response = userService.getVerificationStatus(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching verification status for: {}", userId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/{userId}/can-access-features")
    public ResponseEntity<Boolean> canUserAccessFeatures(@PathVariable String userId) {
        try {
            boolean canAccess = userService.canUserAccessFeatures(userId);
            return ResponseEntity.ok(canAccess);
        } catch (Exception e) {
            log.error("Error checking feature access for: {}", userId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/{userId}/exists")
    public ResponseEntity<Boolean> checkUserExists(@PathVariable String userId) {
        try {
            boolean exists = userService.existsByUserId(userId);
            return ResponseEntity.ok(exists);
        } catch (Exception e) {
            log.error("Error checking user existence for: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
//    @GetMapping("/all")
//    public List<User> getALLuSERS
//            ()
//    {
//        return userService.getALlUser();
//    }
@GetMapping
public ResponseEntity<PageResponse<UserProfileResponse>> getAllUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

    PageResponse<UserProfileResponse> users = userService.getAllUsers(page, size);
    return ResponseEntity.ok(users);
}

    /**
     * GET /api/admin/users?page=0&size=50
     * Admin endpoint with carrier profiles loaded (for dashboard)
     */
    @GetMapping("/admin")
    public ResponseEntity<PageResponse<UserProfileResponse>> getAllUsersWithProfiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageResponse<UserProfileResponse> users = userService.getAllUsersWithCarrierProfile(page, size);
        return ResponseEntity.ok(users);
    }
    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable String userId) {
        try {
            UserProfileResponse response = userService.getUserProfile(userId);
            return ResponseEntity.ok(response);  // ✅ Returns JSON automatically
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
    @PutMapping("/{userId}/fcm-token")
    public ResponseEntity<?> updateFcmToken(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {

        try {
            System.out.println("triggeeed");
            String fcmToken = request.get("fcmToken");

            if (fcmToken == null || fcmToken.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false,
                                "message", "FCM token is required"
                        ));
            }

            // Update the FCM token
            userService.updateFcmToken(userId, fcmToken);

            log.info("FCM token updated successfully for user: {}", userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "FCM token updated successfully",
                    "userId", userId
            ));

        } catch (ResourceNotFoundException e) {
            log.error("User not found: {}", userId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "message", "User not found",
                            "error", e.getMessage()
                    ));

        } catch (Exception e) {
            log.error("Error updating FCM token for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Failed to update FCM token",
                            "error", e.getMessage()
                    ));
        }
    }

}