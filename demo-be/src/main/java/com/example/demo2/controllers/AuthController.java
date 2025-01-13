package com.example.demo2.controllers;

import com.example.demo2.dtos.request.LoginRequest;
import com.example.demo2.dtos.request.PasswordResetRequest;
import com.example.demo2.dtos.request.SignupRequest;
import com.example.demo2.dtos.response.ApiResponse;
import com.example.demo2.security.response.LoginResponse;
import com.example.demo2.services.UserService;
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
}