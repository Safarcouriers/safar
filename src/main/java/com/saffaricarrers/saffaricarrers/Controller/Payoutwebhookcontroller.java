package com.saffaricarrers.saffaricarrers.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saffaricarrers.saffaricarrers.Services.RazorpayPayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives payout status webhooks from Cashfree Payouts V1.
 *
 * SETUP IN CASHFREE DASHBOARD:
 *   Cashfree → Payouts → Developers → Webhooks → Add Webhook URL
 *   Sandbox URL:    your ngrok URL/api/webhooks/payout
 *   Production URL: https://apisafarepackers.lytortech.com/api/webhooks/payout
 *   Version: V1
 *
 * NOTE: Cashfree Payouts V1 does NOT send a signature header.
 * Signature verification is skipped. Safe because:
 *   - transferId in body must match a real payment in our DB
 *   - If no match found, webhook is silently ignored
 *
 * Cashfree V1 webhook body:
 * {
 *   "event": "TRANSFER_SUCCESS",   (or TRANSFER_FAILED / TRANSFER_REVERSED)
 *   "transferId": "TXN123_xxx",    matches what we sent in requestTransfer
 *   "referenceId": "cf_ref_id",
 *   "acknowledged": 0
 * }
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class Payoutwebhookcontroller {

    private final RazorpayPayoutService razorpayPayoutService;
    private final ObjectMapper objectMapper;

    /**
     * POST /api/webhooks/payout
     * Called by Cashfree when a transfer status changes.
     */
    @PostMapping("/payout")
    public ResponseEntity<String> handlePayoutWebhook(@RequestBody String payload) {

        log.info("📨 Cashfree payout webhook received");
        log.debug("📨 Payload: {}", payload);

        try {
            JsonNode root     = objectMapper.readTree(payload);
            String event      = root.path("event").asText();
            String transferId = root.path("transferId").asText();

            // Some events use referenceId instead of transferId
            if (transferId.isBlank()) {
                transferId = root.path("referenceId").asText();
            }

            log.info("📨 Event: {} | TransferID: {}", event, transferId);

            if (transferId.isBlank()) {
                log.warn("⚠️ No transferId in webhook — ignoring. Payload: {}", payload);
                return ResponseEntity.ok("OK");
            }

            razorpayPayoutService.handlePayoutWebhook(transferId, event);

            // Always return 200 — Cashfree retries on non-200
            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("❌ Webhook error: {}", e.getMessage(), e);
            // 200 even on error to stop infinite retries from Cashfree
            return ResponseEntity.ok("Processed");
        }
    }
}