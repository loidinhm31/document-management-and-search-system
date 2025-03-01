package com.dms.processor.service;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@Slf4j
public class AuthEmailService extends EmailService {
    @Value("${app.base-url}")
    private String baseUrl;

    @Autowired
    private TemplateEngine templateEngine;

    public void sendOtpEmail(String to, String username, String otp,
                             int expiryMinutes, int maxAttempts) throws MessagingException {
        Context context = new Context();
        context.setVariable("username", username);
        context.setVariable("otp", otp);
        context.setVariable("expiryMinutes", expiryMinutes);
        context.setVariable("maxAttempts", maxAttempts);

        String htmlContent = templateEngine.process("otp-verification", context);

        sendEmail(to, "OTP Verification", htmlContent);
    }

    public void sendPasswordResetEmail(String to, String username, String token, int expiryMinutes) throws MessagingException {
        String resetUrl = String.format("%s/reset-password?token=%s", baseUrl, token);

        Context context = new Context();
        context.setVariable("username", username);
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("expiryMinutes", expiryMinutes);

        String htmlContent = templateEngine.process("password-reset", context);

        sendEmail(to, "Password Reset Request", htmlContent);
    }
}
