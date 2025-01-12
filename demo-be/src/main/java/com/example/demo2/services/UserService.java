package com.example.demo2.services;

import com.example.demo2.dtos.UserDTO;
import com.example.demo2.dtos.request.LoginRequest;
import com.example.demo2.dtos.request.SignupRequest;
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
    User getUserByUsername(String username);
    void updateUserRole(Long userId, String roleName);
    void updateAccountLockStatus(Long userId, boolean locked);
    void updateAccountExpiryStatus(Long userId, boolean expired);
    void updateAccountEnabledStatus(Long userId, boolean enabled);
    void updateCredentialsExpiryStatus(Long userId, boolean expired);

    // 2FA operations
    GoogleAuthenticatorKey generate2FASecret(String username);
    void verify2FACode(String username, String code);
    boolean validate2FACode(Long userId, int code);
    void enable2FA(Long userId);
    void disable2FA(String username);
    boolean get2FAStatus(String username);
    String getQrCodeUrl(GoogleAuthenticatorKey key, String username);

    // Role operations
    List<Role> getAllRoles();
}