package com.dms.document.search.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UserDto {
    private UUID userId;
    private String username;
    private String email;
}