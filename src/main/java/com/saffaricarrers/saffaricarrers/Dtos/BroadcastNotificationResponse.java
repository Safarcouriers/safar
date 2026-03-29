package com.saffaricarrers.saffaricarrers.Dtos;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastNotificationResponse {
    private String userType;
    private String title;
    private String body;
    private int totalTargeted;   // users with FCM token
    private int sent;            // successful sends
    private int failed;          // FCM errors
    private int skipped;         // no FCM token
    private LocalDateTime sentAt;
}