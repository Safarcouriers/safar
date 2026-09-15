package com.saffaricarrers.saffaricarrers.N1.model;

import java.time.LocalDateTime;

public enum N1MonetizationPlan {
//    THREE_MONTHS("quarterly", "3 Months", 89900L, 90),
//    SIX_MONTHS("biannual", "6 Months", 149900L, 180),
//    ONE_YEAR("annual", "1 Year", 239900L, 365);
QUARTERLY("quarterly", "3 Months", 89900L, 90),
    BIANNUAL("biannual", "6 Months", 149900L, 180),
    ANNUAL("annual", "1 Year", 239900L, 365);
//QUARTERLY("quarterly", "3 Months", 100L, 90),
//    BIANNUAL("biannual", "6 Months", 1L, 180),
//    ANNUAL("annual", "1 Year", 1L, 365);
    private final String key;
    private final String displayName;
    private final long amountPaise;
    private final int validityDays;

    N1MonetizationPlan(String key, String displayName, long amountPaise, int validityDays) {
        this.key = key;
        this.displayName = displayName;
        this.amountPaise = amountPaise;
        this.validityDays = validityDays;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public long getAmountPaise() { return amountPaise; }
    public int getValidityDays() { return validityDays; }

    public LocalDateTime calculateExpiry(LocalDateTime purchaseTime) {
        return purchaseTime.plusDays(validityDays);
    }

    public static N1MonetizationPlan fromKey(String key) {
        if (key == null) throw new IllegalArgumentException("planKey is required");
        for (N1MonetizationPlan p : values()) {
            if (p.key.equalsIgnoreCase(key.trim())) return p;
        }
        throw new IllegalArgumentException("Unsupported plan: " + key);
    }
}
