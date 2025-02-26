package com.dms.document.search.repository;

import com.dms.document.search.model.DocumentFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface DocumentFavoriteRepository extends JpaRepository<DocumentFavorite, Long> {

    @Query("SELECT df.documentId FROM DocumentFavorite df WHERE df.userId = :userId")
    Set<String> findDocumentIdsByUserId(@Param("userId") UUID userId);


    boolean existsByUserIdAndDocumentId(UUID userId, String documentId);

    @Query(value = "SELECT document_id FROM document_favorites WHERE user_id = :userId " +
                   "ORDER BY created_at DESC LIMIT :limit",
            nativeQuery = true)
    Set<String> findRecentFavoriteDocumentIdsByUserId(@Param("userId") UUID userId, @Param("limit") int limit);
}