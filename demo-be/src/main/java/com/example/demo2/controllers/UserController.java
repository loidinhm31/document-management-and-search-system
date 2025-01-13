package com.example.demo2.controllers;

import com.example.demo2.dtos.UserDTO;
import com.example.demo2.dtos.request.UpdateProfileRequest;
import com.example.demo2.dtos.request.UpdateStatusRequest;
import com.example.demo2.dtos.request.UpdateUserRequest;
import com.example.demo2.dtos.response.ApiResponse;
import com.example.demo2.security.response.UserInfoResponse;
import com.example.demo2.services.UserService;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<UserDTO>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(id, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateUserRole(
            @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        userService.updateUserRole(id, request.getRoleName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        userService.updateStatus(id, request, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserInfoResponse userInfo = userService.getUserInfo(userDetails);
        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    @PostMapping("/{id}/2fa/enable")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<String>> enable2FA(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        GoogleAuthenticatorKey key = userService.generate2FASecret(id);
        String qrCodeUrl = userService.getQrCodeUrl(key, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(qrCodeUrl));
    }

    @PostMapping("/{id}/2fa/verify")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<Void>> verify2FA(
            @PathVariable Long id,
            @RequestParam String code) {
        userService.verify2FACode(id, code);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/2fa/disable")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<Void>> disable2FA(
            @PathVariable Long id) {
        userService.disable2FA(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/2fa/status")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<Boolean>> get2FAStatus(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        boolean status = userService.get2FAStatus(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}