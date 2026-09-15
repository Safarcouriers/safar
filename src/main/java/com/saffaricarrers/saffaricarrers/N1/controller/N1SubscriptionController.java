package com.saffaricarrers.saffaricarrers.N1.controller;

import com.saffaricarrers.saffaricarrers.N1.dto.N1SubscriptionDto;
import com.saffaricarrers.saffaricarrers.N1.service.N1FirebaseAuthService;
import com.saffaricarrers.saffaricarrers.N1.service.N1SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/n1/subscription")
@RequiredArgsConstructor
public class N1SubscriptionController {
    private final N1SubscriptionService service;
    private final N1FirebaseAuthService auth;

    @PostMapping("/purchase/create")
    public ResponseEntity<?> create(@RequestHeader(value="Authorization", required=false) String authorization,
                                    @Valid @RequestBody N1SubscriptionDto.CreatePurchaseOrderRequest request) {
        try { if (!auth.verify(authorization).equals(request.getUserId().trim())) return ResponseEntity.status(403).body(N1SubscriptionDto.ApiResponse.error("Account verification failed.")); }
        catch (SecurityException e) { return ResponseEntity.status(401).body(N1SubscriptionDto.ApiResponse.error(e.getMessage())); }
        return ResponseEntity.ok(service.createPurchaseOrder(request));
    }

    @PostMapping("/purchase/confirm")
    public ResponseEntity<?> confirm(@RequestHeader(value="Authorization", required=false) String authorization,
                                     @Valid @RequestBody N1SubscriptionDto.ConfirmPurchaseRequest request) {
        try { if (!auth.verify(authorization).equals(request.getUserId().trim())) return ResponseEntity.status(403).body(N1SubscriptionDto.ApiResponse.error("Account verification failed.")); }
        catch (SecurityException e) { return ResponseEntity.status(401).body(N1SubscriptionDto.ApiResponse.error(e.getMessage())); }
        return ResponseEntity.ok(service.confirmPurchase(request));
    }

    @GetMapping("/status/{userId}")
    public ResponseEntity<?> status(@RequestHeader(value="Authorization", required=false) String authorization,
                                    @PathVariable String userId) {
        try { if (!auth.verify(authorization).equals(userId.trim())) return ResponseEntity.status(403).body(N1SubscriptionDto.ApiResponse.error("Account verification failed.")); }
        catch (SecurityException e) { return ResponseEntity.status(401).body(N1SubscriptionDto.ApiResponse.error(e.getMessage())); }
        return ResponseEntity.ok(service.getSubscriptionStatus(userId));
    }

    @GetMapping("/health")
    public ResponseEntity<N1SubscriptionDto.ApiResponse> health() { return ResponseEntity.ok(N1SubscriptionDto.ApiResponse.ok("N1 one-time payment service running")); }
}
