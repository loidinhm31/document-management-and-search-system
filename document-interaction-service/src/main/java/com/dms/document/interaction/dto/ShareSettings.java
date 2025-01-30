package com.dms.document.interaction.dto;

import java.util.Set;

public record ShareSettings(
        boolean isPublic,
        Set<String> sharedWith
) {}