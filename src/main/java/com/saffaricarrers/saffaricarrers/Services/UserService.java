package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.UserRegistrationRequest;
import com.saffaricarrers.saffaricarrers.Dtos.DocumentUploadRequest;
import com.saffaricarrers.saffaricarrers.Entity.CarrierProfile;
import com.saffaricarrers.saffaricarrers.Entity.User;
import com.saffaricarrers.saffaricarrers.Entity.DocumentVerificationStatus;
import com.saffaricarrers.saffaricarrers.Entity.OtpVerification;
import com.saffaricarrers.saffaricarrers.Exception.DocumentVerificationException;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Exception.UserAlreadyExistsException;
import com.saffaricarrers.saffaricarrers.Repository.UserRepository;
import com.saffaricarrers.saffaricarrers.Repository.DocumentVerificationStatusRepository;
import com.saffaricarrers.saffaricarrers.Repository.AddressRepository;
import com.saffaricarrers.saffaricarrers.Repository.CarrierProfileRepository;
import com.saffaricarrers.saffaricarrers.Responses.PageResponse;
import com.saffaricarrers.saffaricarrers.Responses.UserProfileResponse;
import com.saffaricarrers.saffaricarrers.Responses.VerificationStatusResponse;
import com.saffaricarrers.saffaricarrers.Responses.ProfileCompletionResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final DocumentVerificationStatusRepository documentStatusRepository;
    private final AddressRepository addressRepository;
    private final CarrierProfileRepository carrierProfileRepository;
    private final CallBackService callBackService;
    private final CarrierService carrierService;
    private final EntityManager entityManager;

    // ✅ Sentinel value — marks "collected via OCR, no physical image needed"
    private static final String OCR_COLLECTED = "OCR_VERIFIED";

    // ─────────────────────────────────────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────────────────────────────────────

    public UserProfileResponse registerUser(UserRegistrationRequest request, MultipartFile multipartFile) {
        if (userRepository.existsByUserId(request.getFirebaseUid())) {
            throw new UserAlreadyExistsException("User already exists with UID: " + request.getFirebaseUid());
        }

        String profileUrl = null;
        try {
            profileUrl = callBackService.uploadToS3(
                    multipartFile.getInputStream(),
                    multipartFile.getContentType(),
                    multipartFile.getSize()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        User user = new User();
        user.setUserId(request.getFirebaseUid());
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        user.setAge(request.getAge());
        user.setGender(request.getGender());
        user.setVerified(false);
        user.setVerificationStatus(User.VerificationStatus.PENDING);
        user.setStatus(User.UserStatus.INACTIVE);
        user.setProfileUrl(profileUrl);
        user.setUserType(request.getUserType());

        // ✅ Pre-set document image fields to OCR_VERIFIED sentinel at registration.
        // Since we do Aadhaar OCR + PAN verification via Cashfree/DigiLocker,
        // we never need physical image uploads — mark as collected from the start.
        user.setAadharFrontUrl(OCR_COLLECTED);
        user.setAadharBackUrl(OCR_COLLECTED);
        user.setPanCardUrl(OCR_COLLECTED);

        User savedUser = userRepository.save(user);
        log.info("User registration completed for: {}", savedUser.getUserId());

        return mapToUserProfileResponse(savedUser);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROFILE
    // ─────────────────────────────────────────────────────────────────────────

    @Cacheable(value = "users", key = "#userId")
    public UserProfileResponse getUserProfile(String userId) {
        log.info("Cache MISS - Fetching user profile from database: {}", userId);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        return mapToUserProfileResponse(user);
    }

    @CacheEvict(value = {"users", "profiles", "userPages", "userPagesWithProfile"}, allEntries = true)
    public UserProfileResponse updateUserProfile(String userId, UserRegistrationRequest request) {
        log.info("Updating user profile and evicting cache for: {}", userId);

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Only update fields that are actually provided — never overwrite with null.
        // Frontend edit-profile no longer sends gender/userType, so preserve existing DB values.
        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName());
        }
        if (request.getMobile() != null && !request.getMobile().trim().isEmpty()) {
            user.setMobile(request.getMobile());
        }
        if (request.getAge() != null) {
            user.setAge(request.getAge());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getUserType() != null) {
            user.setUserType(request.getUserType());
        }

        // ✅ FIX: persist the new profile image URL if frontend sends one
        // Frontend uploads image to S3 via /upload-profile-image, gets back URL,
        // then includes it in the PUT /profile body as profileUrl.
        if (request.getProfileUrl() != null && !request.getProfileUrl().trim().isEmpty()) {
            log.info("Updating profileUrl for user: {} → {}", userId, request.getProfileUrl());
            user.setProfileUrl(request.getProfileUrl());
        }

        User updatedUser = userRepository.save(user);
        log.info("User profile updated successfully for: {}", userId);
        return mapToUserProfileResponse(updatedUser);
    }

    @CacheEvict(value = {"users", "profiles"}, key = "#userId")
    public UserProfileResponse updateUserType(String userId, User.UserType newUserType) {
        log.info("Updating user type and evicting cache for: {}", userId);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        if (user.getUserType() == User.UserType.SENDER && newUserType == User.UserType.CARRIER) {
            user.setUserType(User.UserType.BOTH);
        } else if (user.getUserType() == User.UserType.CARRIER && newUserType == User.UserType.SENDER) {
            user.setUserType(User.UserType.BOTH);
        } else {
            user.setUserType(newUserType);
        }
        User updatedUser = userRepository.save(user);
        log.info("User type updated to {} for user: {}", newUserType, userId);
        return mapToUserProfileResponse(updatedUser);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROFILE COMPLETION
    // ─────────────────────────────────────────────────────────────────────────

    @Cacheable(value = "profiles", key = "#userId")
    public ProfileCompletionResponse getProfileCompletionStatus(String userId) {
        log.info("Cache MISS - Calculating profile completion for: {}", userId);

        User user = userRepository.findByUserIdWithProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        ProfileCompletionResponse response = new ProfileCompletionResponse();
        response.setUserId(userId);

        // ── Document verification ─────────────────────────────────────────────
        List<DocumentVerificationStatus> allStatuses = documentStatusRepository.findByUserId(userId);

        boolean aadhaarVerified = allStatuses.stream()
                .anyMatch(s -> s.getDocumentType() == OtpVerification.DocumentType.AADHAAR &&
                        s.getStatus() == DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED);

        boolean panVerified = allStatuses.stream()
                .anyMatch(s -> s.getDocumentType() == OtpVerification.DocumentType.PAN &&
                        s.getStatus() == DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED);

        boolean userVerificationComplete = user.getVerificationStatus() == User.VerificationStatus.VERIFIED;
        response.setDocumentVerificationCompleted((aadhaarVerified && panVerified) || userVerificationComplete);

        // ✅ FIXED: documentImagesUploaded is always true.
        // We do Aadhaar OCR + PAN verification via Cashfree/DigiLocker —
        // physical image uploads are not required. New users have OCR_VERIFIED
        // set at registration; existing users are handled by the fallback below.
        response.setDocumentImagesUploaded(true);

        // ── Basic details ─────────────────────────────────────────────────────
        // Gender intentionally excluded — edit-profile no longer collects it
        boolean userDetailsCompleted = user.getFullName() != null &&
                user.getMobile() != null &&
                user.getAge() != null;
        response.setUserDetailsCompleted(userDetailsCompleted);
        response.setProfileImageUploaded(user.getProfileUrl() != null);

        boolean addressCompleted = addressRepository.existsByUser(user);
        response.setAddressCompleted(addressCompleted);

        // ── Carrier profile + bank details ────────────────────────────────────
        boolean carrierProfileCompleted;
        boolean bankDetailsCompleted;

        boolean isCarrierUser = user.getUserType() == User.UserType.CARRIER
                || user.getUserType() == User.UserType.BOTH;

        if (isCarrierUser) {
            CarrierProfile carrierProfile = user.getCarrierProfile();

            // ✅ Auto-register carrier profile row when docs are complete
            if (carrierProfile == null && user.getVerified()) {
                log.info("Auto-registering carrier profile for user: {}", userId);
                carrierService.registerAsCarrier(userId);
                user = userRepository.findByUserIdWithProfile(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
                carrierProfile = user.getCarrierProfile();
            }

            bankDetailsCompleted = carrierProfile != null
                    && carrierProfile.getBankDetails() != null;

            carrierProfileCompleted = bankDetailsCompleted;

        } else {
            carrierProfileCompleted = true;
            bankDetailsCompleted = true;
        }

        response.setCarrierProfileCompleted(carrierProfileCompleted);

        // ── Completion percentage ─────────────────────────────────────────────
        int totalSteps = isCarrierUser ? 7 : 5;
        int completedSteps = 0;

        if (response.isDocumentVerificationCompleted()) completedSteps++;
        if (response.isDocumentImagesUploaded()) completedSteps++;
        if (response.isUserDetailsCompleted()) completedSteps++;
        if (response.isProfileImageUploaded()) completedSteps++;
        if (response.isAddressCompleted()) completedSteps++;
        if (isCarrierUser && carrierProfileCompleted) completedSteps++;
        if (isCarrierUser && bankDetailsCompleted) completedSteps++;

        response.setCompletionPercentage((completedSteps * 100) / totalSteps);
        response.setProfileCompleted(completedSteps == totalSteps);

        // ── Next step ─────────────────────────────────────────────────────────
        if (!response.isUserDetailsCompleted()) {
            response.setNextStep("USER_DETAILS");
            response.setNextStepMessage("Complete user profile details");
        } else if (!response.isProfileImageUploaded()) {
            response.setNextStep("PROFILE_IMAGE");
            response.setNextStepMessage("Upload profile image");
        } else if (!response.isAddressCompleted()) {
            response.setNextStep("ADDRESS");
            response.setNextStepMessage("Add your address information");
        } else if (!aadhaarVerified) {
            response.setNextStep("AADHAAR_VERIFICATION");
            response.setNextStepMessage("Complete Aadhaar verification via DigiLocker");
        } else if (!panVerified) {
            response.setNextStep("PAN_VERIFICATION");
            response.setNextStepMessage("Complete PAN verification");
        } else if (!bankDetailsCompleted && isCarrierUser) {
            response.setNextStep("BANK_DETAILS");
            response.setNextStepMessage("Add bank details for carrier profile");
        } else if (!carrierProfileCompleted) {
            response.setNextStep("CARRIER_PROFILE");
            response.setNextStepMessage("Complete carrier profile setup");
        } else {
            response.setNextStep("COMPLETED");
            response.setNextStepMessage("Profile setup completed successfully!");
        }

        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOCUMENTS
    // ─────────────────────────────────────────────────────────────────────────

    @CacheEvict(value = {"users", "profiles", "userPages", "userPagesWithProfile"}, allEntries = true)
    public UserProfileResponse uploadDocumentImages(String userId, DocumentUploadRequest request) {
        log.info("Uploading documents and evicting cache for: {}", userId);

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        try {
            if (request.getAadharFrontImage() != null) {
                String aadharFrontUrl = callBackService.uploadToS3(
                        request.getAadharFrontImage().getInputStream(),
                        request.getAadharFrontImage().getContentType(),
                        request.getAadharFrontImage().getSize()
                );
                user.setAadharFrontUrl(aadharFrontUrl);
            }

            if (request.getAadharBackImage() != null) {
                String aadharBackUrl = callBackService.uploadToS3(
                        request.getAadharBackImage().getInputStream(),
                        request.getAadharBackImage().getContentType(),
                        request.getAadharBackImage().getSize()
                );
                user.setAadharBackUrl(aadharBackUrl);
            }

            if (request.getPanCardImage() != null) {
                String panCardUrl = callBackService.uploadToS3(
                        request.getPanCardImage().getInputStream(),
                        request.getPanCardImage().getContentType(),
                        request.getPanCardImage().getSize()
                );
                user.setPanCardUrl(panCardUrl);
            }

            user.setVerified(true);
            User updatedUser = userRepository.save(user);
            log.info("Document images uploaded successfully for user: {}", userId);
            return mapToUserProfileResponse(updatedUser);

        } catch (IOException e) {
            log.error("Error uploading document images for user: {}", userId, e);
            throw new DocumentVerificationException("Failed to upload document images: " + e.getMessage());
        }
    }

    @CacheEvict(value = {"users", "profiles"}, key = "#userId")
    public UserProfileResponse uploadProfileImages(String userId, DocumentUploadRequest request) {
        log.info("Uploading profile image and evicting cache for: {}", userId);

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (user.getVerificationStatus() != User.VerificationStatus.VERIFIED) {
            throw new DocumentVerificationException("Documents must be verified before uploading images");
        }

        try {
            if (request.getProfileImage() != null) {
                String profileUrl = callBackService.uploadToS3(
                        request.getProfileImage().getInputStream(),
                        request.getProfileImage().getContentType(),
                        request.getProfileImage().getSize()
                );
                user.setProfileUrl(profileUrl);
            }

            user.setVerified(true);
            User updatedUser = userRepository.save(user);
            log.info("Profile image uploaded successfully for user: {}", userId);
            return mapToUserProfileResponse(updatedUser);

        } catch (IOException e) {
            log.error("Error uploading profile image for user: {}", userId, e);
            throw new DocumentVerificationException("Failed to upload profile image: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VERIFICATION
    // ─────────────────────────────────────────────────────────────────────────

    @Cacheable(value = "verification", key = "#userId")
    public VerificationStatusResponse getVerificationStatus(String userId) {
        log.info("Cache MISS - Fetching verification status for: {}", userId);

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        VerificationStatusResponse response = new VerificationStatusResponse();
        response.setUserId(userId);
        response.setVerificationStatus(user.getVerificationStatus());
        response.setIsVerified(user.getVerified());
        response.setCanAccessFeatures(canUserAccessFeatures(userId));
        response.setLastUpdatedAt(user.getUpdatedAt());

        switch (user.getVerificationStatus()) {
            case PENDING:
                response.setMessage("Document verification in progress. Please complete Aadhaar and PAN verification.");
                response.setEstimatedWaitTime("Depends on completion");
                response.setCanRetry(false);
                break;
            case VERIFIED:
                if (user.getVerified()) {
                    response.setMessage("Your profile is fully verified and ready to use!");
                } else {
                    response.setMessage("Documents verified. Please complete profile setup.");
                }
                response.setEstimatedWaitTime("0 minutes");
                response.setCanRetry(false);
                break;
            case REJECTED:
                response.setMessage("Document verification failed. Please retry verification with correct details.");
                response.setEstimatedWaitTime("N/A");
                response.setCanRetry(true);
                break;
        }

        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITIES
    // ─────────────────────────────────────────────────────────────────────────

    public boolean canUserAccessFeatures(String userId) {
        return userRepository.findByUserId(userId)
                .map(user -> user.getVerified() && user.getStatus() == User.UserStatus.ACTIVE)
                .orElse(false);
    }

    public boolean existsByUserId(String userId) {
        return userRepository.findByUserId(userId).isPresent();
    }

    public boolean canUserBecomeCarrier(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        return user.getVerified() &&
                user.getVerificationStatus() == User.VerificationStatus.VERIFIED &&
                !carrierProfileRepository.existsByUser(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FCM
    // ─────────────────────────────────────────────────────────────────────────

    @CacheEvict(value = {"users", "profiles"}, key = "#userId")
    public void updateFcmToken(String userId, String fcmToken) {
        log.info("Updating FCM token for user: {}", userId);
        int updated = userRepository.updateFcmTokenDirectly(userId, fcmToken);
        if (updated == 0) {
            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }
        log.info("FCM token updated successfully for user: {}", userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACCOUNT DELETION
    // ─────────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────
// ACCOUNT DELETION
// ─────────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────
// ACCOUNT DELETION
// ─────────────────────────────────────────────────────────────────────────

    @CacheEvict(value = {"users", "profiles", "verification", "userPages", "userPagesWithProfile"}, allEntries = true)
    @Transactional
    public void deleteUser(String userId) {
        log.info("Starting account deletion for user: {}", userId);

        User user = userRepository.findByUserIdWithProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // 0. Clear location_tracking rows referencing ANY delivery_requests tied
        // to this user (as sender OR carrier-via-routes) before any cascade or
        // native delete touches delivery_requests. Must run first.
        int clearedTracking = entityManager.createNativeQuery(
                "DELETE lt FROM location_tracking1 lt " +
                        "INNER JOIN delivery_requests dr ON lt.request_id = dr.request_id " +
                        "LEFT JOIN carrier_routes cr ON dr.route_id = cr.route_id " +
                        "LEFT JOIN carrier_profiles cp ON cr.carrier_id = cp.carrier_id " +
                        "INNER JOIN users u ON dr.sender_id = u.id OR cp.user_id = u.id " +
                        "WHERE u.user_id = :userId"
        ).setParameter("userId", userId).executeUpdate();
        log.info("Cleared {} location_tracking rows for user: {}", clearedTracking, userId);

        // 1. Delete document verification status records
        List<DocumentVerificationStatus> docStatuses = documentStatusRepository.findByUserId(userId);
        if (!docStatuses.isEmpty()) {
            documentStatusRepository.deleteAll(docStatuses);
            log.info("Deleted {} document verification records for: {}", docStatuses.size(), userId);
        }

        // 2. Delete addresses
        addressRepository.deleteByUser(user);
        log.info("Deleted addresses for: {}", userId);

        // 3. Delete carrier profile and everything that depends on it.
        CarrierProfile carrierProfile = user.getCarrierProfile();
        if (carrierProfile != null) {
            Long carrierProfileId = carrierProfile.getCarrierId();

            // 3a. payments tied to delivery_requests on this carrier's routes
            entityManager.createNativeQuery(
                    "DELETE p FROM payments p " +
                            "INNER JOIN delivery_requests dr ON p.delivery_request_id = dr.request_id " +
                            "INNER JOIN carrier_routes cr ON dr.route_id = cr.route_id " +
                            "WHERE cr.carrier_id = :carrierProfileId"
            ).setParameter("carrierProfileId", carrierProfileId).executeUpdate();

            // 3b. payments tied directly to packages on this carrier's routes
            entityManager.createNativeQuery(
                    "DELETE p FROM payments p " +
                            "INNER JOIN delivery_requests dr ON p.package_id = dr.package_id " +
                            "INNER JOIN carrier_routes cr ON dr.route_id = cr.route_id " +
                            "WHERE cr.carrier_id = :carrierProfileId"
            ).setParameter("carrierProfileId", carrierProfileId).executeUpdate();

            // 3c. delivery_requests tied to this carrier's routes
            int deletedRequests = entityManager.createNativeQuery(
                    "DELETE dr FROM delivery_requests dr " +
                            "INNER JOIN carrier_routes cr ON dr.route_id = cr.route_id " +
                            "WHERE cr.carrier_id = :carrierProfileId"
            ).setParameter("carrierProfileId", carrierProfileId).executeUpdate();
            log.info("Deleted {} delivery_requests for carrier: {}", deletedRequests, carrierProfileId);

            // 3d. route_pricing tied to this carrier's routes
            entityManager.createNativeQuery(
                    "DELETE rp FROM route_pricing rp " +
                            "INNER JOIN carrier_routes cr ON rp.route_id = cr.route_id " +
                            "WHERE cr.carrier_id = :carrierProfileId"
            ).setParameter("carrierProfileId", carrierProfileId).executeUpdate();

            // 3e. carrier_routes themselves
            int deletedRoutes = entityManager.createNativeQuery(
                    "DELETE FROM carrier_routes WHERE carrier_id = :carrierProfileId"
            ).setParameter("carrierProfileId", carrierProfileId).executeUpdate();
            log.info("Deleted {} carrier_routes for carrier: {}", deletedRoutes, carrierProfileId);

            // 3f. bank_details — use entityManager.remove on the managed entity
            if (carrierProfile.getBankDetails() != null) {
                entityManager.remove(carrierProfile.getBankDetails());
                carrierProfile.setBankDetails(null);
                log.info("Deleted bank details for carrier profile: {}", carrierProfileId);
            }

            // 3g. flush + clear, then delete the carrier profile itself
            entityManager.flush();
            entityManager.clear();
            carrierProfileRepository.delete(
                    carrierProfileRepository.findById(carrierProfileId)
                            .orElseThrow(() -> new ResourceNotFoundException("Carrier profile not found: " + carrierProfileId))
            );
            log.info("Deleted carrier profile for: {}", userId);

            // entityManager.clear() detached `user` above — re-fetch it
            user = userRepository.findByUserIdWithProfile(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        }

        // 4. Delete S3-stored files (profile image, documents)
        try {
            if (user.getProfileUrl() != null && !user.getProfileUrl().equals(OCR_COLLECTED)) {
                callBackService.deleteFromS3(user.getProfileUrl());
            }
            if (user.getAadharFrontUrl() != null && !user.getAadharFrontUrl().equals(OCR_COLLECTED)) {
                callBackService.deleteFromS3(user.getAadharFrontUrl());
            }
            if (user.getAadharBackUrl() != null && !user.getAadharBackUrl().equals(OCR_COLLECTED)) {
                callBackService.deleteFromS3(user.getAadharBackUrl());
            }
            if (user.getPanCardUrl() != null && !user.getPanCardUrl().equals(OCR_COLLECTED)) {
                callBackService.deleteFromS3(user.getPanCardUrl());
            }
        } catch (Exception e) {
            log.warn("S3 cleanup failed for user {} — continuing with DB deletion", userId, e);
        }

        // 5. TODO: handle packages/payments tied to this user as a SENDER
        // (not carrier). Not covered above — out of scope per client agreement
        // for now, flagged separately.

        // 6. Clear notifications referencing this user directly. Simple
        // direct FK (notifications.user_id -> users.id), no chain — but
        // must run before the user row itself is deleted.
        int deletedNotifications = entityManager.createNativeQuery(
                "DELETE FROM notifications WHERE user_id = :userId"
        ).setParameter("userId", user.getId()).executeUpdate();
        log.info("Deleted {} notifications for: {}", deletedNotifications, userId);

        // 7. Finally, delete the user record itself.
        userRepository.delete(user);
        log.info("User record deleted for: {}", userId);
    }
    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN — PAGED USERS
    // ─────────────────────────────────────────────────────────────────────────

    @Cacheable(value = "userPages", key = "#page + '-' + #size")
    public PageResponse<UserProfileResponse> getAllUsers(int page, int size) {
        log.info("Cache MISS - Fetching users page {} with size {}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> userPage = userRepository.findAll(pageable);
        return mapToPageResponse(userPage);
    }

    @Cacheable(value = "userPagesWithProfile", key = "#page + '-' + #size")
    public PageResponse<UserProfileResponse> getAllUsersWithCarrierProfile(int page, int size) {
        log.info("Cache MISS - Fetching users with profiles page {} with size {}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> userPage = userRepository.findAllWithCarrierProfile(pageable);
        return mapToPageResponse(userPage);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPPERS
    // ─────────────────────────────────────────────────────────────────────────

    private UserProfileResponse mapToUserProfileResponse(User user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(user.getUserId());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setMobile(user.getMobile());
        response.setAge(user.getAge());
        response.setGender(user.getGender() != null ? user.getGender().name() : null);
        response.setUserType(user.getUserType());
        response.setIsVerified(user.getVerified());
        response.setVerificationStatus(user.getVerificationStatus());
        response.setUserStatus(user.getStatus());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        response.setProfileUrl(user.getProfileUrl());
        response.setAadharFrontUrl(user.getAadharFrontUrl());
        response.setAadharBackUrl(user.getAadharBackUrl());
        response.setPanCardUrl(user.getPanCardUrl());
        return response;
    }

    private PageResponse<UserProfileResponse> mapToPageResponse(Page<User> userPage) {
        List<UserProfileResponse> userProfileResponses = userPage.getContent()
                .stream()
                .map(this::mapToUserProfileResponse)
                .collect(Collectors.toList());
        return new PageResponse<>(
                userProfileResponses,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isFirst(),
                userPage.isLast(),
                userPage.isEmpty()
        );
    }
}