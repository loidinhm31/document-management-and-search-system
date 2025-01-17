package com.sdms.document.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class DocumentContent {
    private String content;
    private Map<String, String> metadata;
}