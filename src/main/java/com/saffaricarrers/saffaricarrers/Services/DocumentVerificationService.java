package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.PanVerificationRequest;
import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Exception.DocumentVerificationException;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.DocumentVerificationStatusRepository;
import com.saffaricarrers.saffaricarrers.Repository.UserRepository;
import com.saffaricarrers.saffaricarrers.Responses.CashfreeDocumentResponse;
import com.saffaricarrers.saffaricarrers.Responses.DocumentVerificationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class DocumentVerificationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private DocumentVerificationStatusRepository documentStatusRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${cashfree.verification.app-id}")
    private String cashfreeAppId;

    @Value("${cashfree.verification.secret-key}")
    private String cashfreeSecretKey;

    @Value("${cashfree.verification.base-url}")
    private String cashfreeBaseUrl;

    // ===== AADHAAR VERIFICATION VIA MASKED AADHAAR IMAGE UPLOAD =====

    /**
     * Verify Aadhaar by uploading the physical Aadhaar card image.
     * Calls Cashfree /verification/aadhaar-masking (multipart/form-data).
     *
     * Flow:
     * 1. User uploads a photo of their Aadhaar card (front).
     * 2. We send it to Cashfree Aadhaar Masking API.
     * 3. Cashfree validates the document and returns status + masked image URL.
     * 4. On VALID — we mark Aadhaar as verified and store masked image link.
     *
     * Cashfree API: POST /verification/aadhaar-masking
     * Content-Type: multipart/form-data
     * Fields:
     *   - image (file): JPEG/JPG/PNG, max 10MB, short/long/PVC format accepted
     *   - verification_id (string): your unique ID, max 50 chars, alphanumeric/-/_/.
     *
     * Response statuses:
     *   - VALID            → document is a genuine Aadhaar card, masking applied
     *   - INVALID_DOCUMENT → not a valid Aadhaar image, ask user to re-upload
     */
    public DocumentVerificationResponse verifyMaskedAadhaar(String userId, MultipartFile frontImage, MultipartFile backImage) {
        log.info("Starting Aadhaar OCR verification for user: {}", userId);

        // 1) Guard: already verified?
        DocumentVerificationStatus existingStatus = documentStatusRepository
                .findByUserIdAndDocumentType(userId, OtpVerification.DocumentType.AADHAAR)
                .orElse(null);

        if (existingStatus != null &&
                existingStatus.getStatus() == DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED) {
            throw new DocumentVerificationException("Aadhaar already verified for this user");
        }

        // 2) Validate both files
        if (frontImage == null || frontImage.isEmpty()) {
            throw new DocumentVerificationException("Aadhaar front image is required");
        }
        if (backImage == null || backImage.isEmpty()) {
            throw new DocumentVerificationException("Aadhaar back image is required");
        }

        for (MultipartFile file : new MultipartFile[]{frontImage, backImage}) {
            String contentType = file.getContentType();
            if (contentType == null ||
                    (!contentType.equalsIgnoreCase("image/jpeg") &&
                            !contentType.equalsIgnoreCase("image/jpg") &&
                            !contentType.equalsIgnoreCase("image/png"))) {
                throw new DocumentVerificationException("Invalid file type. Only JPEG/JPG/PNG allowed.");
            }
            if (file.getSize() > 10 * 1024 * 1024) {
                throw new DocumentVerificationException("File too large. Maximum size is 10 MB.");
            }
        }

        String verificationId = generateVerificationId();

        // 3) Mark PENDING before calling API
        updateDocumentVerificationStatus(
                userId,
                OtpVerification.DocumentType.AADHAAR,
                DocumentVerificationStatus.DocumentVerificationStatusEnum.PENDING,
                "AADHAAR_OCR",
                "Aadhaar OCR verification in progress",
                verificationId
        );

        try {
            // 4) Call Cashfree Aadhaar OCR API
            Map<String, Object> apiResponse = callAadhaarOcrApi(frontImage, backImage, verificationId);

            if (apiResponse == null) {
                throw new DocumentVerificationException("No response from Cashfree. Please try again.");
            }

            log.info("Cashfree Aadhaar OCR response: {}", apiResponse);

            String status = (String) apiResponse.get("status");
            Object referenceIdObj = apiResponse.get("reference_id");
            String cashfreeReferenceId = referenceIdObj != null ? referenceIdObj.toString() : verificationId;

            if ("SUCCESS".equalsIgnoreCase(status) || "VALID".equalsIgnoreCase(status)) {
                // Extract name from OCR response
                String extractedName = (String) apiResponse.get("name");
                log.info("Aadhaar OCR extracted name: {}", extractedName);

                if (extractedName == null || extractedName.trim().isEmpty()) {
                    // OCR succeeded but couldn't read the name — image quality issue
                    updateDocumentVerificationStatus(
                            userId,
                            OtpVerification.DocumentType.AADHAAR,
                            DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_REJECTED,
                            "AADHAAR_OCR",
                            "Could not extract name from Aadhaar. Please upload a clearer photo.",
                            cashfreeReferenceId
                    );

                    DocumentVerificationResponse response = new DocumentVerificationResponse();
                    response.setDocumentType("AADHAAR");
                    response.setStatus("DOCUMENT_REJECTED");
                    response.setMessage("Could not read name from Aadhaar card. Please upload a clearer photo.");
                    response.setVerificationId(cashfreeReferenceId);
                    response.setCanRetry(true);
                    return response;
                }

                // 5a) Success — store verified status + extracted name
                updateDocumentVerificationStatus(
                        userId,
                        OtpVerification.DocumentType.AADHAAR,
                        DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED,
                        "AADHAAR_OCR",
                        "Aadhaar verified successfully via OCR",
                        cashfreeReferenceId,
                        extractedName.trim().toUpperCase()  // ← name stored here, used by PAN/RC/DL later
                );

                updateUserVerificationStatusIfComplete(userId);
                evictUserCache(userId);

                DocumentVerificationResponse response = new DocumentVerificationResponse();
                response.setDocumentType("AADHAAR");
                response.setStatus("DOCUMENT_VERIFIED");
                response.setMessage("Aadhaar card verified successfully");
                response.setVerificationId(cashfreeReferenceId);
                response.setVerifiedName(extractedName.trim().toUpperCase());
                response.setCanRetry(false);
                return response;

            } else if ("FAILED".equalsIgnoreCase(status) || "INVALID_DOCUMENT".equalsIgnoreCase(status)) {
                // 5b) Bad image — let user retry
                updateDocumentVerificationStatus(
                        userId,
                        OtpVerification.DocumentType.AADHAAR,
                        DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_REJECTED,
                        "AADHAAR_OCR",
                        "Invalid Aadhaar document. Please upload a clear photo of your Aadhaar card.",
                        cashfreeReferenceId
                );

                DocumentVerificationResponse response = new DocumentVerificationResponse();
                response.setDocumentType("AADHAAR");
                response.setStatus("DOCUMENT_REJECTED");
                response.setMessage("The uploaded image is not a valid Aadhaar card. Please upload a clear photo.");
                response.setVerificationId(cashfreeReferenceId);
                response.setCanRetry(true);
                return response;

            } else {
                // 5c) Unknown status
                String message = apiResponse.get("message") != null
                        ? apiResponse.get("message").toString()
                        : "Unexpected status: " + status;

                updateDocumentVerificationStatus(
                        userId,
                        OtpVerification.DocumentType.AADHAAR,
                        DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_REJECTED,
                        "AADHAAR_OCR",
                        message,
                        cashfreeReferenceId
                );

                DocumentVerificationResponse response = new DocumentVerificationResponse();
                response.setDocumentType("AADHAAR");
                response.setStatus("DOCUMENT_REJECTED");
                response.setMessage(message);
                response.setVerificationId(cashfreeReferenceId);
                response.setCanRetry(true);
                return response;
            }

        } catch (DocumentVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Aadhaar OCR verification failed for user: {}", userId, e);

            updateDocumentVerificationStatus(
                    userId,
                    OtpVerification.DocumentType.AADHAAR,
                    DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_REJECTED,
                    "AADHAAR_OCR",
                    "Verification failed: " + e.getMessage(),
                    verificationId
            );

            throw new DocumentVerificationException("Aadhaar verification failed: " + e.getMessage());
        }
    }

    private Map<String, Object> callAadhaarOcrApi(MultipartFile frontImage, MultipartFile backImage, String verificationId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", cashfreeAppId);
            headers.set("x-client-secret", cashfreeSecretKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource frontResource = new ByteArrayResource(frontImage.getBytes()) {
                @Override
                public String getFilename() {
                    String original = frontImage.getOriginalFilename();
                    return (original != null && !original.isEmpty()) ? original : "aadhaar_front.jpg";
                }
            };

            ByteArrayResource backResource = new ByteArrayResource(backImage.getBytes()) {
                @Override
                public String getFilename() {
                    String original = backImage.getOriginalFilename();
                    return (original != null && !original.isEmpty()) ? original : "aadhaar_back.jpg";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("front_image", frontResource);
            body.add("back_image", backResource);
            body.add("verification_id", verificationId);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            String endpoint = cashfreeBaseUrl + "/verification/document/aadhaar";
            log.info("Calling Cashfree Aadhaar OCR API: {}", endpoint);
            log.info("verification_id: {}", verificationId);

            ResponseEntity<Map> response = restTemplate.exchange(endpoint, HttpMethod.POST, entity, Map.class);
            log.info("Cashfree Aadhaar OCR response status: {}", response.getStatusCode());
            log.info("Cashfree Aadhaar OCR response body: {}", response.getBody());

            return response.getBody();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP error calling Aadhaar OCR API. Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new DocumentVerificationException(
                    "Cashfree API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error calling Aadhaar OCR API", e);
            throw new DocumentVerificationException("Failed to call verification service: " + e.getMessage());
        }
    }
    /**
     * Calls POST /verification/aadhaar-masking with multipart/form-data.
     *
     * Note: We use LinkedMultiValueMap + ByteArrayResource because RestTemplate's
     * multipart support requires the resource to expose a filename for Cashfree
     * to accept the file field correctly.
     */
    private Map<String, Object> callAadhaarMaskingApi(MultipartFile image, String verificationId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", cashfreeAppId);
            headers.set("x-client-secret", cashfreeSecretKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Wrap bytes in a named ByteArrayResource so RestTemplate sets filename in part
            ByteArrayResource imageResource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    String original = image.getOriginalFilename();
                    return (original != null && !original.isEmpty()) ? original : "aadhaar.jpg";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", imageResource);
            body.add("verification_id", verificationId);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            String endpoint = cashfreeBaseUrl + "/verification/aadhaar-masking";
            log.info("Calling Cashfree Aadhaar Masking API: {}", endpoint);
            log.info("verification_id: {}", verificationId);

            ResponseEntity<Map> response = restTemplate.exchange(endpoint, HttpMethod.POST, entity, Map.class);
            log.info("Cashfree Aadhaar Masking response status: {}", response.getStatusCode());
            log.info("Cashfree Aadhaar Masking response body: {}", response.getBody());

            return response.getBody();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP error calling Aadhaar Masking API. Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new DocumentVerificationException(
                    "Cashfree API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error calling Aadhaar Masking API", e);
            throw new DocumentVerificationException("Failed to call verification service: " + e.getMessage());
        }
    }

    // ===== PAN VERIFICATION (UNCHANGED) =====

    public DocumentVerificationResponse verifyPan(String userId, PanVerificationRequest request) {
        log.info("Verifying PAN for user: {}", userId);

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DocumentVerificationStatus existingPanStatus = documentStatusRepository
                .findByUserIdAndDocumentType(userId, OtpVerification.DocumentType.PAN)
                .orElse(null);

        if (existingPanStatus != null &&
                existingPanStatus.getStatus() == DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED) {
            throw new DocumentVerificationException("PAN already verified for this user");
        }

        DocumentVerificationStatus aadhaarStatus = documentStatusRepository
                .findByUserIdAndDocumentType(userId, OtpVerification.DocumentType.AADHAAR)
                .orElseThrow(() -> new DocumentVerificationException(
                        "Aadhaar must be verified before PAN verification"));

        if (aadhaarStatus.getStatus() != DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED) {
            throw new DocumentVerificationException(
                    "Aadhaar verification is not completed. Please verify Aadhaar first.");
        }

        String verifiedAadhaarName = aadhaarStatus.getVerifiedName();

        // NOTE: Masked Aadhaar upload does NOT return a name (no OCR).
        // If verifiedName is null/empty, we skip name cross-check and only verify PAN validity.
        boolean hasAadhaarName = verifiedAadhaarName != null && !verifiedAadhaarName.trim().isEmpty();

        boolean panAlreadyUsed = documentStatusRepository
                .existsByDocumentNumberAndDocumentTypeAndStatusAndUserIdNot(
                        request.getPanNumber(),
                        OtpVerification.DocumentType.PAN,
                        DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED,
                        userId
                );

        if (panAlreadyUsed) {
            throw new DocumentVerificationException("This PAN number is already verified with another account");
        }

        try {
            String nameToMatch = hasAadhaarName ? normalizeName(verifiedAadhaarName) : null;
            CashfreeDocumentResponse panResponse = verifyPanDocument(request.getPanNumber(), nameToMatch);

            if (panResponse.isSuccess() && panResponse.isValid()) {

                String matchDescription = "AADHAAR_IMAGE_FLOW"; // no name from masked aadhaar
                if (hasAadhaarName && panResponse.getName() != null) {
                    String registeredName = normalizeName(panResponse.getName());
                    String normalizedAadhaarName = normalizeName(verifiedAadhaarName);
                    String reversedAadhaarName = reverseNameTokens(verifiedAadhaarName);

                    boolean localNameMatch = doNamesMatch(registeredName, normalizedAadhaarName)
                            || doNamesMatch(registeredName, reversedAadhaarName);

                    log.info("Name match — registered: '{}', aadhaar: '{}', matched: {}",
                            registeredName, normalizedAadhaarName, localNameMatch);

                    if (!localNameMatch) {
                        String message = String.format(
                                "PAN name does not match Aadhaar name. PAN registered name: %s, Aadhaar name: %s",
                                registeredName, verifiedAadhaarName);

                        updateDocumentVerificationStatus(userId, OtpVerification.DocumentType.PAN,
                                DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_REJECTED,
                                request.getPanNumber(), message, UUID.randomUUID().toString());

                        DocumentVerificationResponse response = new DocumentVerificationResponse();
                        response.setDocumentType("PAN");
                        response.setStatus("DOCUMENT_REJECTED");
                        response.setMessage(message);
                        response.setCanRetry(true);
                        return response;
                    }
                    matchDescription = doNamesMatch(registeredName, normalizedAadhaarName)
                            ? "DIRECT_MATCH" : "REVERSED_TOKEN_MATCH";
                }

                String aadhaarSeedingStatus = panResponse.getAadhaarSeedingStatus();
                if (!"Y".equals(aadhaarSeedingStatus)) {
                    log.warn("PAN {} is not linked with Aadhaar. Seeding status: {}",
                            request.getPanNumber(), aadhaarSeedingStatus);
                }

                updateDocumentVerificationStatus(
                        userId, OtpVerification.DocumentType.PAN,
                        DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED,
                        request.getPanNumber(),
                        "PAN verification completed. Match: " + matchDescription,
                        UUID.randomUUID().toString(),
                        panResponse.getName()
                );

                updateUserVerificationStatusIfComplete(userId);
                evictUserCache(userId);

                DocumentVerificationResponse response = new DocumentVerificationResponse();
                response.setDocumentType("PAN");
                response.setStatus("DOCUMENT_VERIFIED");
                response.setMessage("PAN verification completed successfully");
                response.setVerifiedName(panResponse.getName());
                response.setNameMatch(matchDescription);
                response.setAadhaarSeedingStatus(aadhaarSeedingStatus);
                response.setCanRetry(false);
                return response;

            } else {
                updateDocumentVerificationStatus(
                        userId, OtpVerification.DocumentType.PAN,
                        DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_REJECTED,
                        request.getPanNumber(), panResponse.getMessage(), UUID.randomUUID().toString());

                DocumentVerificationResponse response = new DocumentVerificationResponse();
                response.setDocumentType("PAN");
                response.setStatus("DOCUMENT_REJECTED");
                response.setMessage(panResponse.getMessage());
                response.setCanRetry(true);
                return response;
            }

        } catch (Exception e) {
            log.error("PAN verification failed for user: {}", userId, e);

            updateDocumentVerificationStatus(
                    userId, OtpVerification.DocumentType.PAN,
                    DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_REJECTED,
                    request.getPanNumber(), "PAN verification failed: " + e.getMessage(),
                    UUID.randomUUID().toString());

            throw new DocumentVerificationException("PAN verification failed: " + e.getMessage());
        }
    }

    // ===== NAME MATCHING HELPERS =====

    private boolean doNamesMatch(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String n1 = normalizeName(name1);
        String n2 = normalizeName(name2);
        if (n1.equals(n2)) return true;
        return tokenSort(n1).equals(tokenSort(n2));
    }

    private String tokenSort(String name) {
        if (name == null) return "";
        String[] tokens = name.trim().split("\\s+");
        java.util.Arrays.sort(tokens);
        return String.join(" ", tokens);
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        return name.trim().toUpperCase().replaceAll("\\s+", " ");
    }

    private String reverseNameTokens(String name) {
        if (name == null) return null;
        String[] tokens = name.trim().split("\\s+");
        if (tokens.length <= 1) return name.toUpperCase();
        StringBuilder sb = new StringBuilder();
        sb.append(tokens[tokens.length - 1].toUpperCase());
        for (int i = 0; i < tokens.length - 1; i++) {
            sb.append(" ").append(tokens[i].toUpperCase());
        }
        return sb.toString();
    }

    // ===== PAN CASHFREE API =====

    private CashfreeDocumentResponse verifyPanDocument(String panNumber, String nameToMatch) {
        HttpHeaders headers = createCashfreeHeaders();

        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("pan", panNumber);
        if (nameToMatch != null) {
            requestBody.put("name", nameToMatch);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            String panEndpoint = cashfreeBaseUrl + "/verification/pan";
            log.info("Calling Cashfree PAN API: {}", panEndpoint);

            ResponseEntity<Map> response = restTemplate.exchange(panEndpoint, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();

                CashfreeDocumentResponse docResponse = new CashfreeDocumentResponse();
                Boolean validField = (Boolean) responseBody.get("valid");
                String panStatus = (String) responseBody.get("pan_status");
                boolean isValid = (validField != null && validField) || "VALID".equalsIgnoreCase(panStatus);

                docResponse.setSuccess(isValid);
                docResponse.setValid(isValid);
                docResponse.setName((String) responseBody.get("registered_name"));
                docResponse.setNameMatch((String) responseBody.get("name_match"));

                Object scoreObj = responseBody.get("name_match_score");
                if (scoreObj != null) {
                    try { docResponse.setNameMatchScore(Double.parseDouble(scoreObj.toString())); }
                    catch (NumberFormatException ignored) {}
                }

                docResponse.setAadhaarSeedingStatus((String) responseBody.get("aadhaar_seeding_status"));
                docResponse.setAadhaarSeedingStatusDesc((String) responseBody.get("aadhaar_seeding_status_desc"));

                String message = (String) responseBody.get("message");
                docResponse.setMessage(message != null ? message : (isValid ? "PAN verified" : "Invalid PAN"));
                return docResponse;
            }

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP Error calling Cashfree PAN API: {}, {}", e.getStatusCode(), e.getResponseBodyAsString());
            CashfreeDocumentResponse err = new CashfreeDocumentResponse();
            err.setSuccess(false); err.setValid(false);
            err.setMessage("PAN verification failed: " + e.getMessage());
            return err;
        } catch (Exception e) {
            log.error("Error calling Cashfree PAN API", e);
        }

        CashfreeDocumentResponse err = new CashfreeDocumentResponse();
        err.setSuccess(false); err.setValid(false);
        err.setMessage("PAN verification failed. Please try again.");
        return err;
    }

    // ===== HELPER METHODS =====

    private HttpHeaders createCashfreeHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", cashfreeAppId);
        headers.set("x-client-secret", cashfreeSecretKey);
        return headers;
    }

    private void updateDocumentVerificationStatus(String userId, OtpVerification.DocumentType documentType,
                                                  DocumentVerificationStatus.DocumentVerificationStatusEnum status,
                                                  String documentNumber, String message, String referenceId) {
        updateDocumentVerificationStatus(userId, documentType, status, documentNumber, message, referenceId, null);
    }

    private void updateDocumentVerificationStatus(String userId, OtpVerification.DocumentType documentType,
                                                  DocumentVerificationStatus.DocumentVerificationStatusEnum status,
                                                  String documentNumber, String message, String referenceId,
                                                  String verifiedName) {
        DocumentVerificationStatus docStatus = documentStatusRepository
                .findByUserIdAndDocumentType(userId, documentType)
                .orElse(new DocumentVerificationStatus());

        docStatus.setUserId(userId);
        docStatus.setDocumentType(documentType);
        docStatus.setStatus(status);
        docStatus.setDocumentNumber(documentNumber);
        docStatus.setVerificationMessage(message);
        docStatus.setThirdPartyReferenceId(referenceId);
        docStatus.setVerifiedName(verifiedName);

        documentStatusRepository.save(docStatus);
    }

    private void updateUserVerificationStatusIfComplete(String userId) {
        boolean aadhaarVerified = documentStatusRepository.existsByUserIdAndDocumentTypeAndStatus(
                userId, OtpVerification.DocumentType.AADHAAR,
                DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED);

        boolean panVerified = documentStatusRepository.existsByUserIdAndDocumentTypeAndStatus(
                userId, OtpVerification.DocumentType.PAN,
                DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED);

        User user = userRepository.findByUserId(userId).orElse(null);
        if (user != null) {
            if (aadhaarVerified && panVerified) {
                user.setVerificationStatus(User.VerificationStatus.VERIFIED);
                user.setStatus(User.UserStatus.INACTIVE);
                user.setVerified(true);  // ← THIS fixes "still showing Aadhaar verification"
                log.info("Both documents verified for: {}. User marked as verified.", userId);
            } else {
                user.setVerificationStatus(User.VerificationStatus.PENDING);
                user.setStatus(User.UserStatus.INACTIVE);
            }
            userRepository.save(user);
            evictUserCache(userId);
        }
    }    private void evictUserCache(String userId) {
        try {
            if (cacheManager.getCache("profiles") != null) cacheManager.getCache("profiles").evict(userId);
            if (cacheManager.getCache("users") != null) cacheManager.getCache("users").evict(userId);
            if (cacheManager.getCache("verification") != null) cacheManager.getCache("verification").evict(userId);
            log.info("✅ Cache evicted for user: {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to evict cache for user: {}", userId, e);
        }
    }

    private String generateVerificationId() {
        // Cashfree verification_id: max 50 chars, alphanumeric + . - _
        return "MASK_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public List<DocumentVerificationResponse> getDocumentVerificationStatus(String userId) {
        List<DocumentVerificationStatus> statuses = documentStatusRepository.findByUserId(userId);

        return statuses.stream().map(status -> {
            DocumentVerificationResponse response = new DocumentVerificationResponse();
            response.setDocumentType(status.getDocumentType().name());
            response.setStatus(status.getStatus().name());
            response.setMessage(status.getVerificationMessage());
            response.setReferenceId(status.getThirdPartyReferenceId());
            response.setVerifiedName(status.getVerifiedName());
            response.setCanRetry(canRetryVerification(status));
            return response;
        }).toList();
    }

    private boolean canRetryVerification(DocumentVerificationStatus status) {
        return status.getStatus() == DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_REJECTED ||
                status.getStatus() == DocumentVerificationStatus.DocumentVerificationStatusEnum.OTP_EXPIRED ||
                status.getStatus() == DocumentVerificationStatus.DocumentVerificationStatusEnum.OTP_FAILED;
    }
}