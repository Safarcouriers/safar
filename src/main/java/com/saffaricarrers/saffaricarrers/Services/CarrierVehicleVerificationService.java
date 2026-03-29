package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.DlVerificationRequest;
import com.saffaricarrers.saffaricarrers.Dtos.RcVerificationRequest;
import com.saffaricarrers.saffaricarrers.Entity.CarrierVehicleVerification;
import com.saffaricarrers.saffaricarrers.Entity.DigilockerVerification;
import com.saffaricarrers.saffaricarrers.Entity.DocumentVerificationStatus;
import com.saffaricarrers.saffaricarrers.Entity.OtpVerification;
import com.saffaricarrers.saffaricarrers.Exception.DocumentVerificationException;
import com.saffaricarrers.saffaricarrers.Repository.CarrierVehicleVerificationRepository;
import com.saffaricarrers.saffaricarrers.Repository.DigilockerVerificationRepository;
import com.saffaricarrers.saffaricarrers.Repository.DocumentVerificationStatusRepository;
import com.saffaricarrers.saffaricarrers.Responses.VehicleVerificationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@Transactional
public class CarrierVehicleVerificationService {

    @Autowired
    private CarrierVehicleVerificationRepository carrierVehicleVerificationRepository;

    @Autowired
    private DigilockerVerificationRepository digilockerVerificationRepository;

    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private DocumentVerificationStatusRepository documentVerificationStatusRepository;
    @Autowired
    private RestTemplate restTemplate;

    @Value("${cashfree.verification.app-id}")
    private String cashfreeAppId;

    @Value("${cashfree.verification.secret-key}")
    private String cashfreeSecretKey;

    @Value("${cashfree.verification.base-url}")
    private String cashfreeBaseUrl;

    // ===== CHECK VERIFICATION STATUS =====

    /**
     * Get current verification status for a carrier
     */
    public VehicleVerificationResponse getVerificationStatus(String uid) {
        log.info("Getting verification status for carrier: {}", uid);

        CarrierVehicleVerification verification = carrierVehicleVerificationRepository
                .findByUid(uid)
                .orElse(null);

        VehicleVerificationResponse response = new VehicleVerificationResponse();

        if (verification == null) {
            response.setStatus("NOT_STARTED");
            response.setMessage("No verification initiated yet");
            response.setOverallStatus("PENDING");
            return response;
        }

        // Set overall status
        response.setOverallStatus(verification.getOverallStatus().name());

        // RC Status
        if (verification.getRcStatus() != null) {
            response.setVehicleNumber(verification.getRcVehicleNumber());
            response.setVehicleClass(verification.getRcVehicleClass());
        }

        // DL Status
        if (verification.getDlStatus() != null) {
            response.setDlValidityFrom(verification.getDlValidityFrom());
            response.setDlValidityTo(verification.getDlValidityTo());
            response.setDlVehicleClasses(verification.getDlVehicleClasses());
        }

        response.setAadhaarName(verification.getAadhaarVerifiedName());
        response.setNameMatchWithAadhaar(
                verification.getNameMatchRc() != null ? verification.getNameMatchRc() :
                        verification.getNameMatchDl()
        );

        return response;
    }

    // ===== RC VERIFICATION =====

    /**
     * Verify RC and match with Aadhaar name
     */
    public VehicleVerificationResponse verifyRc(String uid, RcVerificationRequest request) {
        log.info("Verifying RC for carrier: {}, RC Number: {}", uid, request.getRcNumber());

        // 1) Validate RC number format
        String rcNumber = request.getRcNumber().toUpperCase().replaceAll("\\s+", "");
        if (rcNumber.isEmpty()) {
            throw new DocumentVerificationException("RC number cannot be empty");
        }

        // 2) Check if carrier exists and get/create verification record
        CarrierVehicleVerification verification = carrierVehicleVerificationRepository
                .findByUid(uid)
                .orElse(new CarrierVehicleVerification());

        if (verification.getUid() == null) {
            verification.setUid(uid);
        }

        // 3) Check if already verified
        if (verification.getRcStatus() == CarrierVehicleVerification.VerificationStatus.VERIFIED) {
            throw new DocumentVerificationException("RC already verified for this carrier");
        }

        // 4) Get Aadhaar verified name
        String aadhaarName = getAadhaarVerifiedName(uid);
        if (aadhaarName == null) {
            throw new DocumentVerificationException("Please complete Aadhaar verification first before verifying RC");
        }
        verification.setAadhaarVerifiedName(aadhaarName);

        try {
            // 5) Call Cashfree RC Verification API
            String verificationId = generateVerificationId("RC");
            Map<String, Object> rcResponse = verifyRcWithCashfree(rcNumber, verificationId);

            if (rcResponse != null) {
                String status = (String) rcResponse.get("status");

                verification.setRcNumber(rcNumber);
                verification.setRcVerificationId(verificationId);

                Object refIdObj = rcResponse.get("reference_id");
                verification.setRcReferenceId(refIdObj != null ? refIdObj.toString() : null);

                if ("VALID".equals(status)) {
                    // Extract owner name from response
                    String ownerName = (String) rcResponse.get("owner");
                    verification.setRcVerifiedOwnerName(ownerName);
                    verification.setRcVehicleNumber((String) rcResponse.get("reg_no"));
                    verification.setRcVehicleClass((String) rcResponse.get("class"));

                    // 6) Match names with Aadhaar
                    boolean nameMatches = matchNames(aadhaarName, ownerName);
                    verification.setNameMatchRc(nameMatches);

                    if (nameMatches) {
                        verification.setRcStatus(CarrierVehicleVerification.VerificationStatus.VERIFIED);
                        verification.setRcVerifiedAt(LocalDateTime.now());
                        verification.setRcVerificationMessage("RC verified successfully and name matches with Aadhaar");

                        updateOverallStatus(verification);
                        carrierVehicleVerificationRepository.save(verification);

                        // Evict cache
                        evictCarrierCache(uid);

                        VehicleVerificationResponse response = new VehicleVerificationResponse();
                        response.setVerificationType("RC");
                        response.setStatus("VERIFIED");
                        response.setMessage("RC verified successfully. Name matches with Aadhaar.");
                        response.setVerificationId(verificationId);
                        response.setReferenceId(verification.getRcReferenceId());
                        response.setVerifiedName(ownerName);
                        response.setNameMatchWithAadhaar(true);
                        response.setAadhaarName(aadhaarName);
                        response.setVehicleNumber(verification.getRcVehicleNumber());
                        response.setVehicleClass(verification.getRcVehicleClass());
                        response.setOverallStatus(verification.getOverallStatus().name());
                        response.setCanRetry(false);

                        return response;

                    } else {
                        verification.setRcStatus(CarrierVehicleVerification.VerificationStatus.FAILED);
                        verification.setRcVerificationMessage("RC is valid but owner name does not match with Aadhaar");
                        carrierVehicleVerificationRepository.save(verification);

                        VehicleVerificationResponse response = new VehicleVerificationResponse();
                        response.setVerificationType("RC");
                        response.setStatus("NAME_MISMATCH");
                        response.setMessage("RC is valid but owner name does not match with Aadhaar verified name.");
                        response.setVerificationId(verificationId);
                        response.setVerifiedName(ownerName);
                        response.setNameMatchWithAadhaar(false);
                        response.setAadhaarName(aadhaarName);
                        response.setVehicleNumber(verification.getRcVehicleNumber());
                        response.setCanRetry(true);

                        return response;
                    }

                } else {
                    verification.setRcStatus(CarrierVehicleVerification.VerificationStatus.FAILED);
                    verification.setRcVerificationMessage("RC verification failed: Invalid RC");
                    carrierVehicleVerificationRepository.save(verification);

                    VehicleVerificationResponse response = new VehicleVerificationResponse();
                    response.setVerificationType("RC");
                    response.setStatus("FAILED");
                    response.setMessage("RC number is invalid");
                    response.setVerificationId(verificationId);
                    response.setCanRetry(true);

                    return response;
                }
            }

        } catch (DocumentVerificationException e) {
            // Re-throw directly without wrapping
            throw e;
        } catch (Exception e) {
            log.error("RC verification failed for carrier: {}", uid, e);

            verification.setRcStatus(CarrierVehicleVerification.VerificationStatus.FAILED);
            verification.setRcVerificationMessage("RC verification failed: " + e.getMessage());
            carrierVehicleVerificationRepository.save(verification);

            throw new DocumentVerificationException("RC verification failed: " + e.getMessage());
        }

        throw new DocumentVerificationException("RC verification failed. Please try again.");
    }

    // ===== DRIVING LICENSE VERIFICATION =====

    /**
     * Verify Driving License and match with Aadhaar name
     */
    public VehicleVerificationResponse verifyDrivingLicense(String uid, DlVerificationRequest request) {
        log.info("Verifying DL for carrier: {}, DL Number: {}", uid, request.getDlNumber());

        // 1) Sanitize and validate DL number
        String dlNumber = request.getDlNumber().toUpperCase().replaceAll("\\s+", "");

        if (dlNumber.isEmpty()) {
            throw new DocumentVerificationException("Driving License number cannot be empty");
        }

        // Standard Indian DL format: 2 letters + 13 digits = 15 chars (e.g., AP3040YYYYXXXXXXX)
        if (!dlNumber.matches("[A-Z]{2}\\d{13}")) {
            log.warn("DL number format may be invalid: {} (length: {})", dlNumber, dlNumber.length());
            // Warn but don't block — some states have variations; let Cashfree validate
        }

        // 2) Validate DOB is present
        if (request.getDob() == null || request.getDob().isBlank()) {
            throw new DocumentVerificationException("Date of Birth is required for Driving License verification");
        }

        // 3) Get or create verification record
        CarrierVehicleVerification verification = carrierVehicleVerificationRepository
                .findByUid(uid)
                .orElse(new CarrierVehicleVerification());

        if (verification.getUid() == null) {
            verification.setUid(uid);
        }

        // 4) Check if already verified
        if (verification.getDlStatus() == CarrierVehicleVerification.VerificationStatus.VERIFIED) {
            throw new DocumentVerificationException("Driving License already verified for this carrier");
        }

        // 5) Get Aadhaar verified name
        String aadhaarName = getAadhaarVerifiedName(uid);
        if (aadhaarName == null) {
            throw new DocumentVerificationException("Please complete Aadhaar verification first before verifying Driving License");
        }
        verification.setAadhaarVerifiedName(aadhaarName);

        try {
            // 6) Format DOB to DD-MM-YYYY (Cashfree requirement)
            String formattedDob = formatDobForCashfree(request.getDob());
            log.info("DL verification - original DOB: {}, formatted DOB: {}", request.getDob(), formattedDob);

            // 7) Call Cashfree DL Verification API
            String verificationId = generateVerificationId("DL");
            Map<String, Object> dlResponse = verifyDlWithCashfree(dlNumber, formattedDob, verificationId);

            if (dlResponse != null) {
                String status = (String) dlResponse.get("status");

                verification.setDlNumber(dlNumber);
                verification.setDlDob(request.getDob()); // Store original DOB
                verification.setDlVerificationId(verificationId);

                Object refIdObj = dlResponse.get("reference_id");
                verification.setDlReferenceId(refIdObj != null ? refIdObj.toString() : null);

                if ("VALID".equals(status)) {
                    // Extract DL details
                    Map<String, Object> dlDetails = (Map<String, Object>) dlResponse.get("details_of_driving_licence");
                    String dlName = dlDetails != null ? (String) dlDetails.get("name") : null;

                    verification.setDlVerifiedName(dlName);

                    // Extract validity
                    Map<String, Object> dlValidity = (Map<String, Object>) dlResponse.get("dl_validity");
                    if (dlValidity != null) {
                        Map<String, Object> nonTransport = (Map<String, Object>) dlValidity.get("non_transport");
                        if (nonTransport != null) {
                            verification.setDlValidityFrom((String) nonTransport.get("from"));
                            verification.setDlValidityTo((String) nonTransport.get("to"));
                        }
                    }

                    // Extract vehicle classes
                    List<Map<String, Object>> badgeDetails = (List<Map<String, Object>>) dlResponse.get("badge_details");
                    if (badgeDetails != null && !badgeDetails.isEmpty()) {
                        List<String> classes = new ArrayList<>();
                        for (Map<String, Object> badge : badgeDetails) {
                            List<String> classOfVehicle = (List<String>) badge.get("class_of_vehicle");
                            if (classOfVehicle != null) {
                                classes.addAll(classOfVehicle);
                            }
                        }
                        verification.setDlVehicleClasses(String.join(",", classes));
                    }

                    // 8) Match names with Aadhaar
                    boolean nameMatches = matchNames(aadhaarName, dlName);
                    verification.setNameMatchDl(nameMatches);

                    if (nameMatches) {
                        verification.setDlStatus(CarrierVehicleVerification.VerificationStatus.VERIFIED);
                        verification.setDlVerifiedAt(LocalDateTime.now());
                        verification.setDlVerificationMessage("Driving License verified successfully and name matches with Aadhaar");

                        updateOverallStatus(verification);
                        carrierVehicleVerificationRepository.save(verification);

                        // Evict cache
                        evictCarrierCache(uid);

                        VehicleVerificationResponse response = new VehicleVerificationResponse();
                        response.setVerificationType("DL");
                        response.setStatus("VERIFIED");
                        response.setMessage("Driving License verified successfully. Name matches with Aadhaar.");
                        response.setVerificationId(verificationId);
                        response.setReferenceId(verification.getDlReferenceId());
                        response.setVerifiedName(dlName);
                        response.setNameMatchWithAadhaar(true);
                        response.setAadhaarName(aadhaarName);
                        response.setDlValidityFrom(verification.getDlValidityFrom());
                        response.setDlValidityTo(verification.getDlValidityTo());
                        response.setDlVehicleClasses(verification.getDlVehicleClasses());
                        response.setOverallStatus(verification.getOverallStatus().name());
                        response.setCanRetry(false);

                        return response;

                    } else {
                        verification.setDlStatus(CarrierVehicleVerification.VerificationStatus.FAILED);
                        verification.setDlVerificationMessage("DL is valid but name does not match with Aadhaar");
                        carrierVehicleVerificationRepository.save(verification);

                        VehicleVerificationResponse response = new VehicleVerificationResponse();
                        response.setVerificationType("DL");
                        response.setStatus("NAME_MISMATCH");
                        response.setMessage("Driving License is valid but name does not match with Aadhaar verified name.");
                        response.setVerificationId(verificationId);
                        response.setVerifiedName(dlName);
                        response.setNameMatchWithAadhaar(false);
                        response.setAadhaarName(aadhaarName);
                        response.setDlValidityFrom(verification.getDlValidityFrom());
                        response.setDlValidityTo(verification.getDlValidityTo());
                        response.setCanRetry(true);

                        return response;
                    }

                } else {
                    // Cashfree returned a non-VALID status (e.g., INVALID)
                    verification.setDlStatus(CarrierVehicleVerification.VerificationStatus.FAILED);
                    verification.setDlVerificationMessage("DL verification failed: Invalid DL or DOB");
                    carrierVehicleVerificationRepository.save(verification);

                    VehicleVerificationResponse response = new VehicleVerificationResponse();
                    response.setVerificationType("DL");
                    response.setStatus("FAILED");
                    response.setMessage("Driving License number or Date of Birth is invalid. Please check and try again.");
                    response.setVerificationId(verificationId);
                    response.setCanRetry(true);

                    return response;
                }
            }

            // dlResponse was null — API call failed silently
            verification.setDlStatus(CarrierVehicleVerification.VerificationStatus.FAILED);
            verification.setDlVerificationMessage("DL verification failed: No response from verification service");
            carrierVehicleVerificationRepository.save(verification);

            throw new DocumentVerificationException("Driving License verification failed. Please try again.");

        } catch (DocumentVerificationException e) {
            // Re-throw directly without wrapping
            throw e;
        } catch (Exception e) {
            log.error("DL verification failed for carrier: {}", uid, e);

            verification.setDlStatus(CarrierVehicleVerification.VerificationStatus.FAILED);
            verification.setDlVerificationMessage("DL verification failed: " + e.getMessage());
            carrierVehicleVerificationRepository.save(verification);

            throw new DocumentVerificationException("Driving License verification failed: " + e.getMessage());
        }
    }

    // ===== CASHFREE API INTEGRATION =====

    /**
     * Call Cashfree RC Verification API (POST with vehicle_number in request body)
     */
    private Map<String, Object> verifyRcWithCashfree(String rcNumber, String verificationId) {
        HttpHeaders headers = createCashfreeHeaders();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("verification_id", verificationId);
        requestBody.put("vehicle_number", rcNumber);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            String endpoint = cashfreeBaseUrl + "/verification/vehicle-rc";
            log.info("Calling Cashfree RC Verification API: {}", endpoint);
            log.debug("Request body: {}", requestBody);

            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            log.info("Cashfree RC Response Status: {}", response.getStatusCode());
            log.debug("Cashfree RC Response Body: {}", response.getBody());
            return response.getBody();

        } catch (HttpClientErrorException e) {
            // 4xx errors — bad request, unauthorized, etc.
            log.error("Cashfree RC API client error. Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new DocumentVerificationException("RC verification service authentication failed. Please contact support.");
            }
            return null;

        } catch (HttpServerErrorException e) {
            // 5xx errors — Cashfree / upstream government API is down
            log.error("Cashfree RC API server error. Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 502 || e.getStatusCode().value() == 503) {
                throw new DocumentVerificationException(
                        "RC verification service is temporarily unavailable (government API is down). Please try again in a few minutes."
                );
            }
            throw new DocumentVerificationException(
                    "RC verification service error. Please try again later."
            );

        } catch (Exception e) {
            log.error("Error calling Cashfree RC API", e);
            return null;
        }
    }

    /**
     * Call Cashfree DL Verification API
     * NOTE: dob must already be in DD-MM-YYYY format before calling this method
     */
    private Map<String, Object> verifyDlWithCashfree(String dlNumber, String dob, String verificationId) {
        HttpHeaders headers = createCashfreeHeaders();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("verification_id", verificationId);
        requestBody.put("dl_number", dlNumber);
        requestBody.put("dob", dob); // Must be DD-MM-YYYY

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            String endpoint = cashfreeBaseUrl + "/verification/driving-license";
            log.info("Calling Cashfree DL Verification API: {}", endpoint);
            log.info("DL Request - dl_number: {}, dob: {}, verification_id: {}", dlNumber, dob, verificationId);

            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            log.info("Cashfree DL Response Status: {}", response.getStatusCode());
            log.debug("Cashfree DL Response Body: {}", response.getBody());
            return response.getBody();

        } catch (HttpClientErrorException e) {
            // 4xx errors — bad request, unauthorized, etc.
            log.error("Cashfree DL API client error. Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new DocumentVerificationException("DL verification service authentication failed. Please contact support.");
            }

            // 400 usually means invalid DL number or DOB — surface clearly
            if (e.getStatusCode().value() == 400) {
                log.error("Bad request to Cashfree DL API — likely invalid DL number format or DOB. DL: {}, DOB: {}", dlNumber, dob);
                return null; // Let the caller handle as FAILED
            }

            return null;

        } catch (HttpServerErrorException e) {
            // 5xx errors — Cashfree / upstream Sarathi government API is down
            log.error("Cashfree DL API server error. Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 502 || e.getStatusCode().value() == 503) {
                throw new DocumentVerificationException(
                        "Driving License verification service is temporarily unavailable (government API is down). Please try again in a few minutes."
                );
            }

            throw new DocumentVerificationException(
                    "Driving License verification service error. Please try again later."
            );

        } catch (Exception e) {
            log.error("Error calling Cashfree DL API", e);
            return null;
        }
    }

    // ===== HELPER METHODS =====

    private HttpHeaders createCashfreeHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", cashfreeAppId);
        headers.set("x-client-secret", cashfreeSecretKey);
        return headers;
    }

    private String generateVerificationId(String type) {
        return type + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Normalize DOB to YYYY-MM-DD format as required by Cashfree.
     * Handles:
     *   YYYY-MM-DD  → YYYY-MM-DD  (already correct, pass through)
     *   DD-MM-YYYY  → YYYY-MM-DD  (reverse dash-separated)
     *   DD/MM/YYYY  → YYYY-MM-DD  (slash-separated Indian format)
     *   YYYY/MM/DD  → YYYY-MM-DD  (slash-separated ISO)
     */
    private String formatDobForCashfree(String dob) {
        if (dob == null || dob.isBlank()) {
            return dob;
        }

        String trimmed = dob.trim();

        // Already in YYYY-MM-DD (Cashfree required format)
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return trimmed;
        }

        // DD-MM-YYYY → YYYY-MM-DD
        if (trimmed.matches("\\d{2}-\\d{2}-\\d{4}")) {
            String[] parts = trimmed.split("-");
            return parts[2] + "-" + parts[1] + "-" + parts[0];
        }

        // DD/MM/YYYY → YYYY-MM-DD
        if (trimmed.matches("\\d{2}/\\d{2}/\\d{4}")) {
            String[] parts = trimmed.split("/");
            return parts[2] + "-" + parts[1] + "-" + parts[0];
        }

        // YYYY/MM/DD → YYYY-MM-DD
        if (trimmed.matches("\\d{4}/\\d{2}/\\d{2}")) {
            return trimmed.replace("/", "-");
        }

        // Unknown format — log warning and return as-is
        log.warn("Unrecognized DOB format: '{}'. Sending as-is to Cashfree.", trimmed);
        return trimmed;
    }

    /**
     * Get Aadhaar verified name for the carrier
     */
    private String getAadhaarVerifiedName(String uid) {
        return documentVerificationStatusRepository
                .findByUserIdAndDocumentType(uid, OtpVerification.DocumentType.AADHAAR)
                .filter(s -> s.getStatus() == DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED)
                .map(DocumentVerificationStatus::getVerifiedName)
                .orElse(null);
    }

    /**
     * Match two names - normalize and compare
     */
    private boolean matchNames(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return false;
        }

        String normalized1 = normalizeName(name1);
        String normalized2 = normalizeName(name2);

        // Exact match after normalization
        if (normalized1.equals(normalized2)) {
            return true;
        }

        // One name contains the other (handles partial names)
        if (normalized1.contains(normalized2) || normalized2.contains(normalized1)) {
            return true;
        }

        // Fuzzy similarity match (>80% similar)
        return calculateSimilarity(normalized1, normalized2) > 0.8;
    }

    private String normalizeName(String name) {
        return name.toUpperCase()
                .replaceAll("\\bMR\\.?\\b|\\bMRS\\.?\\b|\\bMS\\.?\\b|\\bDR\\.?\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double calculateSimilarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) {
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 1.0;
        }
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }

    private int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }

    /**
     * Update overall verification status based on RC and DL statuses
     */
    private void updateOverallStatus(CarrierVehicleVerification verification) {
        boolean rcVerified = verification.getRcStatus() == CarrierVehicleVerification.VerificationStatus.VERIFIED;
        boolean dlVerified = verification.getDlStatus() == CarrierVehicleVerification.VerificationStatus.VERIFIED;

        if (rcVerified && dlVerified) {
            verification.setOverallStatus(CarrierVehicleVerification.OverallVerificationStatus.BOTH_VERIFIED);
        } else if (rcVerified) {
            verification.setOverallStatus(CarrierVehicleVerification.OverallVerificationStatus.RC_VERIFIED);
        } else if (dlVerified) {
            verification.setOverallStatus(CarrierVehicleVerification.OverallVerificationStatus.DL_VERIFIED);
        } else {
            verification.setOverallStatus(CarrierVehicleVerification.OverallVerificationStatus.PENDING);
        }
    }

    /**
     * Evict carrier cache after successful verification
     */
    private void evictCarrierCache(String uid) {
        try {
            if (cacheManager.getCache("carrier-verification") != null) {
                cacheManager.getCache("carrier-verification").evict(uid);
            }
            log.info("Cache evicted for carrier: {}", uid);
        } catch (Exception e) {
            log.error("Failed to evict cache for carrier: {}", uid, e);
        }
    }
}