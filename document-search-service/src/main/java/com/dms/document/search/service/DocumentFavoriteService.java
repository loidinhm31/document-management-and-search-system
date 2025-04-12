package com.dms.document.search.service;

import org.opensearch.index.query.BoolQueryBuilder;

import java.util.UUID;

/**
 * Interface for managing document favorites functionality
 */
public interface DocumentFavoriteService {

    /**
     * Adds a filter to the query builder to only return documents marked as favorites by the user
     *
     * @param queryBuilder the query builder to add the filter to
     * @param userId the ID of the user whose favorites should be considered
     */
    void addFavoriteFilter(BoolQueryBuilder queryBuilder, UUID userId);
}