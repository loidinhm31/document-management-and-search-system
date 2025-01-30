package com.dms.document.search.dto;

import java.util.Set;

public record UpdateShareSettingsRequest(
        boolean isPublic,
        Set<String> sharedWith
) {}