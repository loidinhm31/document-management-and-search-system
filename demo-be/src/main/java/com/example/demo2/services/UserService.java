package com.example.demo2.services;

import com.example.demo2.dtos.UserDTO;
import com.example.demo2.dtos.request.*;
import com.example.demo2.models.Role;
import com.example.demo2.models.User;
import com.example.demo2.security.response.LoginResponse;
import com.example.demo2.security.response.UserInfoResponse;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public interface UserService {
    // Authentication operations
    LoginResponse authenticateUser(LoginRequest loginRequest);
    void registerUser(SignupRequest signupRequest);
    UserInfoResponse getUserInfo(UserDetails userDetails);

    // Password operations
    void generatePasswordResetToken(String email);
    void resetPassword(String token, String newPassword);
    void updatePassword(Long userId, String newPassword);

    // User management operations
    List<User> getAllUsers();
    UserDTO getUserById(Long id);
    UserDTO getUserByUsername(String username);
    void updateUserRole(Long userId, String roleName);

    // 2FA operations
    GoogleAuthenticatorKey generate2FASecret(Long userId);
    void verify2FACode(Long userId, String code);
    boolean validate2FACode(Long userId, int code);
    void enable2FA(Long userId);
    void disable2FA(Long userId);
    boolean get2FAStatus(String username);
    String getQrCodeUrl(GoogleAuthenticatorKey key, String username);

    void updateProfile(Long userId, UpdateProfileRequest request);
    void updateSecurity(Long userId, UpdateSecurityRequest request, UserDetails currentUser);
    void updateStatus(Long userId, UpdateStatusRequest request, boolean isAdmin);

}