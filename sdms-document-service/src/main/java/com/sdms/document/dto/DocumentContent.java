package com.sdms.document.dto;

import java.util.Map;


public record DocumentContent(
        String content,
        Map<String, String> metadata
) {
}