package com.example.demo2.mappers;

import com.example.demo2.dtos.response.AuditLogEntry;
import com.example.demo2.entities.AuditLog;
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
