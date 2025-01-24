package com.dms.auth.controller;

import com.dms.auth.dto.UserDto;
import com.dms.auth.dto.response.AdminStats;
import com.dms.auth.dto.response.AuditLogEntry;
import com.dms.auth.entity.Role;
import com.dms.auth.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<Page<UserDto>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String role,
            @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(
                adminService.getAllUsers(search, enabled, role, pageable)
        );
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getAllRoles() {
        return ResponseEntity.ok(
                adminService.getAllRoles()
        );
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStats> getSystemStats() {
        return ResponseEntity.ok(
                adminService.getSystemStats()
        );
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogEntry>> getAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                adminService.getAuditLogs(username, action, fromDate, toDate, pageable)
        );
    }
}

