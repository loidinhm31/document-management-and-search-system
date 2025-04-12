package com.dms.auth.service;

import com.dms.auth.entity.User;

/**
 * Interface for publishing events to the message broker.
 * This service handles higher-level business logic for creating and publishing events.
 */
public interface PublishEventService {

    /**
     * Send an OTP email notification
     *
     * @param user The user to send the OTP to
     * @param otp The OTP code
     */
    void sendOtpEmail(User user, String otp);

    /**
     * Send a password reset email notification
     *
     * @param user The user to send the password reset to
     * @param token The reset token
     * @param expiryMinutes Token expiration time in hours
     */
    void sendPasswordResetEmail(User user, String token, int expiryMinutes);
}