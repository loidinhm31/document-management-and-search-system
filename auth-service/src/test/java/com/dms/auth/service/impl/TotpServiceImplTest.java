package com.dms.auth.service.impl;

import com.dms.auth.service.TotpService;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TotpServiceImplTest {

    @Mock
    private GoogleAuthenticator mockGoogleAuthenticator;

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpServiceImpl(mockGoogleAuthenticator);
    }

    @Test
    void testGenerateSecret() {
        // Arrange
        GoogleAuthenticatorKey expectedKey = new GoogleAuthenticatorKey.Builder("TESTKEY123456")
                .setVerificationCode(123456)
                .setScratchCodes(java.util.Arrays.asList(12345, 67890))
                .build();
        when(mockGoogleAuthenticator.createCredentials()).thenReturn(expectedKey);

        // Act
        GoogleAuthenticatorKey result = totpService.generateSecret();

        // Assert
        assertNotNull(result);
        assertEquals(expectedKey, result);
        verify(mockGoogleAuthenticator).createCredentials();
    }

    @Test
    void testGetQrCodeUrl() {
        // Arrange
        String username = "testuser";
        GoogleAuthenticatorKey key = new GoogleAuthenticatorKey.Builder("TESTKEY123456")
                .setVerificationCode(123456)
                .setScratchCodes(java.util.Arrays.asList(12345, 67890))
                .build();
        String expectedQrCodeUrl = "otpauth://totp/Document%20Management%20and%20Search%20System:testuser?secret=TESTKEY123456&issuer=Document%20Management%20and%20Search%20System";

        try (MockedStatic<GoogleAuthenticatorQRGenerator> qrGenerator = Mockito.mockStatic(GoogleAuthenticatorQRGenerator.class)) {
            qrGenerator.when(() -> GoogleAuthenticatorQRGenerator.getOtpAuthURL(
                            "Document Management and Search System", username, key))
                    .thenReturn(expectedQrCodeUrl);

            // Act
            String result = totpService.getQrCodeUrl(key, username);

            // Assert
            assertNotNull(result);
            assertEquals(expectedQrCodeUrl, result);

            // Verify static method call
            qrGenerator.verify(() -> GoogleAuthenticatorQRGenerator.getOtpAuthURL(
                    "Document Management and Search System", username, key));
        }
    }

    @Test
    void testVerifyCode_WhenValid() {
        // Arrange
        String secret = "TESTKEY123456";
        int code = 123456;
        when(mockGoogleAuthenticator.authorize(secret, code)).thenReturn(true);

        // Act
        boolean result = totpService.verifyCode(secret, code);

        // Assert
        assertTrue(result);
        verify(mockGoogleAuthenticator).authorize(secret, code);
    }

    @Test
    void testVerifyCode_WhenInvalid() {
        // Arrange
        String secret = "TESTKEY123456";
        int code = 123456;
        when(mockGoogleAuthenticator.authorize(secret, code)).thenReturn(false);

        // Act
        boolean result = totpService.verifyCode(secret, code);

        // Assert
        assertFalse(result);
        verify(mockGoogleAuthenticator).authorize(secret, code);
    }

    @Test
    void testDefaultConstructor() {
        // Act - Create instance using default constructor
        TotpService service = new TotpServiceImpl();

        // Assert - Just verifying it works without exceptions
        assertNotNull(service);

        // Additional test - try generating a secret to verify the internal GoogleAuthenticator was created
        GoogleAuthenticatorKey key = service.generateSecret();
        assertNotNull(key);
        assertNotNull(key.getKey());
    }
}