package com.dms.auth.entity.interfaces;

public interface LoginAttemptStats {
    String getDate();
    Long getSuccessCount();
    Long getFailureCount();
}
