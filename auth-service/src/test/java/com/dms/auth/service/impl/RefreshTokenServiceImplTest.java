package com.dms.auth.service.impl;

import com.dms.auth.entity.RefreshToken;
import com.dms.auth.entity.Role;
import com.dms.auth.entity.User;
import com.dms.auth.enums.AppRole;
import com.dms.auth.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    @Mock
    private HttpServletRequest httpServletRequest;

    private User testUser;
    private RefreshToken validRefreshToken;
    private RefreshToken expiredRefreshToken;
    private RefreshToken revokedRefreshToken;
    private final UUID userId = UUID.randomUUID();
    private final String tokenString = UUID.randomUUID().toString();
    private final String userAgent = "Mozilla/5.0";
    private final String ipAddress = "127.0.0.1";

    @BeforeEach
    void setUp() {
        // Set refresh token expiration time to 24 hours
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationMs", 86400000L);

        // Setup test user
        Role role = new Role();
        role.setRoleName(AppRole.ROLE_USER);

        testUser = new User();
        testUser.setUserId(userId);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("password");
        testUser.setRole(role);
        testUser.setEnabled(true);

        // Setup valid refresh token
        validRefreshToken = new RefreshToken();
        validRefreshToken.setId(UUID.randomUUID());
        validRefreshToken.setToken(tokenString);
        validRefreshToken.setUser(testUser);
        validRefreshToken.setExpiryDate(Instant.now().plusMillis(86400000L)); // 24 hours in future
        validRefreshToken.setRevoked(false);
        validRefreshToken.setUserAgent(userAgent);
        validRefreshToken.setIpAddress(ipAddress);
        validRefreshToken.setCreatedAt(Instant.now());
        validRefreshToken.setCreatedBy(testUser.getUsername());
        validRefreshToken.setUpdatedBy(testUser.getUsername());

        // Setup expired refresh token
        expiredRefreshToken = new RefreshToken();
        expiredRefreshToken.setId(UUID.randomUUID());
        expiredRefreshToken.setToken("expiredToken");
        expiredRefreshToken.setUser(testUser);
        expiredRefreshToken.setExpiryDate(Instant.now().minusMillis(86400000L)); // 24 hours in past
        expiredRefreshToken.setRevoked(false);

        // Setup revoked refresh token
        revokedRefreshToken = new RefreshToken();
        revokedRefreshToken.setId(UUID.randomUUID());
        revokedRefreshToken.setToken("revokedToken");
        revokedRefreshToken.setUser(testUser);
        revokedRefreshToken.setExpiryDate(Instant.now().plusMillis(86400000L)); // 24 hours in future
        revokedRefreshToken.setRevoked(true);
    }

    @Test
    void testCreateRefreshToken() {
        // Given
        when(httpServletRequest.getHeader("User-Agent")).thenReturn(userAgent);
        when(httpServletRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // When
        RefreshToken result = refreshTokenService.createRefreshToken(testUser, httpServletRequest);

        // Then
        assertNotNull(result);
        assertEquals(testUser, result.getUser());
        assertEquals(userAgent, result.getUserAgent());
        assertEquals(ipAddress, result.getIpAddress());
        assertFalse(result.isRevoked());

        // Verify token expiry is set correctly (within a small tolerance)
        long expectedExpiryEpochMilli = Instant.now().plusMillis(86400000L).toEpochMilli();
        long actualExpiryEpochMilli = result.getExpiryDate().toEpochMilli();
        assertTrue(Math.abs(expectedExpiryEpochMilli - actualExpiryEpochMilli) < 1000,
                "Token expiry date should be approximately 24 hours in future");

        // Verify interactions
        verify(refreshTokenRepository).revokeAllUserTokens(testUser);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void testFindByToken() {
        // Given
        when(refreshTokenRepository.findByToken(tokenString)).thenReturn(Optional.of(validRefreshToken));

        // When
        Optional<RefreshToken> result = refreshTokenService.findByToken(tokenString);

        // Then
        assertTrue(result.isPresent());
        assertEquals(validRefreshToken, result.get());
        verify(refreshTokenRepository).findByToken(tokenString);
    }

    @Test
    void testVerifyExpiration_ValidToken() {
        // When
        RefreshToken result = refreshTokenService.verifyExpiration(validRefreshToken);

        // Then
        assertEquals(validRefreshToken, result);
        // No interactions with repository expected
    }

    @Test
    void testVerifyExpiration_ExpiredToken() {
        // When & Then
        Exception exception = assertThrows(RuntimeException.class, () -> {
            refreshTokenService.verifyExpiration(expiredRefreshToken);
        });

        assertEquals("Refresh token was expired. Please make a new signin request", exception.getMessage());
        verify(refreshTokenRepository).delete(expiredRefreshToken);
    }

    @Test
    void testVerifyExpiration_RevokedToken() {
        // When & Then
        Exception exception = assertThrows(RuntimeException.class, () -> {
            refreshTokenService.verifyExpiration(revokedRefreshToken);
        });

        assertEquals("Refresh token was revoked. Please sign in again", exception.getMessage());
        // No delete interaction expected for revoked tokens
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
    }

    @Test
    void testRevokeToken_WhenTokenExists() {
        // Given
        when(refreshTokenRepository.findByToken(tokenString)).thenReturn(Optional.of(validRefreshToken));

        // When
        refreshTokenService.revokeToken(tokenString);

        // Then
        assertTrue(validRefreshToken.isRevoked());
        verify(refreshTokenRepository).findByToken(tokenString);
        verify(refreshTokenRepository).save(validRefreshToken);
    }

    @Test
    void testRevokeToken_WhenTokenDoesNotExist() {
        // Given
        when(refreshTokenRepository.findByToken(tokenString)).thenReturn(Optional.empty());

        // When
        refreshTokenService.revokeToken(tokenString);

        // Then
        verify(refreshTokenRepository).findByToken(tokenString);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void testRevokeAllUserTokens() {
        // When
        refreshTokenService.revokeAllUserTokens(testUser);

        // Then
        verify(refreshTokenRepository).revokeAllUserTokens(testUser);
    }

    @Test
    void testFindActiveTokensByUser() {
        // Given
        List<RefreshToken> expectedTokens = Arrays.asList(validRefreshToken);
        when(refreshTokenRepository.findActiveTokensByUser(testUser)).thenReturn(expectedTokens);

        // When
        List<RefreshToken> result = refreshTokenService.findActiveTokensByUser(testUser);

        // Then
        assertEquals(expectedTokens, result);
        verify(refreshTokenRepository).findActiveTokensByUser(testUser);
    }

    @Test
    void testRemoveExpiredTokens() {
        // When
        refreshTokenService.removeExpiredTokens();

        // Then
        verify(refreshTokenRepository).deleteAllExpiredTokens(any(Instant.class));
    }
}