package com.dms.search.dto;

import java.util.Map;


public record DocumentContent(
        String content,
        Map<String, String> metadata
) {
}