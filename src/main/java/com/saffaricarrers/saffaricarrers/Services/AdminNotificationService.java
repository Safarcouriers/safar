package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.BroadcastNotificationResponse;
import com.saffaricarrers.saffaricarrers.Entity.BroadcastNotificationRequest;
import com.saffaricarrers.saffaricarrers.Entity.User;
import com.saffaricarrers.saffaricarrers.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminNotificationService {

    private final UserRepository userRepository;
    private final FirebaseNotificationService firebaseNotificationService;

    public BroadcastNotificationResponse sendBroadcast(BroadcastNotificationRequest req) {
        List<User> targets = getTargetUsers(req.getUserType());

        int sent = 0, failed = 0, skipped = 0;

        for (User user : targets) {
            String token = user.getFcmToken() != null ? user.getFcmToken() : null;

            // Skip users with no FCM token (never logged in on device)
            if (token == null || token.isBlank()) {
                skipped++;
                continue;
            }

            try {
                firebaseNotificationService.sendNotificationWithData(
                        token,
                        req.getTitle(),
                        req.getBody(),
                        Map.of(
                                "type", "ADMIN_BROADCAST",
                                "userType", req.getUserType()
                        )
                );
                sent++;
                log.debug("FCM sent to userId={}", user.getUserId());
            } catch (Exception e) {
                failed++;
                log.warn("FCM failed for userId={}: {}", user.getUserId(), e.getMessage());
            }
        }

        log.info("Broadcast done — targeted={} sent={} failed={} skipped={}",
                targets.size(), sent, failed, skipped);

        return BroadcastNotificationResponse.builder()
                .userType(req.getUserType())
                .title(req.getTitle())
                .body(req.getBody())
                .totalTargeted(targets.size())
                .sent(sent)
                .failed(failed)
                .skipped(skipped)
                .sentAt(LocalDateTime.now())
                .build();
    }

    public long countTargetUsers(String userType) {
        if ("ALL".equalsIgnoreCase(userType)) {
            return userRepository.count();
        }
        try {
            User.UserType type = User.UserType.valueOf(userType.toUpperCase());
            return userRepository.countByUserType(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid userType: " + userType +
                    ". Must be ALL, SENDER, CARRIER, or BOTH.");
        }
    }

    // ==================== PRIVATE ====================

    private List<User> getTargetUsers(String userType) {
        if ("ALL".equalsIgnoreCase(userType)) {
            // ✅ Only load userId + fcmToken — no joins needed
            return userRepository.findAllForBroadcast();
        }
        try {
            User.UserType type = User.UserType.valueOf(userType.toUpperCase());
            return userRepository.findByUserTypeForBroadcast(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid userType: " + userType +
                    ". Must be ALL, SENDER, CARRIER, or BOTH.");
        }
    }
}