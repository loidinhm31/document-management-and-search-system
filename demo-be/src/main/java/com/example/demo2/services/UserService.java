package com.example.demo2.services;

import com.example.demo2.dtos.UserDto;
import com.example.demo2.dtos.request.*;
import com.example.demo2.exceptions.InvalidRequestException;
import com.example.demo2.security.request.Verify2FARequest;
import com.example.demo2.security.response.LoginResponse;
import com.example.demo2.security.response.UserInfoResponse;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

public interface UserService {
    // Authentication operations
    LoginResponse authenticateUser(LoginRequest loginRequest);
    void registerUser(SignupRequest signupRequest);
    UserInfoResponse getUserInfo(UserDetails userDetails);

    // Password operations
    void generatePasswordResetToken(String email);
    void resetPassword(String token, String newPassword);
    void updatePassword(UUID userId, UpdatePasswordRequest request, UserDetails currentUser) throws InvalidRequestException;

    // User management operations
    UserDto getUserById(UUID id);
    void updateUserRole(UUID userId, String roleName);

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

}