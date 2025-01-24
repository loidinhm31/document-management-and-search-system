package com.dms.auth.service.impl;

import com.dms.auth.dto.UserDto;
import com.dms.auth.dto.response.AdminStats;
import com.dms.auth.dto.response.AuditLogEntry;
import com.dms.auth.entity.AuditLog;
import com.dms.auth.entity.Role;
import com.dms.auth.entity.User;
import com.dms.auth.entity.interfaces.LoginAttemptStats;
import com.dms.auth.enums.AppRole;
import com.dms.auth.mapper.AuditLogMapper;
import com.dms.auth.mapper.UserMapper;
import com.dms.auth.repository.AuditLogRepository;
import com.dms.auth.repository.RoleRepository;
import com.dms.auth.repository.UserRepository;
import com.dms.auth.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;

    @Override
    public Page<UserDto> getAllUsers(String search, Boolean enabled, String role, Pageable pageable) {
        Specification<User> spec = Specification.where(null);

        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("username")), "%" + search.toLowerCase() + "%"),
                            cb.like(cb.lower(root.get("email")), "%" + search.toLowerCase() + "%")
                    )
            );
        }

        if (enabled != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("enabled"), enabled));
        }

        if (role != null && !role.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.join("role").get("roleName"), AppRole.valueOf(role))
            );
        }

        // Add default sort by username if no sort is specified
        if (pageable.getSort().isEmpty()) {
            pageable = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by(Sort.Direction.ASC, "username")
            );
        }

        return userRepository.findAll(spec, pageable).map(userMapper::convertToDto);
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Override
    public AdminStats getSystemStats() {
        AdminStats stats = new AdminStats();
        stats.setTotalUsers(userRepository.count());
        stats.setActiveUsers(userRepository.countByEnabledTrue());
        stats.setLockedAccounts(userRepository.countByAccountNonLockedFalse());
        stats.setExpiredAccounts(userRepository.countByAccountNonExpiredFalse());

        // Get users by role
        Map<String, Long> usersByRole = roleRepository.findAll().stream()
                .collect(Collectors.toMap(
                        role -> role.getRoleName().name(),
                        userRepository::countByRole
                ));
        stats.setUsersByRole(usersByRole);

        // Get recent login attempts
        stats.setRecentLoginAttempts(getRecentLoginAttempts());

        return stats;
    }

    @Override
    public Page<AuditLogEntry> getAuditLogs(String username, String action,
                                            String fromDate, String toDate, Pageable pageable) {
        Specification<AuditLog> spec = Specification.where(null);

        if (username != null && !username.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("username"), username));
        }

        if (action != null && !action.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("action"), action));
        }

        if (fromDate != null && !fromDate.trim().isEmpty()) {
            LocalDateTime from = LocalDateTime.parse(fromDate);
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("timestamp"), from));
        }

        if (toDate != null && !toDate.trim().isEmpty()) {
            LocalDateTime to = LocalDateTime.parse(toDate);
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("timestamp"), to));
        }

        return auditLogRepository.findAll(spec, pageable).map(auditLogMapper::convertToAuditLogEntry);
    }

    @Override
    public void createAuditLog(String username, String action, String details, String ipAddress, String status) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUsername(username);
        auditLog.setAction(action);
        auditLog.setDetails(details);
        auditLog.setIpAddress(ipAddress);
        auditLog.setStatus(status);

        // Get user if available
        userRepository.findByUsername(username).ifPresent(auditLog::setUser);

        auditLogRepository.save(auditLog);
    }


    private List<LoginAttemptStats> getRecentLoginAttempts() {
        // Get login attempts for the last 30 days
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        return auditLogRepository.getLoginAttemptStats(startDate);
    }
}