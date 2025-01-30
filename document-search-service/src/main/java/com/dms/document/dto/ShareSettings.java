package com.dms.document.dto;

import java.util.Set;

public record ShareSettings(
        boolean isPublic,
        Set<String> sharedWith
) {}