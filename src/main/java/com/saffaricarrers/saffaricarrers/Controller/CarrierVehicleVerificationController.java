package com.saffaricarrers.saffaricarrers.Controller;


import com.saffaricarrers.saffaricarrers.Dtos.DlVerificationRequest;
import com.saffaricarrers.saffaricarrers.Dtos.RcVerificationRequest;
import com.saffaricarrers.saffaricarrers.Responses.VehicleVerificationResponse;
import com.saffaricarrers.saffaricarrers.Services.CarrierVehicleVerificationService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carrier-verification")
@Slf4j
public class CarrierVehicleVerificationController {

    @Autowired
    private CarrierVehicleVerificationService carrierVehicleVerificationService;

    /**
     * Get current verification status for carrier
     * GET /api/carrier-verification/{uid}/status
     */
    @GetMapping("/{uid}/status")
    public ResponseEntity<VehicleVerificationResponse> getVerificationStatus(@PathVariable String uid) {
        log.info("GET /api/carrier-verification/{}/status", uid);

        VehicleVerificationResponse response = carrierVehicleVerificationService.getVerificationStatus(uid);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify RC for carrier
     * POST /api/carrier-verification/{uid}/verify-rc
     */
    @PostMapping("/{uid}/verify-rc")
    public ResponseEntity<VehicleVerificationResponse> verifyRc(
            @PathVariable String uid,
            @RequestBody RcVerificationRequest request) {

        log.info("POST /api/carrier-verification/{}/verify-rc - RC Number: {}", uid, request.getRcNumber());

        VehicleVerificationResponse response = carrierVehicleVerificationService.verifyRc(uid, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify Driving License for carrier
     * POST /api/carrier-verification/{uid}/verify-dl
     */
    @PostMapping("/{uid}/verify-dl")
    public ResponseEntity<VehicleVerificationResponse> verifyDrivingLicense(
            @PathVariable String uid,
            @RequestBody DlVerificationRequest request) {

        log.info("POST /api/carrier-verification/{}/verify-dl - DL Number: {}, DOB: {}",
                uid, request.getDlNumber(), request.getDob());

        VehicleVerificationResponse response = carrierVehicleVerificationService.verifyDrivingLicense(uid, request);
        return ResponseEntity.ok(response);
    }
}