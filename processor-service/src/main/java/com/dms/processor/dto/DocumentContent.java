package com.dms.processor.dto;

import java.util.Map;


public record DocumentContent(
        String content,
        Map<String, String> metadata
) {
}