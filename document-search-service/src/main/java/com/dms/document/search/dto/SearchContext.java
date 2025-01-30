package com.dms.document.search.dto;


import com.dms.document.search.enums.QueryType;

public record SearchContext(
        QueryType queryType,
        String originalQuery,
        String uppercaseQuery,
        String lowercaseQuery) {

}