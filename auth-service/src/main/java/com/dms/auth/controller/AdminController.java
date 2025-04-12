package com.dms.auth.controller;

import com.dms.auth.dto.UserDto;
import com.dms.auth.dto.request.UserSearchRequest;
import com.dms.auth.entity.Role;
import com.dms.auth.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @PostMapping("/users")
    public ResponseEntity<Page<UserDto>> getAllUsers(
            @RequestBody UserSearchRequest userSearchRequest) {
        return ResponseEntity.ok(
                adminService.getAllUsers(userSearchRequest)
        );
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getAllRoles() {
        return ResponseEntity.ok(
                adminService.getAllRoles()
        );
    }
}

