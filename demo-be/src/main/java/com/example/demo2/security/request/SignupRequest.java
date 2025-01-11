package com.example.demo2.security.request;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Data
public class SignupRequest {
    private String username;

    private String email;

    @Setter
    @Getter
    private Set<String> role;

    private String password;
}