package com.sdms.authentication.utils;

import com.sdms.authentication.security.services.CustomUserDetails;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

public class SecurityUtils {
    public static String getUserIdentifier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userIdentifier = StringUtils.EMPTY;
        if (Objects.nonNull(authentication)) {
            if (Objects.nonNull(authentication.getPrincipal())) {
                CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
                userIdentifier = customUserDetails.getUsername();
            }
        }
        return userIdentifier;
    }
}
