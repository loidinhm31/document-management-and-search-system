package com.dms.auth.controllers;

import com.dms.auth.dtos.request.LoginRequest;
import com.dms.auth.dtos.request.PasswordResetRequest;
import com.dms.auth.security.request.RefreshTokenRequest;
import com.dms.auth.dtos.request.SignupRequest;
import com.dms.auth.security.response.TokenResponse;
import com.dms.auth.security.request.Verify2FARequest;
import com.dms.auth.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        TokenResponse response = userService.authenticateUser(loginRequest, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<TokenResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = userService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        userService.logout(request.getRefreshToken());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody SignupRequest signupRequest) {
        userService.registerUser(signupRequest);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@RequestParam @Email String email) {
        userService.generatePasswordResetToken(email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<String> verify2FA(
            @RequestBody Verify2FARequest verify2FARequest) {
        boolean isValid = userService.validate2FACode(verify2FARequest);
        if (isValid) {
            userService.enable2FA(verify2FARequest.getUsername());
            return ResponseEntity.ok("VERIFIED");
        }
        return ResponseEntity.ok("UNVERIFIED");
    }


}