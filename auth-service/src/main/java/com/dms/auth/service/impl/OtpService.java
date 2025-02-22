package com.dms.auth.service.impl;

import com.dms.auth.entity.OtpVerification;
import com.dms.auth.entity.User;
import com.dms.auth.exception.ResourceNotFoundException;
import com.dms.auth.repository.OtpVerificationRepository;
import com.dms.auth.repository.UserRepository;
import com.dms.auth.security.response.TokenResponse;
import com.dms.auth.security.service.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Random;

@Service
@Slf4j
public class OtpService extends BaseService {
    @Autowired
    private OtpVerificationRepository otpVerificationRepository;

    @Autowired
    private PublishEventService publishEventService;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${app.otp.lock-minutes:30}")
    private int lockMinutes;

    private final Random random = new SecureRandom();

    @Transactional
    public void resendOtp(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isEnabled()) {
            throw new IllegalStateException("Account is already verified");
        }

        generateAndSendOtp(user);
    }

    public void generateAndSendOtp(User user) {
        // Check if user is locked
        if (otpVerificationRepository.existsByEmailAndLockedUntilAfter(
                user.getEmail(), Instant.now())) {
            throw new IllegalStateException("Account is temporarily locked. Please try again later.");
        }

        // Generate 6-digit OTP
        String otp = String.format("%06d", random.nextInt(1000000));

        // Save or update OTP
        OtpVerification verification = otpVerificationRepository
                .findByEmailAndValidatedFalse(user.getEmail())
                .orElse(new OtpVerification());

        verification.setOtp(otp);
        verification.setEmail(user.getEmail());
        verification.setUser(user);
        verification.setExpiryTime(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES));
        verification.setAttemptCount(0);
        verification.setValidated(false);
        verification.setLockedUntil(null);
        verification.setCreatedAt(Instant.now());
        verification.setCreatedBy(user.getUsername());
        verification.setUpdatedBy(user.getUsername());

        otpVerificationRepository.save(verification);

        // Send OTP
        publishEventService.sendOtpEmail(user, otp);
    }

    @Transactional
    public TokenResponse verifyOtp(String username, String otp, HttpServletRequest request) {
        OtpVerification verification = otpVerificationRepository
                .findValidOtpByUsername(username)
                .orElseThrow(() -> new IllegalStateException("No valid OTP found"));

        if (verification.isLocked()) {
            return new TokenResponse(
                    verification.getAttemptCount(),
                    verification.isLocked(),
                    verification.isValidated()
            );
        } else {
            if (Objects.nonNull(verification.getLockedUntil())) { // If locked before then reset
                verification.setLockedUntil(null);
                verification.setAttemptCount(0);
            }
        }

        verification.setAttemptCount(verification.getAttemptCount() + 1);

        if (verification.hasExceededMaxAttempts()) {
            verification.setLockedUntil(Instant.now().plus(lockMinutes, ChronoUnit.MINUTES));
            OtpVerification savedOtp = otpVerificationRepository.save(verification);
            return new TokenResponse(
                    savedOtp.getAttemptCount(),
                    savedOtp.isValidated(),
                    savedOtp.isLocked()
            );
        }

        TokenResponse tokenResponse = null;
        if (StringUtils.equals(verification.getOtp(), otp)) {
            verification.setValidated(true);

            User user = userRepository.findByUsername(username)
                    .orElse(null);
            if (Objects.nonNull(user)) {
                // Save verified status
                user.setEnabled(true);
                userRepository.save(user);

                CustomUserDetails userDetails = CustomUserDetails.build(user);
                tokenResponse = createToken(user, userDetails, request);
            }
        }

        OtpVerification savedOtp = otpVerificationRepository.save(verification);

        return Objects.isNull(tokenResponse) ? new TokenResponse(
                savedOtp.getAttemptCount(),
                savedOtp.isValidated(),
                savedOtp.isLocked()
        ) : tokenResponse
                .withOtpCount(savedOtp.getAttemptCount())
                .withVerified(verification.isValidated())
                .withLocked(verification.isLocked());
    }
}