package com.example.demo2.security.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Verify2FARequest {
    String username;
    String code;
}
