package com.saffaricarrers.saffaricarrers.N1.controller;

import com.saffaricarrers.saffaricarrers.N1.dto.N1InfluencerDto;
import com.saffaricarrers.saffaricarrers.N1.model.N1Influencer;
import com.saffaricarrers.saffaricarrers.N1.service.N1FirebaseAuthService;
import com.saffaricarrers.saffaricarrers.N1.service.N1InfluencerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/n1/influencer")
@RequiredArgsConstructor
public class N1InfluencerController {

    private final N1InfluencerService influencerService;
    private final N1FirebaseAuthService auth;

    // ============================================================
    // CREATE INFLUENCER
    // ============================================================

    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,

            @Valid
            @RequestBody
            N1InfluencerDto.CreateRequest request
    ) {

        /*
         * IMPORTANT:
         * Protect this endpoint with your ADMIN authentication.
         *
         * For now this verifies Firebase authentication.
         * You should add your actual admin-role check here.
         */

        try {

            auth.verify(authorization);

            N1Influencer influencer =
                    influencerService.createInfluencer(
                            request.getName(),
                            request.getCode(),
                            request.getEmail(),
                            request.getCommissionPercent()
                    );

            return ResponseEntity.ok(
                    N1InfluencerDto.ApiResponse.success(
                            "Influencer created.",
                            influencer
                    )
            );

        } catch (SecurityException e) {

            return ResponseEntity
                    .status(401)
                    .body(
                            N1InfluencerDto.ApiResponse.error(
                                    e.getMessage()
                            )
                    );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            N1InfluencerDto.ApiResponse.error(
                                    e.getMessage()
                            )
                    );
        }
    }

    // ============================================================
    // ATTRIBUTE CURRENT USER
    // ============================================================

    @PostMapping("/attribute")
    public ResponseEntity<?> attribute(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,

            @Valid
            @RequestBody
            N1InfluencerDto.AttributeRequest request
    ) {

        try {

            String userId = auth.verify(authorization);

            influencerService.attributeUser(
                    userId,
                    request.getInfluencerCode(),
                    request.getSource()
            );

            return ResponseEntity.ok(
                    N1InfluencerDto.ApiResponse.success(
                            "Influencer attribution saved.",
                            null
                    )
            );

        } catch (SecurityException e) {

            return ResponseEntity
                    .status(401)
                    .body(
                            N1InfluencerDto.ApiResponse.error(
                                    e.getMessage()
                            )
                    );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            N1InfluencerDto.ApiResponse.error(
                                    e.getMessage()
                            )
                    );
        }
    }

    // ============================================================
    // STATS
    // ============================================================

    @GetMapping("/{code}/stats")
    public ResponseEntity<?> stats(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,

            @PathVariable String code
    ) {

        try {

            auth.verify(authorization);

            return ResponseEntity.ok(
                    N1InfluencerDto.ApiResponse.success(
                            "Influencer statistics.",
                            influencerService.getStats(code)
                    )
            );

        } catch (SecurityException e) {

            return ResponseEntity
                    .status(401)
                    .body(
                            N1InfluencerDto.ApiResponse.error(
                                    e.getMessage()
                            )
                    );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            N1InfluencerDto.ApiResponse.error(
                                    e.getMessage()
                            )
                    );
        }
    }
}