package com.dms.processor.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthEmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    @Spy
    @InjectMocks
    private AuthEmailServiceImpl authEmailService;

    @Captor
    private ArgumentCaptor<Context> contextCaptor;

    private final String fromEmail = "test@example.com";
    private final String baseUrl = "https://dms-example.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authEmailService, "fromEmail", fromEmail);
        ReflectionTestUtils.setField(authEmailService, "baseUrl", baseUrl);
    }

    @Test
    void sendOtpEmail_ShouldRenderTemplateAndSendEmail() throws MessagingException {
        // Arrange
        String to = "user@example.com";
        String username = "testUser";
        String otp = "123456";
        int expiryMinutes = 5;
        int maxAttempts = 3;
        String expectedTemplate = "otp-verification";
        String renderedHtml = "<html>OTP Email Content</html>";

        when(templateEngine.process(eq(expectedTemplate), any(Context.class))).thenReturn(renderedHtml);
        doNothing().when(authEmailService).sendEmail(anyString(), anyString(), anyString());

        // Act
        authEmailService.sendOtpEmail(to, username, otp, expiryMinutes, maxAttempts);

        // Assert
        verify(templateEngine).process(eq(expectedTemplate), contextCaptor.capture());
        Context capturedContext = contextCaptor.getValue();
        assertEquals(username, capturedContext.getVariable("username"));
        assertEquals(otp, capturedContext.getVariable("otp"));
        assertEquals(expiryMinutes, capturedContext.getVariable("expiryMinutes"));
        assertEquals(maxAttempts, capturedContext.getVariable("maxAttempts"));

        verify(authEmailService).sendEmail(eq(to), eq("OTP Verification"), eq(renderedHtml));
    }

    @Test
    void sendOtpEmail_WhenMessagingExceptionOccurs_ShouldPropagateException() throws MessagingException {
        // Arrange
        String to = "user@example.com";
        String username = "testUser";
        String otp = "123456";
        int expiryMinutes = 5;
        int maxAttempts = 3;
        String expectedTemplate = "otp-verification";
        String renderedHtml = "<html>OTP Email Content</html>";

        when(templateEngine.process(eq(expectedTemplate), any(Context.class))).thenReturn(renderedHtml);
        doThrow(new MessagingException("Failed to send email")).when(authEmailService).sendEmail(anyString(), anyString(), anyString());

        // Act & Assert
        MessagingException exception = assertThrows(MessagingException.class, () ->
                authEmailService.sendOtpEmail(to, username, otp, expiryMinutes, maxAttempts)
        );
        assertEquals("Failed to send email", exception.getMessage());
    }

    @Test
    void sendPasswordResetEmail_ShouldRenderTemplateAndSendEmail() throws MessagingException {
        // Arrange
        String to = "user@example.com";
        String username = "testUser";
        String token = "reset-token-123";
        int expiryMinutes = 60;
        String expectedTemplate = "password-reset";
        String expectedResetUrl = baseUrl + "/reset-password?token=" + token;
        String renderedHtml = "<html>Password Reset Email Content</html>";

        when(templateEngine.process(eq(expectedTemplate), any(Context.class))).thenReturn(renderedHtml);
        doNothing().when(authEmailService).sendEmail(anyString(), anyString(), anyString());

        // Act
        authEmailService.sendPasswordResetEmail(to, username, token, expiryMinutes);

        // Assert
        verify(templateEngine).process(eq(expectedTemplate), contextCaptor.capture());
        Context capturedContext = contextCaptor.getValue();
        assertEquals(username, capturedContext.getVariable("username"));
        assertEquals(expectedResetUrl, capturedContext.getVariable("resetUrl"));
        assertEquals(expiryMinutes, capturedContext.getVariable("expiryMinutes"));

        verify(authEmailService).sendEmail(eq(to), eq("Password Reset Request"), eq(renderedHtml));
    }

    @Test
    void sendPasswordResetEmail_WhenMessagingExceptionOccurs_ShouldPropagateException() throws MessagingException {
        // Arrange
        String to = "user@example.com";
        String username = "testUser";
        String token = "reset-token-123";
        int expiryMinutes = 60;
        String expectedTemplate = "password-reset";
        String renderedHtml = "<html>Password Reset Email Content</html>";

        when(templateEngine.process(eq(expectedTemplate), any(Context.class))).thenReturn(renderedHtml);
        doThrow(new MessagingException("Failed to send email")).when(authEmailService).sendEmail(anyString(), anyString(), anyString());

        // Act & Assert
        MessagingException exception = assertThrows(MessagingException.class, () ->
                authEmailService.sendPasswordResetEmail(to, username, token, expiryMinutes)
        );
        assertEquals("Failed to send email", exception.getMessage());
    }

    @Test
    void sendPasswordResetEmail_ShouldFormatResetUrlCorrectly() throws MessagingException {
        // Arrange
        String to = "user@example.com";
        String username = "testUser";
        String token = "complex?token=with&special#chars";
        int expiryMinutes = 60;
        String expectedTemplate = "password-reset";
        // The token should be included as-is in the URL (URL encoding would be handled by the template)
        String expectedResetUrl = baseUrl + "/reset-password?token=" + token;
        String renderedHtml = "<html>Password Reset Email Content</html>";

        when(templateEngine.process(eq(expectedTemplate), any(Context.class))).thenReturn(renderedHtml);
        doNothing().when(authEmailService).sendEmail(anyString(), anyString(), anyString());

        // Act
        authEmailService.sendPasswordResetEmail(to, username, token, expiryMinutes);

        // Assert
        verify(templateEngine).process(eq(expectedTemplate), contextCaptor.capture());
        Context capturedContext = contextCaptor.getValue();
        assertEquals(expectedResetUrl, capturedContext.getVariable("resetUrl"));
    }

    @Test
    void sendOtpEmail_WithEmptyUsername_ShouldStillSendEmail() throws MessagingException {
        // Arrange
        String to = "user@example.com";
        String username = ""; // Empty username
        String otp = "123456";
        int expiryMinutes = 5;
        int maxAttempts = 3;
        String expectedTemplate = "otp-verification";
        String renderedHtml = "<html>OTP Email Content</html>";

        when(templateEngine.process(eq(expectedTemplate), any(Context.class))).thenReturn(renderedHtml);
        doNothing().when(authEmailService).sendEmail(anyString(), anyString(), anyString());

        // Act
        authEmailService.sendOtpEmail(to, username, otp, expiryMinutes, maxAttempts);

        // Assert
        verify(templateEngine).process(eq(expectedTemplate), contextCaptor.capture());
        Context capturedContext = contextCaptor.getValue();
        assertEquals(username, capturedContext.getVariable("username"));
        verify(authEmailService).sendEmail(eq(to), eq("OTP Verification"), eq(renderedHtml));
    }

    @Test
    void sendPasswordResetEmail_WithEmptyUsername_ShouldStillSendEmail() throws MessagingException {
        // Arrange
        String to = "user@example.com";
        String username = ""; // Empty username
        String token = "reset-token-123";
        int expiryMinutes = 60;
        String expectedTemplate = "password-reset";
        String renderedHtml = "<html>Password Reset Email Content</html>";

        when(templateEngine.process(eq(expectedTemplate), any(Context.class))).thenReturn(renderedHtml);
        doNothing().when(authEmailService).sendEmail(anyString(), anyString(), anyString());

        // Act
        authEmailService.sendPasswordResetEmail(to, username, token, expiryMinutes);

        // Assert
        verify(templateEngine).process(eq(expectedTemplate), contextCaptor.capture());
        Context capturedContext = contextCaptor.getValue();
        assertEquals(username, capturedContext.getVariable("username"));
        verify(authEmailService).sendEmail(eq(to), eq("Password Reset Request"), eq(renderedHtml));
    }
}