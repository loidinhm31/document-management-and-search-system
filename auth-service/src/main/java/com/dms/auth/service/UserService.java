package com.dms.auth.service;

import com.dms.auth.dto.UserDto;
import com.dms.auth.dto.UserSearchResponse;
import com.dms.auth.dto.request.*;
import com.dms.auth.exception.InvalidRequestException;
import com.dms.auth.security.request.Verify2FARequest;
import com.dms.auth.security.response.TokenResponse;
import com.dms.auth.security.response.UserInfoResponse;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

public interface UserService {
    // Authentication operations
    TokenResponse authenticateUser(LoginRequest loginRequest, HttpServletRequest request);
    TokenResponse refreshToken(String refreshToken);
    void logout(String refreshToken);
    void registerUser(SignupRequest signupRequest);
    UserInfoResponse getUserInfo(UserDetails userDetails);

    // Password operations
    void generatePasswordResetToken(String email);
    void resetPassword(String token, String newPassword);
    void updatePassword(UUID userId, UpdatePasswordRequest request, UserDetails currentUser) throws InvalidRequestException;

    // User management operations
    UserDto getUserById(UUID id);
    void updateUserRole(UUID userId, String roleName);
    UserDto getUserByUsername(String username);

    // 2FA operations
    GoogleAuthenticatorKey generate2FASecret(UUID userId);
    void verify2FACode(UUID userId, Verify2FARequest verify2FARequest);
    boolean validate2FACode(Verify2FARequest verify2FARequest);
    void enable2FA(String username);
    void disable2FA(UUID userId);
    boolean get2FAStatus(String username);
    String getQrCodeUrl(GoogleAuthenticatorKey key, String username);

    void updateProfile(UUID userId, UpdateProfileRequest request);
    void updateStatus(UUID userId, UpdateStatusRequest request, boolean isAdmin);

    List<UserSearchResponse> searchUsers(String query);
}