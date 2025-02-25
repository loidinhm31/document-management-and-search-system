package com.dms.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class OtpVerificationResponse {
    private String status;
    private int count;
    private boolean locked;
    private boolean verified;
}