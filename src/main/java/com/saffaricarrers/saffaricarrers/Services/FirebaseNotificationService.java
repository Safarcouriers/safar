package com.saffaricarrers.saffaricarrers.Services;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FirebaseNotificationService {

    // ✅ Old method kept for compatibility
    public void sendNotification(String fcmToken, String title, String body) {
        sendNotificationWithData(fcmToken, title, body, Map.of());
    }

    // ✅ New method — use this everywhere
    public void sendNotificationWithData(String fcmToken, String title,
                                         String body, Map<String, String> data) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data) // ✅ This carries type + referenceId + recipientRole
                .build();
        try {
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ FCM notification sent: " + response);
        } catch (FirebaseMessagingException e) {
            System.err.println("❌ FCM send failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
