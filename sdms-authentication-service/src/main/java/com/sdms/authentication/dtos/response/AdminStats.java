package com.sdms.authentication.dtos.response;

import com.sdms.authentication.entities.interfaces.LoginAttemptStats;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AdminStats {
    private long totalUsers;
    private long activeUsers;
    private long lockedAccounts;
    private long expiredAccounts;
    private Map<String, Long> usersByRole;
    private Map<String, Long> usersByStatus;
    private List<LoginAttemptStats> recentLoginAttempts;
}