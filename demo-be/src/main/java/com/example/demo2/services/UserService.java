package com.example.demo2.services;

import com.example.demo2.dtos.UserDTO;
import com.example.demo2.dtos.request.*;
import com.example.demo2.entities.User;
import com.example.demo2.security.response.LoginResponse;
import com.example.demo2.security.response.UserInfoResponse;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

public interface UserService {
    // Authentication operations
    LoginResponse authenticateUser(LoginRequest loginRequest);
    void registerUser(SignupRequest signupRequest);
    UserInfoResponse getUserInfo(UserDetails userDetails);

    // Password operations
    void generatePasswordResetToken(String email);
    void resetPassword(String token, String newPassword);
    void updatePassword(UUID userId, String newPassword);

    // User management operations
    List<User> getAllUsers();
    UserDTO getUserById(UUID id);
    UserDTO getUserByUsername(String username);
    void updateUserRole(UUID userId, String roleName);

    // 2FA operations
    GoogleAuthenticatorKey generate2FASecret(UUID userId);
    void verify2FACode(UUID userId, String code);
    boolean validate2FACode(UUID userId, int code);
    void enable2FA(UUID userId);
    void disable2FA(UUID userId);
    boolean get2FAStatus(String username);
    String getQrCodeUrl(GoogleAuthenticatorKey key, String username);

    void updateProfile(UUID userId, UpdateProfileRequest request);
    void updateSecurity(UUID userId, UpdateSecurityRequest request, UserDetails currentUser);
    void updateStatus(UUID userId, UpdateStatusRequest request, boolean isAdmin);

}