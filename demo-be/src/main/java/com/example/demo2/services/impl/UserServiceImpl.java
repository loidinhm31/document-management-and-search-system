package com.example.demo2.services.impl;

import com.example.demo2.constants.AuditActions;
import com.example.demo2.constants.AuditStatus;
import com.example.demo2.dtos.UserDto;
import com.example.demo2.dtos.request.*;
import com.example.demo2.enums.AppRole;
import com.example.demo2.exceptions.InvalidRequestException;
import com.example.demo2.exceptions.ResourceNotFoundException;
import com.example.demo2.mappers.UserMapper;
import com.example.demo2.entities.PasswordResetToken;
import com.example.demo2.entities.Role;
import com.example.demo2.entities.User;
import com.example.demo2.repositories.PasswordResetTokenRepository;
import com.example.demo2.repositories.RoleRepository;
import com.example.demo2.repositories.UserRepository;
import com.example.demo2.security.jwt.JwtUtils;
import com.example.demo2.security.response.LoginResponse;
import com.example.demo2.security.response.UserInfoResponse;
import com.example.demo2.security.services.CustomUserDetails;
import com.example.demo2.services.AdminService;
import com.example.demo2.services.TotpService;
import com.example.demo2.services.UserService;
import com.example.demo2.utils.SecurityUtils;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;
    private final TotpService totpService;
    private final AdminService adminService;
    private final UserMapper userMapper;

    @Override
    public LoginResponse authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String jwtToken = jwtUtils.generateTokenFromUsername(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return new LoginResponse(userDetails.getUsername(), roles, jwtToken);
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

    // User management operations
    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public UserDto getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return userMapper.convertToDto(user);
    }

    @Override
    public UserDto getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
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
    public void verify2FACode(UUID userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        boolean isValid = totpService.verifyCode(user.getTwoFactorSecret(), Integer.parseInt(code));
        if (!isValid) {
            throw new IllegalArgumentException("Invalid 2FA code");
        }
        user.setTwoFactorEnabled(true);
        user.setUpdatedBy(SecurityUtils.getUserIdentifier());
        userRepository.save(user);
    }

    @Override
    public boolean validate2FACode(UUID userId, int code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return totpService.verifyCode(user.getTwoFactorSecret(), code);
    }

    @Override
    public void enable2FA(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        user.setTwoFactorEnabled(true);
        user.setUpdatedBy(SecurityUtils.getUserIdentifier());
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
    public void updateSecurity(UUID userId, UpdateSecurityRequest request, UserDetails currentUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        ;
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // If not admin, verify current password
        if (!isAdmin && request.getCurrentPassword() != null) {
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new BadCredentialsException("Current password is incorrect");
            }
        }

        if (request.getNewPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

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