package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.BankDetailsDto;
import com.saffaricarrers.saffaricarrers.Dtos.CarrierProfileDto;
import com.saffaricarrers.saffaricarrers.Entity.BankDetails;
import com.saffaricarrers.saffaricarrers.Entity.CarrierProfile;
import com.saffaricarrers.saffaricarrers.Entity.User;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.BankDetailsRepository;
import com.saffaricarrers.saffaricarrers.Repository.CarrierProfileRepository;
import com.saffaricarrers.saffaricarrers.Repository.UserRepository;
import com.saffaricarrers.saffaricarrers.Responses.ApiResponse1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CarrierService {

    private final UserRepository userRepository;
    private final CarrierProfileRepository carrierProfileRepository;
    private final BankDetailsRepository bankDetailsRepository;
    private final Bankdetailsservice bankDetailsService;

    // =====================================================================
    // REGISTER AS CARRIER
    // =====================================================================

    /**
     * Register existing user as carrier.
     * After this, the carrier must submit bank details via BankDetailsService.
     * Carrier profile stays INACTIVE until admin verifies bank details.
     */
    public ApiResponse1<CarrierProfileDto> registerAsCarrier(String userId) {
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            return ApiResponse1.error("User not found: " + userId);
        }

        User user = userOpt.get();

        if (!user.getVerified()) {
            return ApiResponse1.error("User must be verified before registering as carrier.");
        }

        // ✅ If carrier profile already exists
        if (carrierProfileRepository.existsByUser(user)) {
            CarrierProfile existing = carrierProfileRepository.findByUserUid(userId).orElse(null);
            if (existing == null) return ApiResponse1.error("Carrier profile lookup failed.");

            if (existing.getBankDetails() != null
                    && Boolean.TRUE.equals(existing.getBankDetails().getIsVerified())) {
                // Already fully verified
                existing.setIsVerified(true);
                existing.setStatus(CarrierProfile.CarrierStatus.ACTIVE);
                carrierProfileRepository.save(existing);
                return ApiResponse1.success("Carrier profile is active.", mapToDto(existing));
            }

            if (existing.getBankDetails() != null) {
                // Bank details submitted but pending verification
                return ApiResponse1.success(
                        "Carrier profile exists. Bank details are pending verification.",
                        mapToDto(existing));
            }

            // Bank details not yet submitted
            return ApiResponse1.error(
                    "Carrier profile exists. Please submit bank details to complete registration.");
        }

        // ✅ Upgrade user type
        if (user.getUserType() == User.UserType.SENDER) {
            user.setUserType(User.UserType.BOTH);
        }
        userRepository.save(user);

        // ✅ Create carrier profile — INACTIVE until bank verified
        CarrierProfile profile = new CarrierProfile();
        profile.setUserUid(userId);
        profile.setUser(user);
        profile.setIsVerified(false);
        profile.setStatus(CarrierProfile.CarrierStatus.INACTIVE);
        profile.setWeeklyOrderCount(0);
        profile.setTotalEarnings(BigDecimal.ZERO);
        profile.setPendingCommission(BigDecimal.ZERO);

        CarrierProfile saved = carrierProfileRepository.save(profile);

        log.info("✅ Carrier profile created for user: {}. Awaiting bank details.", userId);
        return ApiResponse1.success(
                "Carrier profile created. Please add your bank details to activate your account.",
                mapToDto(saved));
    }

    // =====================================================================
    // ADD BANK DETAILS (delegates to BankDetailsService)
    // =====================================================================

    /**
     * Carrier adds bank details — delegates to BankDetailsService.
     * Carrier stays INACTIVE until admin verifies.
     */
    public ApiResponse1<CarrierProfileDto> addBankDetails(String userId, BankDetailsDto.SubmitRequest req) {
        CarrierProfile profile = carrierProfileRepository.findByUserUid(userId)
                .orElse(null);
        if (profile == null) {
            return ApiResponse1.error("Carrier profile not found. Please register as carrier first.");
        }

        // Check if already verified — don't allow re-submission through this endpoint
        // (use dedicated update endpoint instead)
        if (profile.getBankDetails() != null) {
            return ApiResponse1.error(
                    "Bank details already submitted. Use the update endpoint to make changes.");
        }

        try {
            BankDetailsDto.Response bankResponse = bankDetailsService.submitBankDetails(userId, req);
            log.info("✅ Bank details added for carrier: {}. Status: {}", userId,
                    bankResponse.getVerificationStatus());
            return ApiResponse1.success(
                    "Bank details submitted successfully. Your account will be activated after admin verification (1–2 business days).",
                    mapToDto(profile));
        } catch (Exception e) {
            return ApiResponse1.error(e.getMessage());
        }
    }

    // =====================================================================
    // UPDATE BANK DETAILS
    // =====================================================================

    /**
     * Carrier updates existing bank details — resets verification to PENDING.
     */
    public ApiResponse1<CarrierProfileDto> updateBankDetails(String userId, BankDetailsDto.SubmitRequest req) {
        CarrierProfile profile = carrierProfileRepository.findByUserUid(userId)
                .orElse(null);
        if (profile == null) {
            return ApiResponse1.error("Carrier profile not found.");
        }

        try {
            bankDetailsService.submitBankDetails(userId, req); // submitBankDetails handles update too
            log.info("✅ Bank details updated for carrier: {}", userId);
            return ApiResponse1.success(
                    "Bank details updated. Your account will be re-verified by our team.",
                    mapToDto(profile));
        } catch (Exception e) {
            return ApiResponse1.error(e.getMessage());
        }
    }

    // =====================================================================
    // GET PROFILE
    // =====================================================================

    public CarrierProfileDto getCarrierProfile(String userId) {
        CarrierProfile profile = carrierProfileRepository.findByUserUid(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Carrier profile not found for: " + userId));
        return mapToDto(profile);
    }

    public boolean hasCarrierProfile(String userId) {
        return carrierProfileRepository.findByUserUid(userId).isPresent();
    }

    public boolean isCarrierActive(String userId) {
        return carrierProfileRepository.findByUserUid(userId)
                .map(p -> Boolean.TRUE.equals(p.getIsVerified())
                        && p.getStatus() == CarrierProfile.CarrierStatus.ACTIVE)
                .orElse(false);
    }

    // =====================================================================
    // ADMIN ACTIONS
    // =====================================================================

    public CarrierProfileDto activateCarrier(String userId) {
        CarrierProfile profile = carrierProfileRepository.findByUserUid(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier not found: " + userId));

        if (profile.getBankDetails() == null || !Boolean.TRUE.equals(profile.getBankDetails().getIsVerified())) {
            throw new IllegalStateException("Bank details must be verified before activating carrier.");
        }

        profile.setIsVerified(true);
        profile.setStatus(CarrierProfile.CarrierStatus.ACTIVE);
        return mapToDto(carrierProfileRepository.save(profile));
    }

    public CarrierProfileDto deactivateCarrier(String userId) {
        CarrierProfile profile = carrierProfileRepository.findByUserUid(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier not found: " + userId));
        profile.setStatus(CarrierProfile.CarrierStatus.INACTIVE);
        return mapToDto(carrierProfileRepository.save(profile));
    }

    // =====================================================================
    // PRIVATE MAPPING
    // =====================================================================

    private CarrierProfileDto mapToDto(CarrierProfile profile) {
        CarrierProfileDto dto = new CarrierProfileDto();
        dto.setCarrierId(profile.getCarrierId());
        dto.setUserUid(profile.getUserUid());
        dto.setIsVerified(profile.getIsVerified());
        dto.setStatus(profile.getStatus());
        dto.setWeeklyOrderCount(profile.getWeeklyOrderCount());
        dto.setTotalEarnings(profile.getTotalEarnings());
        dto.setPendingCommission(profile.getPendingCommission());
        dto.setCreatedAt(profile.getCreatedAt());

        if (profile.getBankDetails() != null) {
            BankDetails bd = profile.getBankDetails();
            BankDetailsDto legacy = new BankDetailsDto();
            legacy.setBankId(bd.getBankDetailsId());
            legacy.setAccountHolderName(bd.getAccountHolderName());
            // ✅ Always return masked account number in DTO
            legacy.setAccountNumber(bd.getMaskedAccountNumber());
            legacy.setIfscCode(bd.getIfscCode());
            legacy.setBankName(bd.getBankName());
            legacy.setBranchName(bd.getBranchName());
            legacy.setIsVerified(bd.getIsVerified());
            legacy.setVerificationStatus(bd.getVerificationStatus().toString());
            legacy.setCreatedAt(bd.getCreatedAt());
            dto.setBankDetails(legacy);
        }

        return dto;
    }
}