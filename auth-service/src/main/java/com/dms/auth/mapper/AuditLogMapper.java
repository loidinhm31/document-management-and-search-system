package com.dms.auth.mapper;

import com.dms.auth.dto.response.AuditLogEntry;
import com.dms.auth.entity.AuditLog;
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
