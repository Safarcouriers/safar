package com.saffaricarrers.saffaricarrers.Controller;

import com.saffaricarrers.saffaricarrers.Entity.DigilockerVerification;
import com.saffaricarrers.saffaricarrers.Repository.DigilockerVerificationRepository;
import com.saffaricarrers.saffaricarrers.Services.DocumentVerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@Slf4j
public class DigilockerWebhookController {

//    @Autowired
//    private DigilockerVerificationRepository digilockerVerificationRepository;
//
//    @Autowired
//    private DocumentVerificationService documentVerificationService;
//
//    // ✅ NEW: Cashfree redirects here after user completes DigiLocker
//    @GetMapping("/digilocker/redirect")
//    public ResponseEntity<Void> handleDigilockerRedirect(
//            @RequestParam(required = false) String verification_id,
//            @RequestParam(required = false) String status) {
//
//        log.info("DigiLocker redirect received: verificationId={}, status={}", verification_id, status);
//
//        // 302 redirect → opens app via deep link
//        String appDeepLink = "safar-couriers://digilocker-return?verificationId=" + verification_id;
//
//        return ResponseEntity.status(HttpStatus.FOUND)
//                .location(URI.create(appDeepLink))
//                .build();
//    }
//
//    // ✅ EXISTING: Cashfree webhook for status updates
//    @PostMapping("/digilocker/callback")
//    public ResponseEntity<Map<String, String>> handleDigilockerWebhook(
//            @RequestBody Map<String, Object> payload) {
//
//        log.info("Received DigiLocker webhook: {}", payload);
//
//        try {
//            String verificationId = (String) payload.get("verification_id");
//            String status = (String) payload.get("status");
//
//            if (verificationId == null || status == null) {
//                return ResponseEntity.ok(Map.of("status", "ignored"));
//            }
//
//            DigilockerVerification verification = digilockerVerificationRepository
//                    .findByVerificationId(verificationId)
//                    .orElse(null);
//
//            if (verification == null) {
//                log.warn("Verification ID not found: {}", verificationId);
//                return ResponseEntity.ok(Map.of("status", "not_found"));
//            }
//
//            switch (status) {
//                case "AUTHENTICATED":
//                    verification.setStatus(DigilockerVerification.DigilockerStatus.AUTHENTICATED);
//                    digilockerVerificationRepository.save(verification);
//                    log.info("Webhook AUTHENTICATED - fetching document for user: {}", verification.getUserId());
//                    try {
//                        documentVerificationService.fetchAadhaarFromDigilocker(
//                                verification.getUserId(), verificationId);
//                        log.info("Document fetched successfully via webhook for user: {}", verification.getUserId());
//                    } catch (Exception e) {
//                        log.error("Failed to fetch document after webhook for user: {}", verification.getUserId(), e);
//                    }
//                    break;
//
//                case "EXPIRED":
//                    verification.setStatus(DigilockerVerification.DigilockerStatus.EXPIRED);
//                    digilockerVerificationRepository.save(verification);
//                    log.info("DigiLocker EXPIRED: {}", verificationId);
//                    break;
//
//                case "CONSENT_DENIED":
//                    verification.setStatus(DigilockerVerification.DigilockerStatus.CONSENT_DENIED);
//                    digilockerVerificationRepository.save(verification);
//                    log.info("DigiLocker CONSENT_DENIED: {}", verificationId);
//                    break;
//
//                case "PENDING":
//                    verification.setStatus(DigilockerVerification.DigilockerStatus.PENDING);
//                    digilockerVerificationRepository.save(verification);
//                    break;
//            }
//
//            return ResponseEntity.ok(Map.of("status", "success"));
//
//        } catch (Exception e) {
//            log.error("Error processing DigiLocker webhook", e);
//            return ResponseEntity.status(500)
//                    .body(Map.of("status", "error", "message", "Failed to process webhook"));
//        }
//    }
}
