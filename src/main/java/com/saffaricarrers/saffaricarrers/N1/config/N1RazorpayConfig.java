package com.saffaricarrers.saffaricarrers.N1.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class N1RazorpayConfig {
    @Value("${notivibe.razorpay.key.id}") private String keyId;
    @Value("${notivibe.razorpay.key.secret}") private String keySecret;
    @Value("${notivibe.n1.razorpay.webhook.secret:}") private String webhookSecret;
}
