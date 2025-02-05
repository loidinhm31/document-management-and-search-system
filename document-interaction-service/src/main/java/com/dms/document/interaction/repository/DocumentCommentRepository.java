package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.DocumentComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentCommentRepository extends JpaRepository<DocumentComment, Long> {

    // Get all comments for a document (both top-level and replies)
    @Query(value = """
        WITH RECURSIVE CommentHierarchy AS (
            -- Base case: get top-level comments with index hint
            SELECT /*+ INDEX(c document_comments_doc_id_idx) */ 
                   c.*, 0 as level, c.created_at as thread_order
            FROM document_comments c
            WHERE c.document_id = :documentId 
            AND c.parent_id IS NULL 
            AND c.deleted = false
            
            UNION ALL
            
            -- Recursive case: get replies maintaining thread order
            SELECT c.*, 
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

    // Count total top-level comments for pagination
    @Query("SELECT COUNT(c) FROM DocumentComment c WHERE c.documentId = :documentId AND c.parentId IS NULL AND c.deleted = false")
    long countTopLevelComments(String documentId);

    // Get immediate replies for a list of parent comments
    @Query("SELECT c FROM DocumentComment c WHERE c.parentId IN :parentIds AND c.deleted = false ORDER BY c.createdAt ASC")
    List<DocumentComment> findRepliesByParentIds(List<Long> parentIds);
}