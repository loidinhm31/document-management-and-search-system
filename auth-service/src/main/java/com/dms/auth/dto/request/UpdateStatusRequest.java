
package com.dms.auth.dto.request;

import lombok.Data;

@Data
public class UpdateStatusRequest {
    private Boolean accountLocked;
    private Boolean credentialsExpired;
}