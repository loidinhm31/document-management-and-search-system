package com.dms.document.interaction.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

@Data
@Document(collection = "document_interactions")
public class DocumentInteraction {
    @Id
    private String id;

    @Field("user_id")
    @Indexed
    private String userId;

    @Field("document_id")
    @Indexed
    private String documentId;

    @Field("interactions")
    private Map<String, InteractionStats> interactions;

    @Field("first_interaction_date")
    private Instant firstInteractionDate;

    @Field("last_interaction_date")
    @Indexed
    private Instant lastInteractionDate;

    @Data
    @NoArgsConstructor
    public static class InteractionStats {
        private int count;
        private Instant lastUpdate;
    }
}