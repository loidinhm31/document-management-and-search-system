package com.sdms.authentication.entities.interfaces;

public interface LoginAttemptStats {
    String getDate();
    Long getSuccessCount();
    Long getFailureCount();
}
