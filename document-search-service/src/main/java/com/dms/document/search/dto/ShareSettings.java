package com.dms.document.search.dto;

import java.util.Set;

public record ShareSettings(
        boolean isPublic,
        Set<String> sharedWith
) {}