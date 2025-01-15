package com.sdms.auth.security.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Verify2FARequest {
    String username;
    String code;
}
