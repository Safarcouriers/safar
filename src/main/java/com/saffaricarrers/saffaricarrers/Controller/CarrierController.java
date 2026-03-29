package com.saffaricarrers.saffaricarrers.Controller;

import com.saffaricarrers.saffaricarrers.Dtos.BankDetailsDto;
import com.saffaricarrers.saffaricarrers.Dtos.CarrierProfileDto;
import com.saffaricarrers.saffaricarrers.Entity.CarrierProfile;
import com.saffaricarrers.saffaricarrers.Responses.ApiResponse1;
import com.saffaricarrers.saffaricarrers.Services.CarrierService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/carrier")
@RequiredArgsConstructor
public class CarrierController {

    private final CarrierService carrierService;

    /**
     * Register existing user as carrier
     * POST /api/carrier/register/{userId}
     */


    @GetMapping("/profile/{userId}")
    public ResponseEntity<CarrierProfileDto> getCarrierProfile(@PathVariable String userId) {
        try {
            CarrierProfileDto carrierProfile = carrierService.getCarrierProfile(userId);
            return ResponseEntity.ok(carrierProfile);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Check if user has carrier profile
     * GET /api/carrier/exists/{userId}
     */
    @GetMapping("/exists/{userId}")
    public ResponseEntity<Boolean> hasCarrierProfile(@PathVariable String userId) {
        boolean hasProfile = carrierService.hasCarrierProfile(userId);
        return ResponseEntity.ok(hasProfile);
    }

    /**
     * Add bank details to carrier profile
     * POST /api/carrier/bank-details/{userId}
     */
    @PostMapping("/register/{userId}")
    public ResponseEntity<ApiResponse1<CarrierProfileDto>> registerAsCarrier(@PathVariable String userId) {
        ApiResponse1<CarrierProfileDto> response = carrierService.registerAsCarrier(userId);

        HttpStatus status = response.getStatus().equals("success")
                ? HttpStatus.CREATED
                : HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(response);
    }

//    @PostMapping("/bank-details/{userId}")
//    public ResponseEntity<ApiResponse1<CarrierProfileDto>> addBankDetails(
//            @PathVariable String userId,
//            @RequestBody @Valid BankDetailsDto bankDetailsDto) {
//
//        ApiResponse1<CarrierProfileDto> response = carrierService.addBankDetails(userId, bankDetailsDto);
//
//        HttpStatus status = response.getStatus().equals("success")
//                ? HttpStatus.OK
//                : HttpStatus.BAD_REQUEST;
//
//        return ResponseEntity.status(status).body(response);
//    }



    /**
     * Update bank details
     * PUT /api/carrier/bank-details/{userId}
     */
//    @PutMapping("/bank-details/{userId}")
//    public ResponseEntity<CarrierProfileDto> updateBankDetails(
//            @PathVariable String userId,
//            @Valid @RequestBody BankDetailsDto bankDetailsDto) {
//        try {
//            CarrierProfileDto carrierProfile = carrierService.updateBankDetails(userId, bankDetailsDto);
//            return ResponseEntity.ok(carrierProfile);
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().build();
//        }
//    }

    /**
     * Check if carrier is active
     * GET /api/carrier/status/{userId}
     */
    @GetMapping("/status/{userId}")
    public ResponseEntity<Boolean> isCarrierActive(@PathVariable String userId) {
        boolean isActive = carrierService.isCarrierActive(userId);
        return ResponseEntity.ok(isActive);
    }

    /**
     * Activate carrier profile (Admin endpoint)
     * POST /api/carrier/activate/{userId}
     */
    @PostMapping("/activate/{userId}")
    public ResponseEntity<CarrierProfileDto> activateCarrier(@PathVariable String userId) {
        try {
            CarrierProfileDto carrierProfile = carrierService.activateCarrier(userId);
            return ResponseEntity.ok(carrierProfile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deactivate carrier profile
     * POST /api/carrier/deactivate/{userId}
     */
    @PostMapping("/deactivate/{userId}")
    public ResponseEntity<CarrierProfileDto> deactivateCarrier(@PathVariable String userId) {
        try {
            CarrierProfileDto carrierProfile = carrierService.deactivateCarrier(userId);
            return ResponseEntity.ok(carrierProfile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
