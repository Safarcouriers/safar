package com.saffaricarrers.saffaricarrers.N1.webhook;

import com.razorpay.Utils;
import com.saffaricarrers.saffaricarrers.N1.config.N1RazorpayConfig;
import com.saffaricarrers.saffaricarrers.N1.model.N1ProcessedWebhookEvent;
import com.saffaricarrers.saffaricarrers.N1.repository.N1ProcessedWebhookEventRepository;
import com.saffaricarrers.saffaricarrers.N1.service.N1SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/n1/webhook")
@RequiredArgsConstructor
@Slf4j
public class N1RazorpayWebhookController {
    private final N1RazorpayConfig config;
    private final N1SubscriptionService n1SubscriptionService;
    private final N1ProcessedWebhookEventRepository events;

    @PostMapping("/razorpay")
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader(value="X-Razorpay-Signature", required=false) String signature) {
        if (config.getWebhookSecret() == null || config.getWebhookSecret().isBlank())
            return ResponseEntity.status(500).body("Webhook secret is not configured");
        if (signature == null || !verify(payload, signature)) return ResponseEntity.status(401).body("Invalid signature");
        try {
            JSONObject root = new JSONObject(payload);
            String eventId = root.optString("id", "");
            String type = root.optString("event", "");
            if (eventId.isBlank()) return ResponseEntity.badRequest().body("Missing event id");
            if (events.existsByEventId(eventId)) return ResponseEntity.ok("OK");

            String orderId = "", paymentId = "";
            if (root.has("payload")) {
                JSONObject payloadObj = root.getJSONObject("payload");
                JSONObject paymentEntity = payloadObj.optJSONObject("payment") != null
                        ? payloadObj.getJSONObject("payment").optJSONObject("entity") : null;
                JSONObject orderEntity = payloadObj.optJSONObject("order") != null
                        ? payloadObj.getJSONObject("order").optJSONObject("entity") : null;
                if (paymentEntity != null) { paymentId = paymentEntity.optString("id", ""); orderId = paymentEntity.optString("order_id", ""); }
                if (orderEntity != null && orderId.isBlank()) orderId = orderEntity.optString("id", "");
            }

            if ("payment.captured".equals(type) || "order.paid".equals(type)) {
                if (!orderId.isBlank() && !paymentId.isBlank()) n1SubscriptionService.recoverPayment(orderId, paymentId);
            }

            // Mark only after successful business processing. If Razorpay/API is temporarily down,
            // return 500 so Razorpay retries instead of losing the event.
            events.save(N1ProcessedWebhookEvent.builder().eventId(eventId).eventType(type)
                    .orderId(orderId).paymentId(paymentId).build());
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("N1 webhook processing failed", e);
            return ResponseEntity.status(500).body("Retry");
        }
    }

    private boolean verify(String payload, String signature) {
        try { Utils.verifyWebhookSignature(payload, signature, config.getWebhookSecret()); return true; }
        catch (Exception e) { return false; }
    }
}
