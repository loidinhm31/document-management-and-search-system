package com.sdms.auth.mappers;

import com.sdms.auth.dtos.response.AuditLogEntry;
import com.sdms.auth.entities.AuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {
    public AuditLogEntry convertToAuditLogEntry(AuditLog auditLog) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setId(auditLog.getId());
        entry.setUsername(auditLog.getUsername());
        entry.setAction(auditLog.getAction());
        entry.setDetails(auditLog.getDetails());
        entry.setIpAddress(auditLog.getIpAddress());
        entry.setTimestamp(auditLog.getTimestamp());
        return entry;
    }
}
