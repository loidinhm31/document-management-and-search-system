package com.sdms.authentication.controllers;

import com.sdms.authentication.dtos.UserDto;
import com.sdms.authentication.dtos.request.UpdatePasswordRequest;
import com.sdms.authentication.dtos.request.UpdateProfileRequest;
import com.sdms.authentication.dtos.request.UpdateStatusRequest;
import com.sdms.authentication.dtos.request.UpdateUserRequest;
import com.sdms.authentication.dtos.response.ApiResponse;
import com.sdms.authentication.exceptions.InvalidRequestException;
import com.sdms.authentication.security.request.Verify2FARequest;
import com.sdms.authentication.security.response.UserInfoResponse;
import com.sdms.authentication.services.UserService;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(id, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateUserRole(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        userService.updateUserRole(id, request.getRoleName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        userService.updateStatus(id, request, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) throws InvalidRequestException {
        userService.updatePassword(id, request, userDetails);
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
            @PathVariable UUID id) {
        GoogleAuthenticatorKey key = userService.generate2FASecret(id);
        String qrCodeUrl = userService.getQrCodeUrl(key, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(qrCodeUrl));
    }

    @PostMapping("/{id}/2fa/verify")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<Void>> verify2FA(
            @PathVariable UUID id,
            @RequestBody Verify2FARequest verify2FARequest) {
        userService.verify2FACode(id, verify2FARequest);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/2fa/disable")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<Void>> disable2FA(
            @PathVariable UUID id) {
        userService.disable2FA(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/2fa/status")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<Boolean>> get2FAStatus(@AuthenticationPrincipal UserDetails userDetails, @PathVariable UUID id) {
        boolean status = userService.get2FAStatus(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}