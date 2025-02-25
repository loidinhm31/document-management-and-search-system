package com.dms.document.interaction.dto;

public record DocumentStatisticsResponse(
        int viewCount,
        int updateCount,
        int deleteCount,
        int downloadCount,
        int revertCount,
        int shareCount,
        int favoriteCount,
        int commentCount,
        int totalInteractions
) {
}
