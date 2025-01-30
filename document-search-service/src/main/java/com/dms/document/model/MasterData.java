package com.dms.document.model;

import com.dms.document.enums.MasterDataType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    @Field("is_active")
    private boolean isActive;
}