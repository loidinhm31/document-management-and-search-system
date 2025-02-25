package com.dms.document.interaction.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "document_comments")
@Data
public class DocumentComment {
    @Id
    @GeneratedValue(generator = "comment-id-generator")
    @GenericGenerator(
            name = "comment-id-generator",
            type = com.dms.document.interaction.utils.CommentIdGenerator.class
    )
    private Long id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "edited")
    private boolean edited;

    @Column(name = "deleted")
    private boolean deleted;

    @Version
    private Long version;

    @Transient
    private List<DocumentComment> replies = new ArrayList<>();

    @OneToMany(mappedBy = "comment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CommentReport> reports;
}
