package com.dms.document.interaction.model;

import com.dms.document.interaction.enums.InteractionType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Data
@Document(collection = "document_interactions")
public class DocumentInteraction {
    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private String userId;

    @Indexed
    @Field("document_id")
    private String documentId;

    @Field("interaction_type")
    private InteractionType interactionType;

    @Field("duration_seconds")
    private Long durationSeconds;

    @Field("created_at")
    private Date createdAt;

}