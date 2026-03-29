package com.saffaricarrers.saffaricarrers.Entity;


import lombok.Data;

@Data
public class BroadcastNotificationRequest {
    private String title;
    private String body;
    /**
     * "ALL" | "SENDER" | "CARRIER" | "BOTH"
     */
    private String userType;
}