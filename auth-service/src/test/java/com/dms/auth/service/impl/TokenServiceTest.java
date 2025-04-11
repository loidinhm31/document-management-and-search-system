package com.dms.auth.service.impl;

import com.dms.auth.entity.AuthToken;
import com.dms.auth.entity.Role;
import com.dms.auth.entity.User;
import com.dms.auth.enums.AppRole;
import com.dms.auth.enums.TokenType;
import com.dms.auth.repository.AuthTokenRepository;
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
public class TokenServiceTest {

    @Mock
    private AuthTokenRepository authTokenRepository;

    @InjectMocks
    private TokenServiceImpl tokenService;

    @Mock
    private HttpServletRequest httpServletRequest;

    private User testUser;
    private AuthToken validRefreshToken;
    private AuthToken expiredRefreshToken;
    private AuthToken revokedRefreshToken;
    private AuthToken validAccessToken;
    private final UUID userId = UUID.randomUUID();
    private final String refreshTokenString = UUID.randomUUID().toString();
    private final String accessTokenString = UUID.randomUUID().toString();
    private final String userAgent = "Mozilla/5.0";
    private final String ipAddress = "127.0.0.1";

    @BeforeEach
    void setUp() {
        // Set token expiration times
        ReflectionTestUtils.setField(tokenService, "refreshTokenDurationMs", 86400000L); // 24 hours
        ReflectionTestUtils.setField(tokenService, "accessTokenDurationMs", 3600000L); // 1 hour

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
        validRefreshToken = new AuthToken();
        validRefreshToken.setId(UUID.randomUUID());
        validRefreshToken.setToken(refreshTokenString);
        validRefreshToken.setUser(testUser);
        validRefreshToken.setTokenType(TokenType.REFRESH);
        validRefreshToken.setExpiryDate(Instant.now().plusMillis(86400000L));
        validRefreshToken.setRevoked(false);
        validRefreshToken.setUserAgent(userAgent);
        validRefreshToken.setIpAddress(ipAddress);
        validRefreshToken.setCreatedAt(Instant.now());
        validRefreshToken.setCreatedBy(testUser.getUsername());
        validRefreshToken.setUpdatedBy(testUser.getUsername());

        // Setup expired refresh token
        expiredRefreshToken = new AuthToken();
        expiredRefreshToken.setId(UUID.randomUUID());
        expiredRefreshToken.setToken("expiredToken");
        expiredRefreshToken.setUser(testUser);
        expiredRefreshToken.setTokenType(TokenType.REFRESH);
        expiredRefreshToken.setExpiryDate(Instant.now().minusMillis(86400000L));
        expiredRefreshToken.setRevoked(false);

        // Setup revoked refresh token
        revokedRefreshToken = new AuthToken();
        revokedRefreshToken.setId(UUID.randomUUID());
        revokedRefreshToken.setToken("revokedToken");
        revokedRefreshToken.setUser(testUser);
        revokedRefreshToken.setTokenType(TokenType.REFRESH);
        revokedRefreshToken.setExpiryDate(Instant.now().plusMillis(86400000L));
        revokedRefreshToken.setRevoked(true);

        // Setup valid access token
        validAccessToken = new AuthToken();
        validAccessToken.setId(UUID.randomUUID());
        validAccessToken.setToken(accessTokenString);
        validAccessToken.setUser(testUser);
        validAccessToken.setTokenType(TokenType.ACCESS);
        validAccessToken.setExpiryDate(Instant.now().plusMillis(3600000L));
        validAccessToken.setRevoked(false);
        validAccessToken.setUserAgent(userAgent);
        validAccessToken.setIpAddress(ipAddress);
        validAccessToken.setCreatedAt(Instant.now());
        validAccessToken.setCreatedBy(testUser.getUsername());
        validAccessToken.setUpdatedBy(testUser.getUsername());
    }

    @Test
    void testCreateRefreshToken() {
        // Given
        when(httpServletRequest.getHeader("User-Agent")).thenReturn(userAgent);
        when(httpServletRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(i -> i.getArgument(0));

        // When
        AuthToken result = tokenService.createRefreshToken(testUser, httpServletRequest);

        // Then
        assertNotNull(result);
        assertEquals(testUser, result.getUser());
        assertEquals(TokenType.REFRESH, result.getTokenType());
        assertEquals(userAgent, result.getUserAgent());
        assertEquals(ipAddress, result.getIpAddress());
        assertFalse(result.isRevoked());

        // Verify token expiry
        long expectedExpiryEpochMilli = Instant.now().plusMillis(86400000L).toEpochMilli();
        long actualExpiryEpochMilli = result.getExpiryDate().toEpochMilli();
        assertTrue(Math.abs(expectedExpiryEpochMilli - actualExpiryEpochMilli) < 1000);

        // Verify interactions
        verify(authTokenRepository).revokeAllUserTokensByType(testUser, TokenType.REFRESH);
        verify(authTokenRepository).save(any(AuthToken.class));
    }

    @Test
    void testCreateRefreshToken_NullUserAgent() {
        // Given
        when(httpServletRequest.getHeader("User-Agent")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(i -> i.getArgument(0));

        // When
        AuthToken result = tokenService.createRefreshToken(testUser, httpServletRequest);

        // Then
        assertNotNull(result);
        assertEquals(testUser, result.getUser());
        assertEquals(TokenType.REFRESH, result.getTokenType());
        assertNull(result.getUserAgent());
        assertEquals(ipAddress, result.getIpAddress());
        assertFalse(result.isRevoked());
        verify(authTokenRepository).revokeAllUserTokensByType(testUser, TokenType.REFRESH);
        verify(authTokenRepository).save(any(AuthToken.class));
    }

    @Test
    void testCreateAccessToken() {
        // Given
        String jwtToken = "sampleJwtToken";
        String expectedTokenHash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(jwtToken);
        when(httpServletRequest.getHeader("User-Agent")).thenReturn(userAgent);
        when(httpServletRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(i -> i.getArgument(0));

        // When
        AuthToken result = tokenService.createAccessToken(testUser, jwtToken, httpServletRequest);

        // Then
        assertNotNull(result);
        assertEquals(testUser, result.getUser());
        assertEquals(TokenType.ACCESS, result.getTokenType());
        assertEquals(expectedTokenHash, result.getToken());
        assertEquals(userAgent, result.getUserAgent());
        assertEquals(ipAddress, result.getIpAddress());
        assertFalse(result.isRevoked());

        // Verify token expiry
        long expectedExpiryEpochMilli = Instant.now().plusMillis(3600000L).toEpochMilli();
        long actualExpiryEpochMilli = result.getExpiryDate().toEpochMilli();
        assertTrue(Math.abs(expectedExpiryEpochMilli - actualExpiryEpochMilli) < 1000);

        verify(authTokenRepository).save(any(AuthToken.class));
    }

    @Test
    void testFindByToken_TokenExists() {
        // Given
        when(authTokenRepository.findByToken(refreshTokenString)).thenReturn(Optional.of(validRefreshToken));

        // When
        Optional<AuthToken> result = tokenService.findByToken(refreshTokenString);

        // Then
        assertTrue(result.isPresent());
        assertEquals(validRefreshToken, result.get());
        verify(authTokenRepository).findByToken(refreshTokenString);
    }

    @Test
    void testFindByToken_TokenDoesNotExist() {
        // Given
        String nonExistentToken = "nonExistentToken";
        when(authTokenRepository.findByToken(nonExistentToken)).thenReturn(Optional.empty());

        // When
        Optional<AuthToken> result = tokenService.findByToken(nonExistentToken);

        // Then
        assertFalse(result.isPresent());
        verify(authTokenRepository).findByToken(nonExistentToken);
    }

    @Test
    void testFindByTokenAndType_TokenExists() {
        // Given
        when(authTokenRepository.findByTokenAndTokenType(refreshTokenString, TokenType.REFRESH))
                .thenReturn(Optional.of(validRefreshToken));

        // When
        Optional<AuthToken> result = tokenService.findByTokenAndType(refreshTokenString, TokenType.REFRESH);

        // Then
        assertTrue(result.isPresent());
        assertEquals(validRefreshToken, result.get());
        verify(authTokenRepository).findByTokenAndTokenType(refreshTokenString, TokenType.REFRESH);
    }

    @Test
    void testFindByTokenAndType_TokenDoesNotExist() {
        // Given
        String nonExistentToken = "nonExistentToken";
        when(authTokenRepository.findByTokenAndTokenType(nonExistentToken, TokenType.REFRESH))
                .thenReturn(Optional.empty());

        // When
        Optional<AuthToken> result = tokenService.findByTokenAndType(nonExistentToken, TokenType.REFRESH);

        // Then
        assertFalse(result.isPresent());
        verify(authTokenRepository).findByTokenAndTokenType(nonExistentToken, TokenType.REFRESH);
    }

    @Test
    void testVerifyToken_ValidToken() {
        // When
        AuthToken result = tokenService.verifyToken(validRefreshToken);

        // Then
        assertEquals(validRefreshToken, result);
        verify(authTokenRepository, never()).delete(any(AuthToken.class));
    }

    @Test
    void testVerifyToken_ExpiredToken() {
        // Given
        doNothing().when(authTokenRepository).delete(expiredRefreshToken);

        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            tokenService.verifyToken(expiredRefreshToken);
        });
        assertEquals("Token was expired. Please make a new signin request", exception.getMessage());
        verify(authTokenRepository).delete(expiredRefreshToken);
    }

    @Test
    void testVerifyToken_RevokedToken() {
        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            tokenService.verifyToken(revokedRefreshToken);
        });
        assertEquals("Token was revoked. Please sign in again", exception.getMessage());
        verify(authTokenRepository, never()).delete(any(AuthToken.class));
    }

    @Test
    void testRevokeToken_TokenExists() {
        // Given
        when(authTokenRepository.findByToken(refreshTokenString)).thenReturn(Optional.of(validRefreshToken));

        // When
        tokenService.revokeToken(refreshTokenString);

        // Then
        assertTrue(validRefreshToken.isRevoked());
        verify(authTokenRepository).findByToken(refreshTokenString);
        verify(authTokenRepository).save(validRefreshToken);
    }

    @Test
    void testRevokeToken_TokenDoesNotExist() {
        // Given
        String nonExistentToken = "nonExistentToken";
        when(authTokenRepository.findByToken(nonExistentToken)).thenReturn(Optional.empty());

        // When
        tokenService.revokeToken(nonExistentToken);

        // Then
        verify(authTokenRepository).findByToken(nonExistentToken);
        verify(authTokenRepository, never()).save(any(AuthToken.class));
    }

    @Test
    void testRevokeTokenWithType_TokenExists() {
        // Given
        when(authTokenRepository.findByTokenAndTokenType(refreshTokenString, TokenType.REFRESH))
                .thenReturn(Optional.of(validRefreshToken));

        // When
        tokenService.revokeToken(refreshTokenString, TokenType.REFRESH);

        // Then
        assertTrue(validRefreshToken.isRevoked());
        verify(authTokenRepository).findByTokenAndTokenType(refreshTokenString, TokenType.REFRESH);
        verify(authTokenRepository).save(validRefreshToken);
    }

    @Test
    void testRevokeTokenWithType_TokenDoesNotExist() {
        // Given
        String nonExistentToken = "nonExistentToken";
        when(authTokenRepository.findByTokenAndTokenType(nonExistentToken, TokenType.REFRESH))
                .thenReturn(Optional.empty());

        // When
        tokenService.revokeToken(nonExistentToken, TokenType.REFRESH);

        // Then
        verify(authTokenRepository).findByTokenAndTokenType(nonExistentToken, TokenType.REFRESH);
        verify(authTokenRepository, never()).save(any(AuthToken.class));
    }

    @Test
    void testRevokeAllUserTokens() {
        // When
        tokenService.revokeAllUserTokens(testUser);

        // Then
        verify(authTokenRepository).revokeAllUserTokens(testUser);
    }

    @Test
    void testRevokeAllUserTokensByType() {
        // When
        tokenService.revokeAllUserTokensByType(testUser, TokenType.REFRESH);

        // Then
        verify(authTokenRepository).revokeAllUserTokensByType(testUser, TokenType.REFRESH);
    }

    @Test
    void testFindActiveTokensByUser() {
        // Given
        List<AuthToken> expectedTokens = Arrays.asList(validRefreshToken, validAccessToken);
        when(authTokenRepository.findActiveTokensByUser(testUser)).thenReturn(expectedTokens);

        // When
        List<AuthToken> result = tokenService.findActiveTokensByUser(testUser);

        // Then
        assertEquals(expectedTokens, result);
        verify(authTokenRepository).findActiveTokensByUser(testUser);
    }

    @Test
    void testFindActiveTokensByUserAndType() {
        // Given
        List<AuthToken> expectedTokens = Arrays.asList(validRefreshToken);
        when(authTokenRepository.findActiveTokensByUserAndType(testUser, TokenType.REFRESH))
                .thenReturn(expectedTokens);

        // When
        List<AuthToken> result = tokenService.findActiveTokensByUserAndType(testUser, TokenType.REFRESH);

        // Then
        assertEquals(expectedTokens, result);
        verify(authTokenRepository).findActiveTokensByUserAndType(testUser, TokenType.REFRESH);
    }

    @Test
    void testRemoveExpiredTokens() {
        // When
        tokenService.removeExpiredTokens();

        // Then
        verify(authTokenRepository).deleteAllExpiredTokens(any(Instant.class));
    }

    @Test
    void testRemoveExpiredTokens_NoExpiredTokens() {
        // When
        tokenService.removeExpiredTokens();

        // Then
        verify(authTokenRepository).deleteAllExpiredTokens(any(Instant.class));
    }
}