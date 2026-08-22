package com.outpass.portal.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@Slf4j
public class EmailService {

    private static final String DEFAULT_FRONTEND_URL = "http://localhost:5173";

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.frontend.url:" + DEFAULT_FRONTEND_URL + "}")
    private String frontendUrl;

    public boolean isConfigured() {
        return mailSender != null && !fromEmail.isBlank();
    }

    @PostConstruct
    void logConfigurationState() {
        if (!isConfigured()) {
            log.warn("Email is NOT configured (MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD missing) — "
                    + "verification and password reset emails will not be sent until this is fixed.");
        }
        if (isConfigured() && DEFAULT_FRONTEND_URL.equals(frontendUrl)) {
            // Mail is live but the frontend URL was never overridden — almost certainly a
            // misconfigured production deploy about to send real emails with broken links.
            log.error("FRONTEND_URL is not set and mail IS configured — verification/reset "
                    + "emails will contain localhost links. Set the FRONTEND_URL environment variable.");
        }
    }

    public void sendVerificationEmail(String toEmail, String name, String token) {
        if (!isConfigured()) {
            log.warn("Email not configured — skipping verification email to {}", toEmail);
            return;
        }
        String link = frontendUrl + "/verify-email?token=" + token;
        String safeName = HtmlUtils.htmlEscape(name);
        String html = """
                <!DOCTYPE html>
                <html><body style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px;">
                  <h2 style="color:#c07a3a;">Verify Your Email</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>Thanks for registering! Click the button below to verify your email address and activate your account.</p>
                  <a href="%s" style="display:inline-block;padding:12px 28px;background:#ed8936;color:#fff;text-decoration:none;border-radius:6px;font-weight:600;margin:16px 0;">
                    Verify Email
                  </a>
                  <p style="color:#888;font-size:12px;margin-top:24px;">
                    This link expires in 24 hours. If you did not register, please ignore this email.
                  </p>
                  <p style="color:#888;font-size:12px;">Or copy this link: %s</p>
                </body></html>
                """.formatted(safeName, link, link);
        send(toEmail, "Verify Your Email — Hostel Management", html);
    }

    public void sendPasswordResetEmail(String toEmail, String name, String token) {
        if (!isConfigured()) {
            log.warn("Email not configured — skipping password reset email to {}", toEmail);
            return;
        }
        String link = frontendUrl + "/forgot-password?token=" + token;
        String safeName = HtmlUtils.htmlEscape(name);
        String html = """
                <!DOCTYPE html>
                <html><body style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px;">
                  <h2 style="color:#c07a3a;">Reset Your Password</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>Click the button below to reset your password. This link is valid for 15 minutes.</p>
                  <a href="%s" style="display:inline-block;padding:12px 28px;background:#ed8936;color:#fff;text-decoration:none;border-radius:6px;font-weight:600;margin:16px 0;">
                    Reset Password
                  </a>
                  <p style="color:#888;font-size:12px;margin-top:24px;">
                    If you did not request this, please ignore this email. Your password will not change.
                  </p>
                  <p style="color:#888;font-size:12px;">Or copy this link: %s</p>
                </body></html>
                """.formatted(safeName, link, link);
        send(toEmail, "Reset Your Password — Hostel Management", html);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            // Log full detail server-side only; never let SMTP host/port/auth or
            // provider-specific exception text reach an API response.
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email. Please try again later.");
        }
    }
}
