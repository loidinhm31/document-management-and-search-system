package com.example.demo2.controllers;

import com.example.demo2.dtos.request.*;
import com.example.demo2.dtos.response.ApiResponse;
import com.example.demo2.security.response.LoginResponse;
import com.example.demo2.security.response.UserInfoResponse;
import com.example.demo2.services.UserService;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse response = userService.authenticateUser(loginRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody SignupRequest signupRequest) {
        userService.registerUser(signupRequest);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(null));
    }

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserInfoResponse userInfo = userService.getUserInfo(userDetails);
        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestParam @Email String email) {
        userService.generatePasswordResetToken(email);
        return ResponseEntity.ok(
                ApiResponse.success(null)
        );
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<ApiResponse<String>> enable2FA(@AuthenticationPrincipal UserDetails userDetails) {
        GoogleAuthenticatorKey key = userService.generate2FASecret(userDetails.getUsername());
        String qrCodeUrl = userService.getQrCodeUrl(key, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(qrCodeUrl));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<ApiResponse<Void>> verify2FA(
            @Valid @RequestBody TwoFactorVerificationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        userService.verify2FACode(userDetails.getUsername(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<ApiResponse<Void>> disable2FA(@AuthenticationPrincipal UserDetails userDetails) {
        userService.disable2FA(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/2fa/status")
    public ResponseEntity<ApiResponse<Boolean>> get2FAStatus(@AuthenticationPrincipal UserDetails userDetails) {
        boolean status = userService.get2FAStatus(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}