package com.dms.document.model;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Set;

@Data
public class TagsResponse {
    @Field("tags")
    private Set<String> tags;
}