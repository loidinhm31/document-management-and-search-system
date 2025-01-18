
package com.dms.auth.dtos.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateStatusRequest {
    private Boolean accountLocked;
    private Boolean accountExpired;
    private Boolean credentialsExpired;
    private Boolean enabled;
    private LocalDate credentialsExpiryDate;
    private LocalDate accountExpiryDate;
}