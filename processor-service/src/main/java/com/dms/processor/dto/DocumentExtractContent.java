package com.dms.processor.dto;

import java.util.Map;


public record DocumentExtractContent(
        String content,
        Map<String, String> metadata
) {
}