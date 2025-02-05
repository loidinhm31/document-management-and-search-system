package com.dms.document.interaction.dto;

import java.util.Set;
import java.util.UUID;

public record UpdateShareSettingsRequest(
        boolean isPublic,
        Set<UUID> sharedWith
) {}