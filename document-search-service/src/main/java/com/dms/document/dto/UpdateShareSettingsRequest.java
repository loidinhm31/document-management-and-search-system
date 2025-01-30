package com.dms.document.dto;

import java.util.Set;

public record UpdateShareSettingsRequest(
        boolean isPublic,
        Set<String> sharedWith
) {}