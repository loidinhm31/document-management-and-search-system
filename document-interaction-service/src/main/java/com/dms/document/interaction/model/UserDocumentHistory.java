package com.dms.document.interaction.model;

import com.dms.document.interaction.enums.UserDocumentActionType;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Builder
@Document(collection = "user_document_histories")
public class UserDocumentHistory {
    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("action_type")
    private UserDocumentActionType userDocumentActionType;

    private String detail;

    @Field("document_id")
    private String documentId;

    private Integer version;

    @Field("created_at")
    private Instant createdAt;
}
