package com.dms.document.interaction.dto;

import lombok.Data;

@Data
public class AggregatedInteractionStats {
    private long totalViews;
    private long totalDownloads;
    private long totalComments;
    private long totalShares;
    private int uniqueDocuments;
}