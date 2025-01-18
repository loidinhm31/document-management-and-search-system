package com.dms.auth.services;

import com.dms.auth.dtos.UserDto;
import com.dms.auth.dtos.response.AdminStats;
import com.dms.auth.dtos.response.AuditLogEntry;
import com.dms.auth.entities.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AdminService {
    Page<UserDto> getAllUsers(String search, Boolean enabled, String role, Pageable pageable);

    List<Role> getAllRoles();

    AdminStats getSystemStats();

    Page<AuditLogEntry> getAuditLogs(String username, String action, String fromDate,
                                     String toDate, Pageable pageable);

    void createAuditLog(String username, String action, String details, String ipAddress, String status);
}