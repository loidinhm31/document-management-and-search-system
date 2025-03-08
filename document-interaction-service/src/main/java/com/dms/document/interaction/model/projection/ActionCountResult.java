package com.dms.document.interaction.model.projection;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
public class ActionCountResult {
    @Field("_id")
    private String actionType;
    private Integer count;
}