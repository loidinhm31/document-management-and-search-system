package com.dms.search.dto;


import com.dms.search.enums.QueryType;

public record SearchContext(
        QueryType queryType,
        String originalQuery,
        String uppercaseQuery,
        String lowercaseQuery) {

}