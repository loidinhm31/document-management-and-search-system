package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.DocumentComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentCommentRepository extends JpaRepository<DocumentComment, Long> {
    Optional<DocumentComment> findByDocumentIdAndId(String documentId, Long id);

    @Query(value = """
        WITH RECURSIVE CommentHierarchy AS (
            -- Base case: get top-level comments
            SELECT 
                c.*, 
                0 as level,
                c.created_at as thread_order
            FROM document_comments c
            WHERE c.document_id = :documentId 
            AND c.parent_id IS NULL 
            AND c.deleted = false
            
            UNION ALL
            
            -- Recursive case: get replies
            SELECT 
                c.*,
                ch.level + 1,
                ch.thread_order
            FROM document_comments c
            INNER JOIN CommentHierarchy ch ON c.parent_id = ch.id
            WHERE c.deleted = false
        )
        SELECT * FROM CommentHierarchy
        ORDER BY thread_order DESC, level ASC, created_at ASC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<DocumentComment> findCommentsWithReplies(String documentId, int limit, int offset);

    @Query("SELECT COUNT(c) FROM DocumentComment c WHERE c.documentId = :documentId AND c.parentId IS NULL AND c.deleted = false")
    long countTopLevelComments(String documentId);

    @Query(value = """
    WITH RECURSIVE CommentHierarchy AS (
        -- Base case: start with the target comment
        SELECT id FROM document_comments 
        WHERE id = :commentId
        
        UNION ALL
        
        -- Recursive case: get all replies
        SELECT c.id
        FROM document_comments c
        INNER JOIN CommentHierarchy ch ON c.parent_id = ch.id
    )
    SELECT id FROM CommentHierarchy
    """, nativeQuery = true)
    List<Long> findAllDescendantIds(Long commentId);

    @Modifying
    @Query("""
    UPDATE DocumentComment c 
    SET c.deleted = true, 
        c.content = '[deleted]',
        c.updatedAt = CURRENT_TIMESTAMP 
    WHERE c.id IN :commentIds
    """)
    void markCommentsAsDeleted(List<Long> commentIds);

    boolean existsByDocumentIdAndUserIdAndIdNot(String documentId, UUID userId, Long id);
}