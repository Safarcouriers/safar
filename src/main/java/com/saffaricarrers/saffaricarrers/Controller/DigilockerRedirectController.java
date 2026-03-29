package com.saffaricarrers.saffaricarrers.Controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@Slf4j
public class DigilockerRedirectController {

    @GetMapping(value = "/digilocker-redirect", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String handleDigilockerRedirect(@RequestParam Map<String, String> allParams) {

        try {
            log.info("═══════════════════════════════════════");
            log.info("🔄 DIGILOCKER REDIRECT RECEIVED");
            log.info("═══════════════════════════════════════");
            log.info("ALL PARAMETERS: {}", allParams);

            // Try multiple parameter name variations
            String verificationId = allParams.getOrDefault("verificationid",
                    allParams.getOrDefault("verificationId",
                            allParams.getOrDefault("verification_id",
                                    allParams.getOrDefault("id", "UNKNOWN"))));

            String status = allParams.getOrDefault("status",
                    allParams.getOrDefault("state", ""));

            log.info("✅ Extracted Verification ID: {}", verificationId);
            log.info("✅ Extracted Status: {}", status);
            log.info("═══════════════════════════════════════");

            return generateRedirectHtml(verificationId);

        } catch (Exception e) {
            log.error("❌ ERROR in DigiLocker redirect handler", e);
            return generateErrorHtml(e.getMessage());
        }
    }

    // Test endpoint
    @GetMapping("/test-redirect")
    @ResponseBody
    public String testRedirect() {
        log.info("Test endpoint called");
        return "<html><body><h1>✅ Test Successful!</h1><p>Controller is working!</p></body></html>";
    }

    private String generateRedirectHtml(String verificationId) {
        return "<!DOCTYPE html>" +
                "<html><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>Verification Complete</title>" +
                "<style>" +
                "* { margin:0; padding:0; box-sizing:border-box; }" +
                "body { font-family: -apple-system, BlinkMacSystemFont, sans-serif;" +
                "display:flex; justify-content:center; align-items:center;" +
                "min-height:100vh; background:#E53935; color:white;" +
                "text-align:center; padding:20px; }" +
                ".box { background:rgba(255,255,255,0.15); border-radius:20px;" +
                "padding:40px 30px; max-width:400px; width:100%; }" +
                ".check { font-size:72px; margin-bottom:20px; }" +
                "h1 { font-size:26px; font-weight:700; margin-bottom:16px; }" +
                "p { font-size:16px; opacity:0.95; margin-bottom:12px; line-height:1.5; }" +
                ".highlight { background:rgba(255,255,255,0.2); border-radius:12px;" +
                "padding:16px; margin-top:20px; font-weight:600; font-size:17px; }" +
                "</style></head>" +
                "<body><div class='box'>" +
                "<div class='check'>✅</div>" +
                "<h1>DigiLocker Verified!</h1>" +
                "<p>Your Aadhaar verification is complete.</p>" +
                "<div class='highlight'>" +
                "👉 Now open the<br/><strong>Safar Couriers app</strong><br/>" +
                "and tap<br/><strong>\"I've Completed DigiLocker ✓\"</strong>" +
                "</div>" +
                "</div></body></html>";
    }

    private String generateErrorHtml(String errorMessage) {
        return "<html><body style='font-family: sans-serif; padding: 20px;'>" +
                "<h1 style='color: red;'>❌ Error</h1>" +
                "<p>" + errorMessage + "</p>" +
                "<a href='/test-redirect' style='color: blue;'>Test Endpoint</a>" +
                "</body></html>";
    }
}
