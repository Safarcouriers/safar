package com.saffaricarrers.saffaricarrers.Controller;

import com.saffaricarrers.saffaricarrers.Dtos.BankDetailsDto;
import com.saffaricarrers.saffaricarrers.Responses.ApiResponse1;
import com.saffaricarrers.saffaricarrers.Services.Bankdetailsservice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
@Slf4j
public class Bankdetailscontroller {

    private final Bankdetailsservice bankDetailsService;

    // =====================================================================
    // CARRIER ENDPOINTS
    // =====================================================================

    /**
     * Submit bank details (first time or re-submission after rejection)
     * POST /api/bank/submit
     */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse1<BankDetailsDto.Response>> submitBankDetails(
            @RequestHeader("userId") String userId,
            @RequestBody BankDetailsDto.SubmitRequest request) {
        try {
            BankDetailsDto.Response response = bankDetailsService.submitBankDetails(userId, request);
            return ResponseEntity.ok(ApiResponse1.success(
                    "Bank details submitted successfully. Verification takes 1–2 business days.", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse1.error(e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Submit bank details error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse1.error("Failed to submit bank details."));
        }
    }

    /**
     * Get own bank details (masked)
     * GET /api/bank/my-details
     */
    @GetMapping("/my-details")
    public ResponseEntity<ApiResponse1<BankDetailsDto.Response>> getMyBankDetails(
            @RequestHeader("userId") String userId) {
        try {
            BankDetailsDto.Response response = bankDetailsService.getBankDetails(userId);
            if (response == null) {
                return ResponseEntity.ok(ApiResponse1.error("No bank details found. Please submit your details."));
            }
            return ResponseEntity.ok(ApiResponse1.success("Bank details fetched successfully.", response));
        } catch (Exception e) {
            log.error("❌ Get bank details error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse1.error("Failed to fetch bank details."));
        }
    }

    /**
     * Get bank verification status summary
     * GET /api/bank/status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse1<Map<String, Object>>> getBankStatus(
            @RequestHeader("userId") String userId) {
        try {
            Map<String, Object> status = bankDetailsService.getBankStatus(userId);
            return ResponseEntity.ok(ApiResponse1.success("Bank status fetched.", status));
        } catch (Exception e) {
            log.error("❌ Get bank status error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse1.error("Failed to fetch bank status."));
        }
    }

    /**
     * IFSC code lookup — auto-fill bank + branch name
     * GET /api/bank/ifsc/{ifscCode}
     */
    @GetMapping("/ifsc/{ifscCode}")
    public ResponseEntity<ApiResponse1<BankDetailsDto.IfscInfo>> lookupIfsc(
            @PathVariable String ifscCode) {
        try {
            BankDetailsDto.IfscInfo info = bankDetailsService.lookupIfsc(ifscCode.toUpperCase());
            if (Boolean.TRUE.equals(info.getFound())) {
                return ResponseEntity.ok(ApiResponse1.success("IFSC found.", info));
            } else {
                return ResponseEntity.ok(ApiResponse1.error(info.getErrorMessage()));
            }
        } catch (Exception e) {
            log.error("❌ IFSC lookup error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse1.error("IFSC lookup failed."));
        }
    }

    // =====================================================================
    // ADMIN ENDPOINTS
    // =====================================================================

    /**
     * Admin: Get all pending bank verifications
     * GET /api/bank/admin/pending
     */
    @GetMapping("/admin/pending")
    public ResponseEntity<ApiResponse1<List<BankDetailsDto.AdminResponse>>> getPendingVerifications(
            @RequestHeader("adminId") String adminId) {
        try {
            List<BankDetailsDto.AdminResponse> list = bankDetailsService.getPendingVerifications();
            return ResponseEntity.ok(ApiResponse1.success(
                    "Found " + list.size() + " pending verification(s).", list));
        } catch (Exception e) {
            log.error("❌ Get pending verifications error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse1.error("Failed to fetch pending list."));
        }
    }

    /**
     * Admin: Get all bank details
     * GET /api/bank/admin/all
     */
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse1<List<BankDetailsDto.AdminResponse>>> getAllBankDetails(
            @RequestHeader("adminId") String adminId) {
        try {
            List<BankDetailsDto.AdminResponse> list = bankDetailsService.getAllBankDetails();
            return ResponseEntity.ok(ApiResponse1.success("Fetched all bank details.", list));
        } catch (Exception e) {
            log.error("❌ Get all bank details error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse1.error("Failed to fetch list."));
        }
    }

    /**
     * Admin: Get single bank detail
     * GET /api/bank/admin/{bankId}
     */
    @GetMapping("/admin/{bankId}")
    public ResponseEntity<ApiResponse1<BankDetailsDto.AdminResponse>> getAdminBankDetail(
            @PathVariable Long bankId,
            @RequestHeader("adminId") String adminId) {
        try {
            BankDetailsDto.AdminResponse detail = bankDetailsService.getAdminBankDetails(bankId);
            return ResponseEntity.ok(ApiResponse1.success("Fetched.", detail));
        } catch (Exception e) {
            log.error("❌ Get admin bank detail error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse1.error("Failed to fetch detail."));
        }
    }

    /**
     * Admin: Verify or reject bank details
     * PATCH /api/bank/admin/{bankId}/verify
     */
    @PatchMapping("/admin/{bankId}/verify")
    public ResponseEntity<ApiResponse1<BankDetailsDto.Response>> verifyBankDetails(
            @PathVariable Long bankId,
            @RequestHeader("adminId") String adminId,
            @RequestBody BankDetailsDto.VerifyRequest request) {
        try {
            BankDetailsDto.Response response = bankDetailsService.adminVerifyBankDetails(
                    bankId, adminId, request);
            String msg = "APPROVE".equalsIgnoreCase(request.getAction())
                    ? "Bank details approved. Carrier is now active."
                    : "Bank details rejected. Carrier has been notified.";
            return ResponseEntity.ok(ApiResponse1.success(msg, response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse1.error(e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Admin verify error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse1.error("Verification failed."));
        }
    }
}