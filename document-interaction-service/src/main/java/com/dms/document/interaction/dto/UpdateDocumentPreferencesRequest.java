package com.dms.document.interaction.dto;

import java.util.Set;

public record UpdateDocumentPreferencesRequest(
        Set<String> preferredMajors,
        Set<String> preferredCourseCodes,
        Set<String> preferredLevels,
        Set<String> preferredCategories,
        Set<String> preferredTags,
        Set<String> languagePreferences
) {}