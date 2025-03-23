package com.dms.processor.service;

import jakarta.mail.MessagingException;

public interface AuthEmailService {
    /**
     * Sends an OTP verification email to the specified recipient.
     *
     * @param to            Email address of the recipient
     * @param username      Username of the recipient
     * @param otp           One-time password for verification
     * @param expiryMinutes OTP expiry time in minutes
     * @param maxAttempts   Maximum number of verification attempts allowed
     * @throws MessagingException If there is an error sending the email
     */
    void sendOtpEmail(String to, String username, String otp, int expiryMinutes, int maxAttempts) throws MessagingException;

    /**
     * Sends a password reset email to the specified recipient.
     *
     * @param to            Email address of the recipient
     * @param username      Username of the recipient
     * @param token         Reset password token
     * @param expiryMinutes Token expiry time in minutes
     * @throws MessagingException If there is an error sending the email
     */
    void sendPasswordResetEmail(String to, String username, String token, int expiryMinutes) throws MessagingException;
}