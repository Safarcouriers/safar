package com.saffaricarrers.saffaricarrers.Controller;

import com.saffaricarrers.saffaricarrers.Dtos.PanVerificationRequest;
import com.saffaricarrers.saffaricarrers.Exception.DocumentVerificationException;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Responses.ApiResponse;
import com.saffaricarrers.saffaricarrers.Responses.DocumentVerificationResponse;
import com.saffaricarrers.saffaricarrers.Services.DocumentVerificationService;
import com.saffaricarrers.saffaricarrers.Services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/document-verification")
@RequiredArgsConstructor
@Slf4j
@Validated
public class DocumentVerificationController {

    @Autowired
    private DocumentVerificationService documentVerificationService;

    @Autowired
    private UserService userService;

    /**
     * Verify Aadhaar via masked Aadhaar card image upload.
     *
     * The user uploads a photo of their physical Aadhaar card (front side).
     * We forward it to Cashfree's /verification/aadhaar-masking API which:
     *   - Validates the card is genuine
     *   - Masks the first 8 digits of the Aadhaar number
     *   - Returns a masked image link (safe to store)
     *
     * @param userId  User ID (path variable)
     * @param image   Aadhaar card front image (JPEG/JPG/PNG, max 10MB)
     *
     * Status Codes:
     * - 200 OK:                 Aadhaar verified successfully
     * - 400 Bad Request:        No file / wrong file type / too large
     * - 404 Not Found:          User not found
     * - 409 Conflict:           Aadhaar already verified
     * - 422 Unprocessable:      Invalid Aadhaar document (bad image)
     * - 500 Internal Error:     Unexpected system error
     *
     * Example cURL:
     *   curl -X POST /api/document-verification/{userId}/aadhaar/upload-masked \
     *     -F "image=@/path/to/aadhaar_front.jpg"
     */
    @PostMapping(value = "/{userId}/aadhaar/upload-masked", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentVerificationResponse> verifyMaskedAadhaar(
            @PathVariable String userId,
            @RequestPart("front_image") MultipartFile frontImage,
            @RequestPart("back_image") MultipartFile backImage) {
        return ResponseEntity.ok(documentVerificationService.verifyMaskedAadhaar(userId, frontImage, backImage));
    }
    /**
     * Verify PAN card.
     * Prerequisite: Aadhaar must be verified first.
     *
     * @param userId  User ID
     * @param request PAN number
     *
     * Status Codes:
     * - 200 OK:             PAN verified
     * - 400 Bad Request:    Invalid PAN or Aadhaar not yet verified
     * - 404 Not Found:      User not found
     * - 409 Conflict:       PAN already verified
     * - 422 Unprocessable:  PAN is invalid
     * - 500 Internal Error: Unexpected error
     */
    @PostMapping("/{userId}/pan/verify")
    public ResponseEntity<ApiResponse<DocumentVerificationResponse>> verifyPan(
            @PathVariable String userId,
            @RequestBody PanVerificationRequest request) {

        log.info("Received PAN verification request for user: {}", userId);

        try {
            if (!userService.existsByUserId(userId)) {
                log.warn("User not found: {}", userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("User not found with ID: " + userId));
            }

            DocumentVerificationResponse response = documentVerificationService.verifyPan(userId, request);

            if ("DOCUMENT_VERIFIED".equals(response.getStatus())) {
                log.info("PAN verified successfully for user: {}", userId);
                return ResponseEntity.ok(ApiResponse.success(response,
                        "PAN verification completed successfully"));

            } else if ("DOCUMENT_REJECTED".equals(response.getStatus())) {
                log.warn("PAN rejected for user {}: {}", userId, response.getMessage());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(ApiResponse.error(response.getMessage()));

            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(response.getMessage()));
            }

        } catch (DocumentVerificationException e) {
            log.warn("PAN verification failed for user {}: {}", userId, e.getMessage());
            if (e.getMessage().contains("already verified")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error during PAN verification for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("System error occurred. Please try again later."));
        }
    }

    /**
     * Get all document verification statuses for a user.
     */
    @GetMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<List<DocumentVerificationResponse>>> getVerificationStatus(
            @PathVariable String userId) {

        log.info("Fetching verification status for user: {}", userId);

        try {
            if (!userService.existsByUserId(userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("User not found with ID: " + userId));
            }

            List<DocumentVerificationResponse> statuses =
                    documentVerificationService.getDocumentVerificationStatus(userId);

            return ResponseEntity.ok(ApiResponse.success(statuses,
                    "Document verification status retrieved successfully"));

        } catch (Exception e) {
            log.error("Failed to retrieve verification status for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve verification status. Please try again later."));
        }
    }
}