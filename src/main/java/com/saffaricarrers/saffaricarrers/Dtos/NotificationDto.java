package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.Notification;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationDto {
    private Long notificationId;
    private String title;
    private String message;
    private Notification.NotificationType type;
    private Long referenceId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
