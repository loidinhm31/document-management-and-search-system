package com.dms.auth.service;

import com.dms.auth.entity.User;
import com.dms.auth.security.response.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Service for OTP (One-Time Password) generation, verification and management.
 */
public interface OtpService {

    /**
     * Resends OTP to the user with the specified username.
     *
     * @param username the username of the user
     * @throws com.dms.auth.exception.ResourceNotFoundException if user not found
     * @throws IllegalStateException if account is already verified
     */
    void resendOtp(String username);

    /**
     * Generates and sends OTP to the specified user.
     *
     * @param user the user to send OTP to
     * @throws IllegalStateException if account is temporarily locked
     */
    void generateAndSendOtp(User user);

    /**
     * Verifies the OTP provided by the user.
     *
     * @param username the username of the user
     * @param otp the OTP to verify
     * @param request the HTTP request
     * @return TokenResponse containing authentication tokens if verification is successful
     * @throws IllegalStateException if no valid OTP found
     */
    TokenResponse verifyOtp(String username, String otp, HttpServletRequest request);
}