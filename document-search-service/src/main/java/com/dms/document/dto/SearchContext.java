package com.dms.document.dto;


import com.dms.document.enums.QueryType;

public record SearchContext(
        QueryType queryType,
        String originalQuery,
        String uppercaseQuery,
        String lowercaseQuery) {

}