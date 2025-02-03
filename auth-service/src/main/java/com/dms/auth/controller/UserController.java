package com.dms.auth.controller;

import com.dms.auth.dto.UserDto;
import com.dms.auth.dto.UserSearchResponse;
import com.dms.auth.dto.request.UpdatePasswordRequest;
import com.dms.auth.dto.request.UpdateProfileRequest;
import com.dms.auth.dto.request.UpdateStatusRequest;
import com.dms.auth.dto.request.UpdateUserRequest;
import com.dms.auth.exception.InvalidRequestException;
import com.dms.auth.security.request.Verify2FARequest;
import com.dms.auth.security.response.UserInfoResponse;
import com.dms.auth.service.UserService;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserDto> getUserByUsername(@RequestParam String username) {
        return ResponseEntity.ok(userService.getUserByUsername(username));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#id)")
    public ResponseEntity<UserDto> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#id)")
    public ResponseEntity<Void> updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(id, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        userService.updateUserRole(id, request.getRoleName());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        userService.updateStatus(id, request, isAdmin);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<Void> updatePassword(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) throws InvalidRequestException {
        userService.updatePassword(id, request, userDetails);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserInfoResponse userInfo = userService.getUserInfo(userDetails);
        return ResponseEntity.ok(userInfo);
    }

    @PostMapping("/{id}/2fa/enable")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<String> enable2FA(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        GoogleAuthenticatorKey key = userService.generate2FASecret(id);
        String qrCodeUrl = userService.getQrCodeUrl(key, userDetails.getUsername());
        return ResponseEntity.ok(qrCodeUrl);
    }

    @PostMapping("/{id}/2fa/verify")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<Void> verify2FA(
            @PathVariable UUID id,
            @RequestBody Verify2FARequest verify2FARequest) {
        userService.verify2FACode(id, verify2FARequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/2fa/disable")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<Void> disable2FA(
            @PathVariable UUID id) {
        userService.disable2FA(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/2fa/status")
    @PreAuthorize("@userSecurity.isCurrentUser(#id)")
    public ResponseEntity<Boolean> get2FAStatus(@AuthenticationPrincipal UserDetails userDetails, @PathVariable UUID id) {
        boolean status = userService.get2FAStatus(userDetails.getUsername());
        return ResponseEntity.ok(status);
    }

    @GetMapping("/search")
//    @PreAuthorize("hasAnyRole('USER', 'MENTOR')")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(
            @RequestParam String query) {

        List<UserSearchResponse> results = userService.searchUsers(query);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/details")
    //    @PreAuthorize("hasAnyRole('USER', 'MENTOR')")
    public ResponseEntity<List<UserSearchResponse>> getUsersByIds(@RequestBody List<UUID> userIds) {
        return ResponseEntity.ok(userService.getUsersByIds(userIds));
    }
}