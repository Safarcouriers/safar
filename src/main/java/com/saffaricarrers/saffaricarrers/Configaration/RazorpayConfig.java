package com.saffaricarrers.saffaricarrers.Configaration;

import com.razorpay.RazorpayClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Getter
public class RazorpayConfig {

    // ── SafarCarry credentials ────────────────────────────────────────────────
    @Value("${safarcarry.razorpay.key.id}")
    private String safarcarryKeyId;

    @Value("${safarcarry.razorpay.key.secret}")
    private String safarcarryKeySecret;

    // ── Notivibe credentials ──────────────────────────────────────────────────
    @Value("${notivibe.razorpay.key.id}")
    private String keyId;                    // kept as keyId — Notivibe services use this

    @Value("${notivibe.razorpay.key.secret}")
    private String keySecret;

    @Value("${notivibe.razorpay.webhook.secret}")
    private String webhookSecret;

    // ── Notivibe plan IDs ─────────────────────────────────────────────────────
    @Value("${razorpay.plan.weekly}")    private String weeklyPlanId;
    @Value("${razorpay.plan.monthly}")   private String monthlyPlanId;
    @Value("${razorpay.plan.quarterly}") private String quarterlyPlanId;
    @Value("${razorpay.plan.biannual}")  private String biannualPlanId;
    @Value("${razorpay.plan.annual}")    private String annualPlanId;

    @Value("${razorpay.plan.intro.weekly}")    private String introWeeklyPlanId;
    @Value("${razorpay.plan.intro.monthly}")   private String introMonthlyPlanId;
    @Value("${razorpay.plan.intro.quarterly}") private String introQuarterlyPlanId;
    @Value("${razorpay.plan.intro.biannual}")  private String introBiannualPlanId;
    @Value("${razorpay.plan.intro.annual}")    private String introAnnualPlanId;

    @Value("${razorpay.intro.days.weekly}")    private int introWeeklyDays;
    @Value("${razorpay.intro.days.monthly}")   private int introMonthlyDays;
    @Value("${razorpay.intro.days.quarterly}") private int introQuarterlyDays;
    @Value("${razorpay.intro.days.biannual}")  private int introBiannualDays;
    @Value("${razorpay.intro.days.annual}")    private int introAnnualDays;

    @Value("${razorpay.intro.amount}")
    private long introAmount;

    // ── Beans ─────────────────────────────────────────────────────────────────

    // SafarCarry — used by PaymentService
    @Bean("safarcarryRazorpayClient")
    public RazorpayClient safarcarryRazorpayClient() throws Exception {
        return new RazorpayClient(safarcarryKeyId, safarcarryKeySecret);
    }

    // Notivibe — used by SubscriptionService, WebhookController
    @Bean("notivibeRazorpayClient")
    public RazorpayClient notivibeRazorpayClient() throws Exception {
        return new RazorpayClient(keyId, keySecret);
    }

    // ── Helpers (Notivibe plan logic) ─────────────────────────────────────────
    public String getRegularPlanId(String planKey) {
        return switch (planKey.toLowerCase()) {
            case "weekly"    -> weeklyPlanId;
            case "monthly"   -> monthlyPlanId;
            case "quarterly" -> quarterlyPlanId;
            case "biannual"  -> biannualPlanId;
            case "annual"    -> annualPlanId;
            default -> throw new IllegalArgumentException("Unknown plan key: " + planKey);
        };
    }

    public String getIntroPlanId(String planKey) {
        return switch (planKey.toLowerCase()) {
            case "weekly"    -> introWeeklyPlanId;
            case "monthly"   -> introMonthlyPlanId;
            case "quarterly" -> introQuarterlyPlanId;
            case "biannual"  -> introBiannualPlanId;
            case "annual"    -> introAnnualPlanId;
            default -> throw new IllegalArgumentException("Unknown plan key: " + planKey);
        };
    }

    public int getIntroDays(String planKey) {
        return switch (planKey.toLowerCase()) {
            case "weekly"    -> introWeeklyDays;
            case "monthly"   -> introMonthlyDays;
            case "quarterly" -> introQuarterlyDays;
            case "biannual"  -> introBiannualDays;
            case "annual"    -> introAnnualDays;
            default -> 7;
        };
    }

    public String getPlanDisplayName(String planKey) {
        return switch (planKey.toLowerCase()) {
            case "weekly"    -> "Weekly";
            case "monthly"   -> "Monthly";
            case "quarterly" -> "3-Month";
            case "biannual"  -> "6-Month";
            case "annual"    -> "Yearly";
            default -> "Premium";
        };
    }

    public long getPlanAmountPaise(String planKey) {
        return switch (planKey.toLowerCase()) {
            case "weekly"    -> 9900L;
            case "monthly"   -> 19900L;
            case "quarterly" -> 49900L;
            case "biannual"  -> 149900L;
            case "annual"    -> 249900L;
            default -> 9900L;
        };
    }

    public Map<String, String> getAllPlanKeys() {
        Map<String, String> map = new HashMap<>();
        map.put("weekly",    weeklyPlanId);
        map.put("monthly",   monthlyPlanId);
        map.put("quarterly", quarterlyPlanId);
        map.put("biannual",  biannualPlanId);
        map.put("annual",    annualPlanId);
        return map;
    }
}