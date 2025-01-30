package com.dms.document.interaction.dto;

import java.util.Set;

public record UpdateShareSettingsRequest(
        boolean isPublic,
        Set<String> sharedWith
) {}