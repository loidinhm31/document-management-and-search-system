package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.DocumentBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentBookmarkRepository extends JpaRepository<DocumentBookmark, Long> {
    boolean existsByUserIdAndDocumentId(UUID userId, String documentId);
    void deleteByUserIdAndDocumentId(UUID userId, String documentId);
    Page<DocumentBookmark> findByUserId(UUID userId, Pageable pageable);
    List<DocumentBookmark> findByDocumentId(String documentId);

    @Query("SELECT COUNT(db) > 0 FROM DocumentBookmark db WHERE db.documentId = :documentId")
    boolean hasBookmarks(String documentId);

    @Query("SELECT COUNT(db) FROM DocumentBookmark db WHERE db.documentId = :documentId")
    long countBookmarksByDocument(String documentId);
}