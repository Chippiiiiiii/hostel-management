package com.outpass.portal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Value("${brevo.api.key:}")
    private String apiKey;

    @Value("${app.mail.from.email:noreply@hostel.app}")
    private String fromEmail;

    @Value("${app.mail.from.name:Hostel Management}")
    private String fromName;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public void sendVerificationEmail(String toEmail, String name, String token) {
        if (!isConfigured()) {
            log.warn("Brevo API key not configured — skipping verification email to {}", toEmail);
            return;
        }
        String link = frontendUrl + "/verify-email?token=" + token;
        String html = """
                <!DOCTYPE html>
                <html><body style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px;">
                  <h2 style="color:#c07a3a;">Verify Your Email</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>Thanks for registering! Click the button below to verify your email and activate your account.</p>
                  <a href="%s" style="display:inline-block;padding:12px 28px;background:#ed8936;color:#fff;text-decoration:none;border-radius:6px;font-weight:600;margin:16px 0;">
                    Verify Email
                  </a>
                  <p style="color:#888;font-size:12px;margin-top:24px;">This link expires in 24 hours.</p>
                  <p style="color:#888;font-size:12px;">Or copy: %s</p>
                </body></html>
                """.formatted(name, link, link);
        send(toEmail, name, "Verify Your Email — Hostel Management", html);
    }

    public void sendPasswordResetEmail(String toEmail, String name, String token) {
        if (!isConfigured()) {
            log.warn("Brevo API key not configured — skipping password reset email to {}", toEmail);
            return;
        }
        String link = frontendUrl + "/forgot-password?token=" + token;
        String html = """
                <!DOCTYPE html>
                <html><body style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px;">
                  <h2 style="color:#c07a3a;">Reset Your Password</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>Click the button below to reset your password. This link is valid for 15 minutes.</p>
                  <a href="%s" style="display:inline-block;padding:12px 28px;background:#ed8936;color:#fff;text-decoration:none;border-radius:6px;font-weight:600;margin:16px 0;">
                    Reset Password
                  </a>
                  <p style="color:#888;font-size:12px;margin-top:24px;">If you did not request this, ignore this email.</p>
                  <p style="color:#888;font-size:12px;">Or copy: %s</p>
                </body></html>
                """.formatted(name, link, link);
        send(toEmail, name, "Reset Your Password — Hostel Management", html);
    }

    private void send(String toEmail, String toName, String subject, String html) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            Map<String, Object> body = Map.of(
                    "sender", Map.of("name", fromName, "email", fromEmail),
                    "to", List.of(Map.of("email", toEmail, "name", toName)),
                    "subject", subject,
                    "htmlContent", html
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.brevo.com/v3/smtp/email", entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent to {} via Brevo: {}", toEmail, subject);
            } else {
                log.error("Brevo rejected email to {}: {}", toEmail, response.getBody());
                throw new RuntimeException("Email delivery failed");
            }
        } catch (Exception e) {
            log.error("Failed to send email to {} via Brevo: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email. Please try again later.");
        }
    }
}
