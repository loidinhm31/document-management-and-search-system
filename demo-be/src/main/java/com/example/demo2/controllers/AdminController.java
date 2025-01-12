package com.example.demo2.controllers;

import com.example.demo2.dtos.UserDTO;
import com.example.demo2.dtos.request.PasswordResetRequest;
import com.example.demo2.dtos.request.UpdateUserRequest;
import com.example.demo2.dtos.response.ApiResponse;
import com.example.demo2.models.Role;
import com.example.demo2.models.User;
import com.example.demo2.services.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getAllUsers())
        );
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> getUser(@PathVariable @NotNull Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getUserById(id))
        );
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<Role>>> getAllRoles() {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getAllRoles())
        );
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<Void>> updateUserRole(
            @PathVariable Long id, @RequestBody UpdateUserRequest request) {
        userService.updateUserRole(id, request.getRoleName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/users/{id}/status/lock")
    public ResponseEntity<ApiResponse<Void>> updateAccountLockStatus(
            @PathVariable Long id,
            @RequestParam boolean locked) {
        userService.updateAccountLockStatus(id, locked);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/users/{id}/status/expiry")
    public ResponseEntity<ApiResponse<Void>> updateAccountExpiryStatus(
            @PathVariable Long id,
            @RequestParam boolean expired) {
        userService.updateAccountExpiryStatus(id, expired);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/users/{id}/status/enable")
    public ResponseEntity<ApiResponse<Void>> updateAccountEnabledStatus(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        userService.updateAccountEnabledStatus(id, enabled);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/users/{id}/credentials/expiry")
    public ResponseEntity<ApiResponse<Void>> updateCredentialsExpiryStatus(
            @PathVariable Long id,
            @RequestParam boolean expired) {
        userService.updateCredentialsExpiryStatus(id, expired);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @PathVariable Long id,
            @RequestBody @Valid PasswordResetRequest request) {
        userService.updatePassword(id, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}