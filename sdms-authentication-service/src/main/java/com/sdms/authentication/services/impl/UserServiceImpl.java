package com.sdms.authentication.services.impl;

import com.sdms.authentication.constants.AuditActions;
import com.sdms.authentication.constants.AuditStatus;
import com.sdms.authentication.dtos.UserDto;
import com.sdms.authentication.dtos.request.*;
import com.sdms.authentication.entities.RefreshToken;
import com.sdms.authentication.enums.AppRole;
import com.sdms.authentication.exceptions.InvalidRequestException;
import com.sdms.authentication.exceptions.ResourceNotFoundException;
import com.sdms.authentication.mappers.UserMapper;
import com.sdms.authentication.entities.PasswordResetToken;
import com.sdms.authentication.entities.Role;
import com.sdms.authentication.entities.User;
import com.sdms.authentication.repositories.PasswordResetTokenRepository;
import com.sdms.authentication.repositories.RoleRepository;
import com.sdms.authentication.repositories.UserRepository;
import com.sdms.authentication.security.jwt.JwtUtils;
import com.sdms.authentication.security.request.Verify2FARequest;
import com.sdms.authentication.security.response.TokenResponse;
import com.sdms.authentication.security.response.UserInfoResponse;
import com.sdms.authentication.security.services.CustomUserDetails;
import com.sdms.authentication.services.AdminService;
import com.sdms.authentication.services.TotpService;
import com.sdms.authentication.services.UserService;
import com.sdms.authentication.utils.SecurityUtils;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {
    @Value("${frontend.url}")
    private String frontendUrl;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;
    private final TotpService totpService;
    private final AdminService adminService;
    private final UserMapper userMapper;

    @Override
    public TokenResponse authenticateUser(LoginRequest loginRequest, HttpServletRequest request) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // Generate JWT token
        String jwt = jwtUtils.generateTokenFromUsername(userDetails);

        // Get user from database
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", loginRequest.getUsername()));

        // Create refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, request);

        // Create audit log for successful login
        adminService.createAuditLog(
                user.getUsername(),
                AuditActions.LOGIN,
                "User logged in successfully",
                request.getRemoteAddr(),
                AuditStatus.SUCCESS
        );

        // Get user roles
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Return token response
        return new TokenResponse(
                jwt,
                refreshToken.getToken(),
                "Bearer",
                userDetails.getUsername(),
                roles
        );
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        return refreshTokenService.findByToken(refreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    CustomUserDetails userDetails = CustomUserDetails.build(user);
                    String jwt = jwtUtils.generateTokenFromUsername(userDetails);

                    List<String> roles = userDetails.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList());

                    return new TokenResponse(
                            jwt,
                            refreshToken,
                            "Bearer",
                            userDetails.getUsername(),
                            roles
                    );
                })
                .orElseThrow(() -> new RuntimeException("Refresh token not found in database"));
    }

    @Override
    public void logout(String refreshToken) {
        // Find and validate refresh token
        RefreshToken token = refreshTokenService.findByToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        // Get user before revoking token
        User user = token.getUser();

        // Revoke refresh token
        refreshTokenService.revokeToken(refreshToken);

        // Create audit log for logout
        adminService.createAuditLog(
                user.getUsername(),
                AuditActions.LOGOUT,
                "User logged out successfully",
                null,
                AuditStatus.SUCCESS
        );

        // Clear security context
        SecurityContextHolder.clearContext();
    }

    @Override
    public void registerUser(SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        Role role = roleRepository.findByRoleName(AppRole.ROLE_USER)
                .orElseThrow(() -> new ResourceNotFoundException("Default role not found"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setAccountNonLocked(true);
        user.setAccountNonExpired(true);
        user.setCredentialsNonExpired(true);
        user.setEnabled(true);
        user.setCredentialsExpiryDate(LocalDate.now().plusYears(1));
        user.setAccountExpiryDate(LocalDate.now().plusYears(1));
        user.setTwoFactorEnabled(false);
        user.setSignUpMethod("email");
        user.setCreatedBy(request.getUsername());
        user.setUpdatedBy(request.getUsername());

        userRepository.save(user);
    }

    @Override
    public UserInfoResponse getUserInfo(UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return new UserInfoResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.isAccountNonLocked(),
                user.isAccountNonExpired(),
                user.isCredentialsNonExpired(),
                user.isEnabled(),
                user.getCredentialsExpiryDate(),
                user.getAccountExpiryDate(),
                user.isTwoFactorEnabled(),
                roles
        );
    }

    @Override
    public void generatePasswordResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        String token = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plus(24, ChronoUnit.HOURS);

        PasswordResetToken resetToken = new PasswordResetToken(token, expiryDate, user);
        resetToken.setUpdatedBy(SecurityUtils.getUserIdentifier());
        passwordResetTokenRepository.save(resetToken);

        String resetUrl = String.format("%s/reset-password?token=%s", frontendUrl, token);
        emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token"));

        if (resetToken.isUsed() || resetToken.getExpiryDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Password reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        resetToken.setUsed(true);

        userRepository.save(user);
        passwordResetTokenRepository.save(resetToken);
    }

    @Override
    public UserDto getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return userMapper.convertToDto(user);
    }

    @Override
    public void updateUserRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        AppRole appRole = AppRole.valueOf(roleName);
        Role role = roleRepository.findByRoleName(appRole)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));

        user.setRole(role);
        user.setUpdatedBy(SecurityUtils.getUserIdentifier());
        userRepository.save(user);
    }

    @Override
    public void updatePassword(UUID userId, UpdatePasswordRequest request, UserDetails currentUser) throws InvalidRequestException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidRequestException("INCORRECT_PASSWORD");
        }

        // Check if new password is same as old password
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new InvalidRequestException("DIFFERENT_PASSWORD");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedBy(SecurityUtils.getUserIdentifier());
        user.setCredentialsExpiryDate(LocalDate.now().plusMonths(6)); // Reset credentials expiry

        // Optionally invalidate any existing password reset tokens
        passwordResetTokenRepository.findAll().stream()
                .filter(token -> token.getUser().equals(user) && !token.isUsed())
                .forEach(token -> {
                    token.setUsed(true);
                    passwordResetTokenRepository.save(token);
                });

        userRepository.save(user);

        // Create audit log
        adminService.createAuditLog(user.getUsername(), AuditActions.PASSWORD_CHANGE, "Password changed successfully", null, AuditStatus.SUCCESS);
    }

    // 2FA operations
    @Override
    public GoogleAuthenticatorKey generate2FASecret(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        GoogleAuthenticatorKey key = totpService.generateSecret();
        user.setTwoFactorSecret(key.getKey());
        user.setUpdatedBy(SecurityUtils.getUserIdentifier());
        userRepository.save(user);
        return key;
    }

    @Override
    public void verify2FACode(UUID userId, Verify2FARequest verify2FARequest) {
        if (Objects.isNull(verify2FARequest.getCode())) {
            throw new IllegalArgumentException("Invalid code");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        boolean isValid = totpService.verifyCode(user.getTwoFactorSecret(), Integer.parseInt(verify2FARequest.getCode()));
        if (!isValid) {
            throw new IllegalArgumentException("Invalid 2FA code");
        }
        user.setTwoFactorEnabled(true);
        user.setUpdatedBy(SecurityUtils.getUserIdentifier());
        userRepository.save(user);
    }

    @Override
    public boolean validate2FACode(Verify2FARequest verify2FARequest) {
        if (Objects.isNull(verify2FARequest.getCode())) {
            throw new IllegalArgumentException("Invalid code");
        }

        if (Objects.isNull(verify2FARequest.getUsername())) {
            throw new IllegalArgumentException("Invalid username");
        }

        User user = userRepository.findByUsername(verify2FARequest.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", verify2FARequest.getUsername()));
        return totpService.verifyCode(user.getTwoFactorSecret(), Integer.parseInt(verify2FARequest.getCode()));
    }

    @Override
    public void enable2FA(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "name", username));
        user.setTwoFactorEnabled(true);
        user.setUpdatedBy(username);
        userRepository.save(user);
    }

    @Override
    public void disable2FA(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setUpdatedBy(SecurityUtils.getUserIdentifier());
        userRepository.save(user);
    }

    @Override
    public boolean get2FAStatus(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        return user.isTwoFactorEnabled();
    }

    @Override
    public String getQrCodeUrl(GoogleAuthenticatorKey key, String username) {
        return totpService.getQrCodeUrl(key, username);
    }

    @Override
    public void updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        ;

        // Validate username uniqueness if changed
        if (!user.getUsername().equals(request.getUsername()) &&
                userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        // Validate email uniqueness if changed
        if (!user.getEmail().equals(request.getEmail()) &&
                userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setUpdatedBy(SecurityUtils.getUserIdentifier());
        userRepository.save(user);
    }

    @Override
    public void updateStatus(UUID userId, UpdateStatusRequest request, boolean isAdmin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        ;

        if (!isAdmin) {
            // Regular users can only update specific fields
            if (request.getAccountLocked() != null ||
                    request.getAccountExpired() != null ||
                    request.getCredentialsExpired() != null ||
                    request.getEnabled() != null) {
                throw new AccessDeniedException("Operation not allowed for regular users");
            }
        }

        // Update allowed fields based on role
        if (isAdmin) {
            if (request.getAccountLocked() != null) {
                user.setAccountNonLocked(!request.getAccountLocked());
            }
            if (request.getAccountExpired() != null) {
                user.setAccountNonExpired(!request.getAccountExpired());
            }
            if (request.getCredentialsExpired() != null) {
                user.setCredentialsNonExpired(!request.getCredentialsExpired());
            }
            if (request.getEnabled() != null) {
                user.setEnabled(request.getEnabled());
            }
            if (request.getCredentialsExpiryDate() != null) {
                user.setCredentialsExpiryDate(request.getCredentialsExpiryDate());
            }
            if (request.getAccountExpiryDate() != null) {
                user.setAccountExpiryDate(request.getAccountExpiryDate());
            }
        }

        user.setUpdatedBy(SecurityUtils.getUserIdentifier());

        userRepository.save(user);
    }
}