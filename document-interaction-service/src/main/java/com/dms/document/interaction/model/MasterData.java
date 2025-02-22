package com.dms.document.interaction.model;

import com.dms.document.interaction.enums.MasterDataType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Document(collection = "master_data")
@CompoundIndex(def = "{'type': 1, 'code': 1}", unique = true)
public class MasterData {
    @Id
    private String id;

    private MasterDataType type;
    private String code;
    private Translation translations;

    private String description;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    @Field("is_active")
    private boolean isActive;

    @Field("parent_id")
    private String parentId;
}