package com.example.demo2.controllers;

import com.example.demo2.dtos.request.LoginRequest;
import com.example.demo2.dtos.request.PasswordResetRequest;
import com.example.demo2.dtos.request.SignupRequest;
import com.example.demo2.dtos.response.ApiResponse;
import com.example.demo2.security.request.Verify2FARequest;
import com.example.demo2.security.response.LoginResponse;
import com.example.demo2.services.UserService;
import com.example.demo2.utils.SecurityUtils;
import com.example.demo2.utils.UserSecurity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;
    private final UserSecurity userSecurity;

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

    @PostMapping("/2fa/verify")
    public ResponseEntity<ApiResponse<Void>> verify2FA(
            @RequestBody Verify2FARequest verify2FARequest) {
        boolean isValid = userService.validate2FACode(verify2FARequest);
        if (isValid) {
            userService.enable2FA(verify2FARequest.getUsername());
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}