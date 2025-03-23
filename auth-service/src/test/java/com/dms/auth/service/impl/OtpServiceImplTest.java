package com.dms.auth.service.impl;

import com.dms.auth.entity.OtpVerification;
import com.dms.auth.entity.RefreshToken;
import com.dms.auth.entity.Role;
import com.dms.auth.entity.User;
import com.dms.auth.enums.AppRole;
import com.dms.auth.exception.ResourceNotFoundException;
import com.dms.auth.repository.OtpVerificationRepository;
import com.dms.auth.repository.UserRepository;
import com.dms.auth.security.jwt.JwtUtils;
import com.dms.auth.security.response.TokenResponse;
import com.dms.auth.security.service.CustomUserDetails;
import com.dms.auth.service.PublishEventService;
import com.dms.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OtpServiceImplTest {

    @Mock
    private OtpVerificationRepository otpVerificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PublishEventService publishEventService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private OtpServiceImpl otpService;

    private User user;
    private OtpVerification otpVerification;
    private static final String USERNAME = "testuser";
    private static final String EMAIL = "test@example.com";
    private static final String OTP = "123456";

    @BeforeEach
    void setUp() {
        // Set up test data
        Role role = new Role();
        role.setRoleId(UUID.randomUUID());
        role.setRoleName(AppRole.ROLE_USER);

        user = new User();
        user.setUserId(UUID.randomUUID());
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);
        user.setEnabled(false);
        user.setRole(role);

        otpVerification = new OtpVerification();
        otpVerification.setId(UUID.randomUUID());
        otpVerification.setEmail(EMAIL);
        otpVerification.setOtp(OTP);
        otpVerification.setUser(user);
        otpVerification.setValidated(false);
        otpVerification.setAttemptCount(0);
        otpVerification.setExpiryTime(Instant.now().plus(5, ChronoUnit.MINUTES));

        // Set private fields using ReflectionTestUtils
        ReflectionTestUtils.setField(otpService, "otpExpiryMinutes", 5);
        ReflectionTestUtils.setField(otpService, "lockMinutes", 30);
    }

    @Test
    void resendOtp_ShouldThrowResourceNotFoundException_WhenUserNotFound() {
        // Arrange
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> otpService.resendOtp(USERNAME));
        verify(userRepository).findByUsername(USERNAME);
        verifyNoInteractions(publishEventService);
    }

    @Test
    void resendOtp_ShouldThrowIllegalStateException_WhenUserAlreadyEnabled() {
        // Arrange
        user.setEnabled(true);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> otpService.resendOtp(USERNAME));
        verify(userRepository).findByUsername(USERNAME);
        verifyNoInteractions(publishEventService);
    }

    @Test
    void resendOtp_ShouldGenerateAndSendNewOtp_WhenUserValid() {
        // Arrange
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        doNothing().when(publishEventService).sendOtpEmail(any(User.class), anyString());
        when(otpVerificationRepository.save(any(OtpVerification.class))).thenReturn(otpVerification);

        // Act
        otpService.resendOtp(USERNAME);

        // Assert
        verify(userRepository).findByUsername(USERNAME);
        verify(otpVerificationRepository).findByEmailAndValidatedFalse(EMAIL);
        verify(otpVerificationRepository).save(any(OtpVerification.class));
        verify(publishEventService).sendOtpEmail(eq(user), anyString());
    }

    @Test
    void generateAndSendOtp_ShouldThrowIllegalStateException_WhenUserLocked() {
        // Arrange
        when(otpVerificationRepository.existsByEmailAndLockedUntilAfter(eq(EMAIL), any(Instant.class)))
                .thenReturn(true);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> otpService.generateAndSendOtp(user));
        verify(otpVerificationRepository).existsByEmailAndLockedUntilAfter(eq(EMAIL), any(Instant.class));
        verifyNoInteractions(publishEventService);
    }

    @Test
    void generateAndSendOtp_ShouldCreateNewOtp_WhenNoExistingOtp() {
        // Arrange
        when(otpVerificationRepository.existsByEmailAndLockedUntilAfter(eq(EMAIL), any(Instant.class)))
                .thenReturn(false);
        when(otpVerificationRepository.findByEmailAndValidatedFalse(EMAIL))
                .thenReturn(Optional.empty());
        when(otpVerificationRepository.save(any(OtpVerification.class)))
                .thenReturn(otpVerification);
        doNothing().when(publishEventService).sendOtpEmail(any(User.class), anyString());

        // Act
        otpService.generateAndSendOtp(user);

        // Assert
        verify(otpVerificationRepository).existsByEmailAndLockedUntilAfter(eq(EMAIL), any(Instant.class));
        verify(otpVerificationRepository).findByEmailAndValidatedFalse(EMAIL);
        verify(otpVerificationRepository).save(any(OtpVerification.class));
        verify(publishEventService).sendOtpEmail(eq(user), anyString());
    }

    @Test
    void generateAndSendOtp_ShouldUpdateExistingOtp_WhenOtpExists() {
        // Arrange
        when(otpVerificationRepository.existsByEmailAndLockedUntilAfter(eq(EMAIL), any(Instant.class)))
                .thenReturn(false);
        when(otpVerificationRepository.findByEmailAndValidatedFalse(EMAIL))
                .thenReturn(Optional.of(otpVerification));
        when(otpVerificationRepository.save(any(OtpVerification.class)))
                .thenReturn(otpVerification);
        doNothing().when(publishEventService).sendOtpEmail(any(User.class), anyString());

        // Act
        otpService.generateAndSendOtp(user);

        // Assert
        verify(otpVerificationRepository).existsByEmailAndLockedUntilAfter(eq(EMAIL), any(Instant.class));
        verify(otpVerificationRepository).findByEmailAndValidatedFalse(EMAIL);
        verify(otpVerificationRepository).save(any(OtpVerification.class));
        verify(publishEventService).sendOtpEmail(eq(user), anyString());
    }

    @Test
    void verifyOtp_ShouldThrowIllegalStateException_WhenNoValidOtpFound() {
        // Arrange
        when(otpVerificationRepository.findValidOtpByUsername(USERNAME))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> otpService.verifyOtp(USERNAME, OTP, httpServletRequest));
        verify(otpVerificationRepository).findValidOtpByUsername(USERNAME);
    }

    @Test
    void verifyOtp_ShouldReturnTokenResponse_WhenOtpLocked() {
        // Arrange
        otpVerification.setLockedUntil(Instant.now().plus(30, ChronoUnit.MINUTES));
        when(otpVerificationRepository.findValidOtpByUsername(USERNAME))
                .thenReturn(Optional.of(otpVerification));

        // Act
        TokenResponse response = otpService.verifyOtp(USERNAME, OTP, httpServletRequest);

        // Assert
        verify(otpVerificationRepository).findValidOtpByUsername(USERNAME);
        assertTrue(response.isLocked());
        assertEquals(0, response.getOtpCount());
        assertFalse(response.isVerified());
        assertFalse(response.isExpired());
        verifyNoMoreInteractions(otpVerificationRepository);
    }

    @Test
    void verifyOtp_ShouldReturnTokenResponse_WhenOtpExpired() {
        // Arrange
        otpVerification.setExpiryTime(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(otpVerificationRepository.findValidOtpByUsername(USERNAME))
                .thenReturn(Optional.of(otpVerification));

        // Act
        TokenResponse response = otpService.verifyOtp(USERNAME, OTP, httpServletRequest);

        // Assert
        verify(otpVerificationRepository).findValidOtpByUsername(USERNAME);
        assertFalse(response.isLocked());
        assertEquals(0, response.getOtpCount());
        assertFalse(response.isVerified());
        assertTrue(response.isExpired());
        verifyNoMoreInteractions(otpVerificationRepository);
    }

    @Test
    void verifyOtp_ShouldResetLockAndAttemptCount_WhenPreviouslyLockedButNowUnlocked() {
        // Arrange
        otpVerification.setLockedUntil(Instant.now().minus(1, ChronoUnit.HOURS)); // Locked in the past
        otpVerification.setAttemptCount(3);

        OtpVerification savedOtp = new OtpVerification();
        savedOtp.setAttemptCount(1); // Incremented from 0 to 1
        savedOtp.setValidated(false);
        savedOtp.setLockedUntil(null);
        savedOtp.setExpiryTime(Instant.now().plus(5, ChronoUnit.MINUTES)); // Not expired

        when(otpVerificationRepository.findValidOtpByUsername(USERNAME))
                .thenReturn(Optional.of(otpVerification));
        when(otpVerificationRepository.save(any(OtpVerification.class)))
                .thenReturn(savedOtp);

        // Act
        TokenResponse response = otpService.verifyOtp(USERNAME, "wrongOtp", httpServletRequest);

        // Assert
        verify(otpVerificationRepository).findValidOtpByUsername(USERNAME);
        verify(otpVerificationRepository).save(otpVerification);

        assertEquals(1, response.getOtpCount());
        assertFalse(response.isLocked());
        assertFalse(response.isVerified());
        assertFalse(response.isExpired());

        // Verify the OTP entity was updated
        assertNull(otpVerification.getLockedUntil());
        assertEquals(1, otpVerification.getAttemptCount());
    }

    @Test
    void verifyOtp_ShouldLockAccount_WhenMaxAttemptsExceeded() {
        // Arrange
        otpVerification.setAttemptCount(4); // One more attempt will exceed the max

        OtpVerification savedOtp = new OtpVerification();
        savedOtp.setAttemptCount(5);
        savedOtp.setValidated(false);
        savedOtp.setExpiryTime(Instant.now().plus(5, ChronoUnit.MINUTES)); // Not expired
        // This is important - we need to make isLocked() return true for the saved OTP
        Instant lockTime = Instant.now().plus(30, ChronoUnit.MINUTES);
        savedOtp.setLockedUntil(lockTime);

        when(otpVerificationRepository.findValidOtpByUsername(USERNAME))
                .thenReturn(Optional.of(otpVerification));
        when(otpVerificationRepository.save(any(OtpVerification.class))).thenAnswer(invocation -> {
            OtpVerification savedVerification = invocation.getArgument(0);
            // Update the mutable otpVerification that's passed to save()
            savedVerification.setAttemptCount(5);
            savedVerification.setLockedUntil(lockTime);
            return savedOtp;
        });

        // Act
        TokenResponse response = otpService.verifyOtp(USERNAME, "wrongOtp", httpServletRequest);

        // Assert
        verify(otpVerificationRepository).findValidOtpByUsername(USERNAME);
        verify(otpVerificationRepository).save(otpVerification);

        // The TokenResponse is created based on the saved entity
        assertEquals(5, response.getOtpCount());
        assertTrue(response.isLocked());
        assertFalse(response.isVerified());
        assertFalse(response.isExpired());

        // Verify the OTP entity was updated with a lock time
        assertNotNull(otpVerification.getLockedUntil());
        assertEquals(5, otpVerification.getAttemptCount());

        // Double check that the saved object has the correct values
        assertTrue(savedOtp.isLocked());
    }

    @Test
    void verifyOtp_ShouldReturnTokenResponse_WhenOtpInvalid() {
        // Arrange
        OtpVerification savedOtp = new OtpVerification();
        savedOtp.setAttemptCount(1);
        savedOtp.setValidated(false);
        savedOtp.setExpiryTime(Instant.now().plus(5, ChronoUnit.MINUTES)); // Not expired

        when(otpVerificationRepository.findValidOtpByUsername(USERNAME))
                .thenReturn(Optional.of(otpVerification));
        when(otpVerificationRepository.save(any(OtpVerification.class)))
                .thenReturn(savedOtp);

        // Act
        TokenResponse response = otpService.verifyOtp(USERNAME, "wrongOtp", httpServletRequest);

        // Assert
        verify(otpVerificationRepository).findValidOtpByUsername(USERNAME);
        verify(otpVerificationRepository).save(otpVerification);

        assertEquals(1, response.getOtpCount());
        assertFalse(response.isLocked());
        assertFalse(response.isVerified());
        assertFalse(response.isExpired());
    }

    @Test
    void verifyOtp_ShouldCreateTokenAndUpdateUser_WhenOtpValid() {
        // Arrange
        OtpVerification savedOtp = new OtpVerification();
        savedOtp.setAttemptCount(1);
        savedOtp.setValidated(true);
        savedOtp.setExpiryTime(Instant.now().plus(5, ChronoUnit.MINUTES)); // Not expired

        when(otpVerificationRepository.findValidOtpByUsername(USERNAME))
                .thenReturn(Optional.of(otpVerification));
        when(otpVerificationRepository.save(any(OtpVerification.class)))
                .thenReturn(savedOtp);

        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        // Setup refresh token
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");
        when(refreshTokenService.createRefreshToken(any(User.class), any(HttpServletRequest.class)))
                .thenReturn(refreshToken);

        // Setup JWT token
        when(jwtUtils.generateTokenFromUsername(any(CustomUserDetails.class)))
                .thenReturn("jwt-token");

        // Act
        TokenResponse response = otpService.verifyOtp(USERNAME, OTP, httpServletRequest);

        // Assert
        verify(otpVerificationRepository).findValidOtpByUsername(USERNAME);
        verify(userRepository).findByUsername(USERNAME);
        verify(userRepository).save(user);
        verify(refreshTokenService).createRefreshToken(eq(user), eq(httpServletRequest));
        verify(jwtUtils).generateTokenFromUsername(any(CustomUserDetails.class));
        verify(otpVerificationRepository).save(otpVerification);

        // Verify user was updated
        assertTrue(user.isEnabled());

        // Verify token response
        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(USERNAME, response.getUsername());
        assertEquals(1, response.getOtpCount());
        assertTrue(response.isVerified());
        assertFalse(response.isLocked());
        assertFalse(response.isExpired());
    }
}