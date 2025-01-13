package com.example.demo2.dtos.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogEntry {
    private Long id;
    private String username;
    private String action;
    private String details;
    private String ipAddress;
    private LocalDateTime timestamp;
}