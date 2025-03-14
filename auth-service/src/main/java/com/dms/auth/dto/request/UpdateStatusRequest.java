
package com.dms.auth.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateStatusRequest {
    private Boolean accountLocked;
    private Boolean tokenExpired;
}